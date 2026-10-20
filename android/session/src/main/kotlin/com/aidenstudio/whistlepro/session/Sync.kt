package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.array
import com.aidenstudio.whistlepro.core.obj
import com.aidenstudio.whistlepro.core.objects
import com.aidenstudio.whistlepro.core.present
import com.aidenstudio.whistlepro.core.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** 워치 → 폰으로 보내는 경기 하나 (경기 기록 + 운동 데이터). (Swift MatchBundle과 같은 JSON 키) */
data class MatchBundle(
    val record: MatchRecord,
    val workout: WorkoutLog?,
) {
    val id: UUID get() = record.id

    fun toJson(): JsonObject = buildJsonObject {
        put("record", record.toJson())
        workout?.let { put("workout", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JsonObject) = MatchBundle(
            record = MatchRecord.fromJson(json.obj("record")),
            workout = json.present("workout")?.let { WorkoutLog.fromJson(json.obj("workout")) },
        )
    }
}

/** 폰에서 만들어 워치로 보내는 경기 설정 */
data class MatchPreset(
    val id: UUID,
    val name: String,
    val setup: MatchSetup,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.swiftString))
        put("name", JsonPrimitive(name))
        put("setup", setup.toJson())
    }

    companion object {
        /** 내장 프리셋 (수정·삭제 불가, 워치에 항상 있음) */
        val FIFA_STANDARD = MatchPreset(UUID.fromString("00000000-0000-0000-0000-000000000001"), "FIFA 표준", MatchSetup())

        fun fromJson(json: JsonObject) = MatchPreset(
            id = json.uuid("id"),
            name = json.string("name"),
            setup = MatchSetup.fromJson(json.obj("setup")),
        )
    }
}

private fun presetsToBytes(presets: List<MatchPreset>): ByteArray =
    JsonArray(presets.map { it.toJson() }).toString().toByteArray(Charsets.UTF_8)

/** 프리셋 목록 파일. 없거나 읽을 수 없으면 빈 목록. */
class PresetStore(val file: File) {
    fun load(): List<MatchPreset> = try {
        parseJson(file.readBytes()).jsonArray.objects("presets").map(MatchPreset::fromJson)
    } catch (e: Exception) {
        emptyList()
    }

    fun save(presets: List<MatchPreset>) {
        file.parentFile?.mkdirs()
        writeFileAtomically(file, presetsToBytes(presets))
    }
}

/**
 * 워치 ↔ 폰 메시지 형식. 메시지는 키와 값의 맵이다 (워치 Data Layer로는 DataMap에 담는다).
 * `presets`·`entitlement` 값은 JSON 텍스트의 UTF-8 바이트.
 */
object SyncPayload {
    const val MATCH_ID_KEY = "matchID"
    const val PRESETS_KEY = "presets"
    const val ENTITLEMENT_KEY = "entitlement"
    const val UNSUPPORTED_KEY = "unsupported"
    /** 보낸 시각: 내용이 같아도 다시 전달되게 한다 (워치 앱 재설치 후 다시 보낼 때) */
    const val SENT_AT_KEY = "sentAt"

    const val CONTEXT_REQUEST_KEY = "contextRequest"
    const val RECEIPT_QUERY_KEY = "receiptQuery"
    const val RECEIVED_KEY = "received"

    /** 컨텍스트 한도 (Swift는 WCSession 한도와 같은 64KB로 본다) */
    const val CONTEXT_LIMIT = 65_536
    /** 구독 상태·보낸 시각 몫으로 남기는 크기 */
    const val CONTEXT_RESERVE = 4096

    /**
     * 워치 → 폰: 프리셋·구독 상태를 보내 달라는 요청. 새로 설치된 워치 앱은 컨텍스트가 비어 있다.
     */
    fun contextRequest(): Map<String, Any?> = mapOf(CONTEXT_REQUEST_KEY to true)

    fun isContextRequest(info: Map<String, Any?>): Boolean = info[CONTEXT_REQUEST_KEY] == true

    /** 받은 컨텍스트가 없다 (새로 설치된 워치 앱): 폰에 요청한다 */
    fun needsContext(context: Map<String, Any?>): Boolean =
        context[PRESETS_KEY] == null && context[ENTITLEMENT_KEY] == null

    /** 폰이 읽을 수 없는 형식의 경기 (앱 버전 차이). 워치는 다시 보내지 않고 보관한다. */
    fun unsupported(matchID: UUID): Map<String, Any?> =
        mapOf(MATCH_ID_KEY to matchID.swiftString, UNSUPPORTED_KEY to true)

    fun isUnsupported(info: Map<String, Any?>): Boolean = info[UNSUPPORTED_KEY] == true

    /** 워치 outbox 파일 이름: 전송마다 다르게. 같은 이름이면 끝난 이전 전송의 정리가 새 전송의 파일을 지울 수 있었다 */
    fun outboxFileName(matchID: UUID): String = "${matchID.swiftString}-${UUID.randomUUID().swiftString}.json"

    /** 폰이 경기를 저장했다는 확인 */
    fun ack(matchID: UUID): Map<String, Any?> = mapOf(MATCH_ID_KEY to matchID.swiftString)

    fun matchID(info: Map<String, Any?>): UUID? = (info[MATCH_ID_KEY] as? String)?.let(::parseUuid)

    /** 워치 → 폰 (답을 받는다): 보관함에 남은 경기 중 이미 받은 게 있는지 묻는다. */
    fun receiptQuery(matchIDs: List<UUID>): Map<String, Any?> = mapOf(RECEIPT_QUERY_KEY to matchIDs.map { it.swiftString })

    /** 묻는 메시지가 아니면 null */
    fun receiptQueryIDs(message: Map<String, Any?>): List<UUID>? = uuidList(message[RECEIPT_QUERY_KEY])

    /** 폰 → 워치: 물은 경기 중 폰이 이미 가진(또는 사용자가 지운) 경기 */
    fun receiptReply(matchIDs: List<UUID>): Map<String, Any?> = mapOf(RECEIVED_KEY to matchIDs.map { it.swiftString })

    /** 빈 답(이 질문을 모르는 이전 폰 앱)이면 빈 목록 → 아무것도 확인하지 않는다 */
    fun receivedIDs(reply: Map<String, Any?>): List<UUID> = uuidList(reply[RECEIVED_KEY]) ?: emptyList()

    private fun uuidList(value: Any?): List<UUID>? =
        (value as? List<*>)?.takeIf { list -> list.all { it is String } }?.mapNotNull { parseUuid(it as String) }

    /**
     * 폰 → 워치. 컨텍스트는 통째로 바뀌므로 프리셋과 구독 상태를 항상 함께 보낸다.
     * 프리셋이 한도를 넘으면 앞에서부터 들어가는 만큼만 넣는다 (구독 상태는 늘 가야 한다).
     */
    fun context(
        presets: List<MatchPreset>,
        entitlement: Entitlement,
        /** Unix 초 (Swift `Date().timeIntervalSince1970`과 같다) */
        sentAt: Double = System.currentTimeMillis() / 1000.0,
    ): Map<String, Any?> = mapOf(
        PRESETS_KEY to presetsToBytes(presetsFittingContext(presets)),
        ENTITLEMENT_KEY to entitlement.toJson().toString().toByteArray(Charsets.UTF_8),
        SENT_AT_KEY to sentAt,
    )

    fun entitlement(context: Map<String, Any?>): Entitlement? = try {
        (context[ENTITLEMENT_KEY] as? ByteArray)?.let { Entitlement.fromJson(parseJsonObject(it)) }
    } catch (e: Exception) {
        null
    }

    /**
     * 한도 안에 드는 앞쪽 프리셋 (목록 순서). 첫 프리셋부터 넘치면 빈 목록.
     * 명단이 꽉 찬(팀당 40명) 프리셋 하나가 3.3~7.4KB라 이름이 최대 길이면 9개쯤, 3글자 이름이면 18개쯤에서 넘친다.
     */
    fun presetsFittingContext(presets: List<MatchPreset>): List<MatchPreset> {
        // JSON 배열 크기 = 괄호 2 + 항목들 + 항목 사이 쉼표
        var size = 2
        val fitting = mutableListOf<MatchPreset>()
        for (preset in presets) {
            size += preset.toJson().toString().toByteArray(Charsets.UTF_8).size + (if (fitting.isEmpty()) 0 else 1)
            if (size > CONTEXT_LIMIT - CONTEXT_RESERVE) break
            fitting += preset
        }
        return fitting
    }

    /** 프리셋을 모두 워치로 보낼 수 있는지 (넘치면 목록·편집기에 안내한다) */
    fun presetsFitContext(presets: List<MatchPreset>): Boolean = presetsFittingContext(presets).size == presets.size

    fun presets(context: Map<String, Any?>): List<MatchPreset>? = try {
        (context[PRESETS_KEY] as? ByteArray)?.let { parseJson(it).jsonArray.objects("presets").map(MatchPreset::fromJson) }
    } catch (e: Exception) {
        null
    }
}

/** Swift UUID(uuidString:)처럼 8-4-4-4-12 형식만 받는다 (java UUID.fromString은 짧은 형식도 받는다). */
private val uuidText = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")

internal fun parseUuid(text: String): UUID? = if (uuidText.matches(text)) UUID.fromString(text) else null

/** 폰의 경기 보관함. 경기마다 <id>.json(전체)과 <id>.summary.json(목록용 요약). */
class MatchLibrary(val directory: File) {
    private companion object {
        const val SUMMARY_SUFFIX = ".summary.json"
        const val DELETED_SUFFIX = ".deleted"
        const val LEGACY_DELETED_NAME = "deleted.json"
    }

    /** 예전 형식: 지운 경기 id 목록 파일 하나 (읽기만 한다. 되돌릴 때만 항목을 뺀다) */
    private val legacyDeletedFile get() = File(directory, LEGACY_DELETED_NAME)

    /**
     * 지운 경기 표시: 경기마다 빈 파일 <id>.deleted. 목록 파일 하나를 읽고 다시 쓰면,
     * 그 파일이 깨졌을 때 다음 삭제가 예전에 지운 경기들을 모두 잊었다.
     */
    private fun deletedMarker(id: UUID) = File(directory, id.swiftString + DELETED_SUFFIX)

    private fun isDeleted(id: UUID, legacy: Set<UUID> = legacyDeleted()): Boolean =
        deletedMarker(id).exists() || id in legacy

    /** 예전 형식으로 지운 경기 id (파일이 없거나 읽을 수 없으면 빈 집합) */
    private fun legacyDeleted(): Set<UUID> = try {
        parseJson(legacyDeletedFile.readBytes()).jsonArray
            .map { parseUuid((it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return emptySet()) ?: return emptySet() }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun file(id: UUID) = File(directory, "${id.swiftString}.json")
    private fun summaryFile(id: UUID) = File(directory, id.swiftString + SUMMARY_SUFFIX)

    private fun write(file: File, json: JsonObject) {
        requireFinite(json, file.name)
        writeFileAtomically(file, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun readSummary(file: File): MatchSummary? =
        runCatching { MatchSummary.fromJson(readJsonFile(file)) }.getOrNull()

    private fun readBundle(file: File): MatchBundle? =
        runCatching { MatchBundle.fromJson(readJsonFile(file)) }.getOrNull()

    /** 경기 묶음 파일이다 (요약·노트·예전 지운 목록 파일은 아니다) */
    private fun isBundleFile(file: File) = file.extension == "json" &&
        file.name != LEGACY_DELETED_NAME && file.name != NOTES_FILE_NAME && !file.name.endsWith(SUMMARY_SUFFIX)

    /**
     * 같은 경기가 다시 오면 덮어쓴다 (중복 전송에 안전). 요약도 함께 쓴다.
     * 사용자가 지운 경기는 다시 저장하지 않고 false를 돌려준다 (확인은 그대로 보낸다).
     */
    fun upsert(bundle: MatchBundle): Boolean {
        if (isDeleted(bundle.id)) return false
        directory.mkdirs()
        write(file(bundle.id), bundle.toJson())
        write(summaryFile(bundle.id), MatchSummary.of(bundle).toJson())
        return true
    }

    /**
     * 목록용 요약 (최신순).
     * - rebuild가 true면 요약이 없거나 이전 형식(통계 값 없음)인 경기는 전체 기록을 읽어 요약을 다시 만들어 둔다.
     *   경기마다 전체 기록을 디코딩하므로 메인 스레드에서 부르지 않는다.
     * - false면 저장된 요약만 읽는다 (이전 형식도 그대로, 요약이 없는 경기는 빠진다). 화면을 바로 그릴 때 쓴다.
     * - 지움 표시가 있는 경기는 어느 쪽이든 빠진다: 다시 만드는 도중 경기가 지워지면(화면의 지우기) 요약을 다시 쓰지 않는다
     *   — 다시 쓰면 지운 경기의 요약 파일이 되살아나 목록에 남았다
     */
    fun summaries(rebuild: Boolean = true): List<MatchSummary> {
        val files = directory.listFiles() ?: return emptyList()
        val summaries = LinkedHashMap<String, MatchSummary>()
        for (file in files.filter { it.name.endsWith(SUMMARY_SUFFIX) }) {
            readSummary(file)?.let { summaries[file.name.removeSuffix(SUMMARY_SUFFIX)] = it }
        }
        // 예전 형식 지운 목록은 한 번만 읽는다
        val legacy = legacyDeleted()
        if (rebuild) {
            for (file in files.filter(::isBundleFile)) {
                val stem = file.name.removeSuffix(".json")
                if (summaries[stem]?.isCurrent == true) continue
                val bundle = readBundle(file)
                if (bundle != null) {
                    // 그사이 경기 수신(upsert)이 최신 요약을 썼으면 옛 기록으로 만든 요약으로 덮지 않는다
                    val fresh = readSummary(summaryFile(bundle.id))
                    if (fresh != null && fresh.isCurrent) {
                        summaries[stem] = fresh
                        continue
                    }
                    val summary = MatchSummary.of(bundle)
                    // 기록을 읽는 사이 지워졌으면 요약을 쓰지 않는다 (지움 표시는 기록보다 먼저 쓰인다)
                    if (isDeleted(bundle.id, legacy)) continue
                    runCatching { write(summaryFile(bundle.id), summary.toJson()) }
                    summaries[stem] = summary
                } else {
                    val old = summaries[stem] ?: continue
                    // 전체 기록을 읽을 수 없으면 이전 요약을 쓰고, 다음부터 다시 시도하지 않게 최신으로 표시한다 (통계 값은 없음)
                    val marked = old.copy(summaryVersion = MatchSummary.CURRENT_VERSION)
                    if (isDeleted(old.id, legacy)) continue
                    runCatching { write(summaryFile(old.id), marked.toJson()) }
                    summaries[stem] = marked
                }
            }
        }
        // 시각이 같으면 id 순서로 (목록이 매번 흔들리지 않게). 지운 경기는 뺀다 (지우다 요약 파일 하나를 못 지웠을 때도)
        return summaries.values.filterNot { isDeleted(it.id, legacy) }.sortedWith(compareByDescending<MatchSummary> { it.createdAt }.thenByDescending { it.id.swiftString })
    }

    /** 최신 경기가 먼저. 읽을 수 없는 파일은 건너뛴다. */
    fun all(): List<MatchBundle> {
        val files = directory.listFiles() ?: return emptyList()
        return files.filter(::isBundleFile).mapNotNull(::readBundle).sortedByDescending { it.record.createdAt }
    }

    /** 워치가 물은 경기 중 이미 받은 경기: 저장돼 있거나 사용자가 지운 경기 (지운 경기도 받을 때 확인을 보낸다) */
    fun received(among: List<UUID>): List<UUID> {
        // 예전 목록 파일은 ID마다 다시 읽지 않고 한 번만 읽는다
        val legacy = legacyDeleted()
        return among.filter { file(it).exists() || isDeleted(it, legacy) }
    }

    fun bundle(id: UUID): MatchBundle? {
        val file = file(id)
        if (!file.exists()) return null
        return MatchBundle.fromJson(readJsonFile(file))
    }

    fun delete(id: UUID) {
        // 지움 표시를 먼저 쓴다: 표시를 못 쓰면(디스크 부족 등) 기록도 지우지 않는다 — 기록만 지워지면 워치가 다시 보낼 때 되살아났다
        directory.mkdirs()
        writeFileAtomically(deletedMarker(id), ByteArray(0))
        for (file in listOf(file(id), summaryFile(id))) Files.deleteIfExists(file.toPath())
        // 노트는 기록과 함께 지운다 (노트 파일이 없거나 깨졌으면 할 일 없음)
        val notes = readNotes()
        if (notes != null && id in notes) writeNotes(notes - id)
    }

    /**
     * 지운 경기를 되돌린다 (안드로이드 "실행 취소"용). 지움 표시(<id>.deleted)를 지우고 다시 저장한다.
     * 예전 형식 목록(deleted.json)에 있으면 거기서도 뺀다. 노트는 delete가 지우므로 note를 주면 함께 되살린다.
     */
    fun restore(bundle: MatchBundle, note: MatchNote? = null) {
        val marker = deletedMarker(bundle.id)
        if (marker.exists()) Files.delete(marker.toPath())
        val legacy = legacyDeleted()
        if (bundle.id in legacy) {
            val rest = JsonArray((legacy - bundle.id).map { JsonPrimitive(it.swiftString) })
            writeFileAtomically(legacyDeletedFile, rest.toString().toByteArray(Charsets.UTF_8))
        }
        upsert(bundle)
        if (note != null) saveNote(note, bundle.id)
    }
}
