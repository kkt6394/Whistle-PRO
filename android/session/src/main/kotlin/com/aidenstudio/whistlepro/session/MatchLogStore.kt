package com.aidenstudio.whistlepro.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

interface MatchLogStore {
    /** 진행 중인 경기. 없으면 null. */
    fun loadActive(): MatchRecord?
    fun saveActive(record: MatchRecord)
    /** 경기(와 같은 경기의 운동 기록)를 보관함으로 옮기고 진행 중 기록을 지운다. */
    fun finishActive(record: MatchRecord)
    /** 보관된 경기 (오래된 순) */
    fun archived(): List<MatchRecord>

    fun saveActiveWorkout(log: WorkoutLog)
    fun loadActiveWorkout(): WorkoutLog?
    fun archivedWorkout(matchID: UUID): WorkoutLog?
    /** 폰이 받았다고 확인한 경기를 워치 보관함에서 지운다. */
    fun deleteArchived(matchID: UUID)
}

/**
 * 파일 저장소. directory/active.json·workout-active.json 에 진행 중 기록,
 * directory/finished/<id>.json·<id>-workout.json 에 폰으로 보낼 끝난 기록,
 * directory/history/<id>.json 에 워치에서 다시 볼 최근 경기(운동 기록 없이)를 둔다. (Swift FileMatchLogStore와 같은 배치)
 */
class FileMatchLogStore(val directory: File) : MatchLogStore {
    private val activeFile get() = File(directory, "active.json")
    private val activeWorkoutFile get() = File(directory, "workout-active.json")
    private val archiveDirectory get() = File(directory, "finished")
    private val historyDirectory get() = File(directory, "history")

    companion object {
        /** 워치 "지난 경기"에 남기는 경기 수 */
        const val HISTORY_LIMIT = 10
    }

    private fun archivedFile(id: UUID) = File(archiveDirectory, "${id.swiftString}.json")
    private fun archivedWorkoutFile(id: UUID) = File(archiveDirectory, "${id.swiftString}-workout.json")

    /** 읽을 수 없는 파일은 지우지 않고 active-corrupt-<uuid>.json 으로 옮긴 뒤 null을 돌려준다. */
    override fun loadActive(): MatchRecord? {
        if (!activeFile.exists()) return null
        return try {
            MatchRecord.fromJson(read(activeFile))
        } catch (e: Exception) {
            val quarantine = File(directory, "active-corrupt-${UUID.randomUUID().swiftString}.json")
            try {
                Files.move(activeFile.toPath(), quarantine.toPath())
            } catch (moveError: Exception) {
                // 이동이 안 되면 복사로라도 보관한 뒤 원본을 치운다 (다음 경기가 덮어쓰기 전에)
                Files.copy(activeFile.toPath(), quarantine.toPath())
                activeFile.delete()
            }
            null
        }
    }

    override fun saveActive(record: MatchRecord) {
        directory.mkdirs()
        writeAtomically(activeFile, record.toJson())
    }

    override fun finishActive(record: MatchRecord) {
        archiveDirectory.mkdirs()
        writeAtomically(archivedFile(record.id), record.toJson())
        val log = runCatching { loadActiveWorkout() }.getOrNull()
        if (log != null && log.matchID == record.id) writeAtomically(archivedWorkoutFile(record.id), log.toJson())
        // 지난 경기 사본은 보조 기능이라 실패해도 경기 보관을 막지 않는다
        runCatching { addToHistory(record) }
        for (active in listOf(activeFile, activeWorkoutFile)) {
            if (active.exists()) Files.delete(active.toPath())
        }
    }

    /** 워치에서 다시 볼 최근 경기 (최근 순). 폰이 받았다고 확인해도 남는다. */
    fun history(): List<MatchRecord> = historyEntries().map { it.second }

    /**
     * 워치 [지난 경기] 목록에서 지운다. 폰으로 보낼 보관함(finished/)은 건드리지 않는다.
     * 폰에서 지운 경기도 워치에는 남으므로 워치에서 따로 지울 길을 둔다.
     */
    fun deleteHistory(matchID: UUID) {
        val file = File(historyDirectory, "${matchID.swiftString}.json")
        if (file.exists()) Files.delete(file.toPath())
    }

    /**
     * 무료 경기는 이벤트가 없고 지난 경기 조회는 유료 기능이라 남기지 않는다.
     * 방금 끝낸 경기는 시작 시각이 같은 기록이 있어도 정리하지 않는다.
     */
    private fun addToHistory(record: MatchRecord) {
        if (!record.unlocked) return
        historyDirectory.mkdirs()
        writeAtomically(File(historyDirectory, "${record.id.swiftString}.json"), record.toJson())
        val others = historyEntries().filter { it.second.id != record.id }
        for ((file, _) in others.drop(HISTORY_LIMIT - 1)) runCatching { file.delete() }
    }

    /** 지난 경기 파일과 기록 (최근 순, 같은 시각이면 id 순). 읽을 수 없는 파일은 지운다 (보조 기능이라 격리하지 않는다). */
    private fun historyEntries(): List<Pair<File, MatchRecord>> {
        val files = historyDirectory.listFiles() ?: return emptyList()
        val entries = mutableListOf<Pair<File, MatchRecord>>()
        for (file in files.filter { it.extension == "json" }) {
            val record = runCatching { MatchRecord.fromJson(read(file)) }.getOrNull()
            if (record != null) entries += file to record else runCatching { file.delete() }
        }
        return entries.sortedWith(
            compareByDescending<Pair<File, MatchRecord>> { it.second.createdAt }.thenBy { it.second.id.swiftString },
        )
    }

    override fun archived(): List<MatchRecord> {
        val files = archiveDirectory.listFiles() ?: return emptyList()
        return files
            .filter { it.extension == "json" && !it.name.endsWith("-workout.json") }
            .mapNotNull { runCatching { MatchRecord.fromJson(read(it)) }.getOrNull() }
            .sortedBy { it.createdAt }
    }

    /**
     * 보관된 경기 ID (오래 수정된 순, 같으면 id 순). 기록을 디코딩하지 않고 finished/ 파일 이름에서 뽑는다:
     * 폰에 받았는지 물을 때 보관함 전체를 읽지 않게 (Swift archivedIDs와 같음). 운동 기록·이름이 다른 파일은 뺀다.
     * 폴더가 없으면 빈 목록, 있는데 읽지 못하면 IOException (Swift도 던진다): 빈 목록으로 보면 남은 전송 정리가 모두 지운다.
     */
    fun archivedIDs(): List<UUID> {
        if (!archiveDirectory.exists()) return emptyList()
        val files = archiveDirectory.listFiles() ?: throw java.io.IOException("보관함 목록을 읽지 못했다: $archiveDirectory")
        return files
            .filter { it.extension == "json" }
            .mapNotNull { file -> parseUuid(file.nameWithoutExtension)?.let { it to file.lastModified() } }
            .sortedWith(compareBy<Pair<UUID, Long>> { it.second }.thenBy { it.first.swiftString })
            .map { it.first }
    }

    override fun saveActiveWorkout(log: WorkoutLog) {
        directory.mkdirs()
        writeAtomically(activeWorkoutFile, log.toJson())
    }

    /** 읽을 수 없으면 null (운동 기록은 경기 기록보다 덜 중요해서 격리하지 않는다) */
    override fun loadActiveWorkout(): WorkoutLog? {
        if (!activeWorkoutFile.exists()) return null
        return runCatching { WorkoutLog.fromJson(read(activeWorkoutFile)) }.getOrNull()
    }

    override fun archivedWorkout(matchID: UUID): WorkoutLog? {
        val file = archivedWorkoutFile(matchID)
        if (!file.exists()) return null
        return WorkoutLog.fromJson(read(file))
    }

    override fun deleteArchived(matchID: UUID) {
        for (file in listOf(archivedFile(matchID), archivedWorkoutFile(matchID))) {
            if (file.exists()) Files.delete(file.toPath())
        }
    }

    private fun read(file: File): JsonObject = readJsonFile(file)

    private fun writeAtomically(file: File, json: JsonObject) {
        requireFinite(json, file.name)
        writeFileAtomically(file, json.toString().toByteArray(Charsets.UTF_8))
    }
}

/**
 * 임시 파일에 쓰고 원자적으로 바꾼다 (Swift .atomic과 같음). android.util.AtomicFile처럼 이름을 바꾸기 전에 fsync해,
 * 전원이 갑자기 꺼져도 빈 파일로 바뀌지 않는다. 쓰기가 실패하면(디스크 가득 참 등) 임시 파일을 지운다.
 * write는 테스트에서 쓰기 실패를 흉내 내려고 바꿀 수 있다.
 */
internal fun writeFileAtomically(
    file: File,
    bytes: ByteArray,
    write: (FileOutputStream, ByteArray) -> Unit = { out, b -> out.write(b) },
) {
    val temp = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
        FileOutputStream(temp).use { out ->
            write(out, bytes)
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        temp.delete()
        throw e
    }
}

/**
 * NaN·Infinity가 든 값은 쓰지 않는다. kotlinx는 이를 따옴표 없는 NaN/Infinity로 써서 Swift가 읽지 못하는 파일이 된다.
 * Swift JSONEncoder가 EncodingError.invalidValue로 저장을 실패시키는 것과 같게 예외를 던진다.
 */
internal fun requireFinite(element: JsonElement, where: String) {
    when (element) {
        is JsonObject -> element.values.forEach { requireFinite(it, where) }
        is JsonArray -> element.forEach { requireFinite(it, where) }
        is JsonPrimitive -> if (!element.isString && element.content in setOf("NaN", "Infinity", "-Infinity")) {
            throw IllegalArgumentException("$where: 유한하지 않은 숫자 ${element.content}")
        }
    }
}

/**
 * 엄격한 UTF-8로 읽는다. readText()는 깨진 바이트를 대체 문자(U+FFFD)로 바꿔 읽어 버려 격리되지 않는다
 * (Swift JSONDecoder는 깨진 UTF-8을 거부한다).
 */
internal fun readJsonFile(file: File): JsonObject = parseJsonObject(file.readBytes())

internal fun parseJsonObject(bytes: ByteArray): JsonObject = parseJson(bytes).jsonObject

internal fun parseJson(bytes: ByteArray): JsonElement {
    val text = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
    return Json.parseToJsonElement(text)
}
