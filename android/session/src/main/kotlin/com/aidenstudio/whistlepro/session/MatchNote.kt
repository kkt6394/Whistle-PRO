package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.optionalString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.UUID

/** 경기마다 폰에서 적는 메모 (대회명·장소·팀 색·보고서 정보). 폰 전용이라 워치로는 보내지 않는다. (Swift MatchNote와 같은 JSON 키) */
class MatchNote(
    val competition: String? = null,
    val venue: String? = null,
    /** 이 경기에서 바꾼 팀 색 (없으면 기록의 색). 예전 notes.json에는 이 키가 없다 */
    val colors: TeamColors? = null,
    /** 이 경기만 보고서 구성 (없으면 설정의 기본값) */
    val reportSections: ReportSections? = null,
    /** 이벤트 ID → 카드 사유. 비면 JSON에서 키를 생략한다 */
    val cardReasons: Map<String, CardReason> = emptyMap(),
    otherReport: String? = null,
    matchNumber: String? = null,
    assistant1: String? = null,
    assistant2: String? = null,
    fourthOfficial: String? = null,
    /** 페널티킥으로 넣은 골의 이벤트 ID (경기 뒤 폰에서 표시, 독일 심판 카드의 E/S 칸 — 11-09). 비면 JSON에서 키를 생략한다 */
    val penaltyGoals: Set<String> = emptySet(),
) {
    /** 기타 보고 사항 (여러 줄). 아래 다섯 칸은 공백을 다듬어 비면 null로 둔다 */
    val otherReport: String? = clean(otherReport)
    val matchNumber: String? = clean(matchNumber)
    val assistant1: String? = clean(assistant1)
    val assistant2: String? = clean(assistant2)
    val fourthOfficial: String? = clean(fourthOfficial)

    val isEmpty: Boolean
        get() = competition == null && venue == null && colors == null && reportSections == null && cardReasons.isEmpty() && penaltyGoals.isEmpty() &&
            otherReport == null && matchNumber == null && assistant1 == null && assistant2 == null && fourthOfficial == null

    fun copy(
        competition: String? = this.competition, venue: String? = this.venue, colors: TeamColors? = this.colors,
        reportSections: ReportSections? = this.reportSections, cardReasons: Map<String, CardReason> = this.cardReasons,
        otherReport: String? = this.otherReport, matchNumber: String? = this.matchNumber,
        assistant1: String? = this.assistant1, assistant2: String? = this.assistant2,
        fourthOfficial: String? = this.fourthOfficial, penaltyGoals: Set<String> = this.penaltyGoals,
    ) = MatchNote(
        competition, venue, colors, reportSections, cardReasons, otherReport, matchNumber, assistant1, assistant2, fourthOfficial,
        penaltyGoals)

    private fun fields(): List<Any?> = listOf(
        competition, venue, colors, reportSections, cardReasons, otherReport, matchNumber, assistant1, assistant2, fourthOfficial,
        penaltyGoals)

    override fun equals(other: Any?): Boolean = other is MatchNote && fields() == other.fields()
    override fun hashCode(): Int = fields().hashCode()
    override fun toString(): String = "MatchNote${fields()}"

    fun toJson(): JsonObject = buildJsonObject {
        competition?.let { put("competition", JsonPrimitive(it)) }
        venue?.let { put("venue", JsonPrimitive(it)) }
        colors?.let { put("colors", it.toJson()) }
        reportSections?.let { put("reportSections", it.toJson()) }
        if (cardReasons.isNotEmpty()) {
            put("cardReasons", buildJsonObject { cardReasons.forEach { (id, reason) -> put(id, JsonPrimitive(reason.key)) } })
        }
        if (penaltyGoals.isNotEmpty()) put("penaltyGoals", JsonArray(penaltyGoals.sorted().map(::JsonPrimitive)))
        otherReport?.let { put("otherReport", JsonPrimitive(it)) }
        matchNumber?.let { put("matchNumber", JsonPrimitive(it)) }
        assistant1?.let { put("assistant1", JsonPrimitive(it)) }
        assistant2?.let { put("assistant2", JsonPrimitive(it)) }
        fourthOfficial?.let { put("fourthOfficial", JsonPrimitive(it)) }
    }

    companion object {
        /** 앞뒤 공백을 다듬고, 비면 null (글 안의 줄바꿈은 그대로) */
        internal fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * 경기 정보 편집기에서 고른 값으로 만든 노트. 고른 네 색이 기록의 색과 같으면 덮어쓰지 않는다 (colors null).
         * 두 번째 색이 주 색과 같으면 없는 것으로 정리한 뒤 비교한다 (Swift `MatchNote.edited`).
         * `keeping`을 주면 그 노트의 카드 사유·페널티킥 골·보고서 구성·기타 보고 사항은 그대로 가져오고,
         * 경기번호·심판진은 따로 주지 않으면 `keeping`의 값을 쓴다
         */
        fun edited(
            competition: String?, venue: String?, colors: TeamColors, recorded: TeamColors, keeping: MatchNote? = null,
            matchNumber: String? = keeping?.matchNumber, assistant1: String? = keeping?.assistant1,
            assistant2: String? = keeping?.assistant2, fourthOfficial: String? = keeping?.fourthOfficial,
        ): MatchNote {
            val picked = colors.normalized()
            return MatchNote(
                competition, venue, picked.takeIf { it != recorded.normalized() },
                keeping?.reportSections, keeping?.cardReasons ?: emptyMap(),
                keeping?.otherReport, matchNumber, assistant1, assistant2, fourthOfficial,
                keeping?.penaltyGoals ?: emptySet())
        }

        fun fromJson(json: JsonObject) = MatchNote(
            json.optionalString("competition"), json.optionalString("venue"),
            (json["colors"] as? JsonObject)?.let(TeamColors::fromJson),
            (json["reportSections"] as? JsonObject)?.let(ReportSections::fromJson),
            // 알 수 없는 사유는 그 항목만 버린다. 옛 값은 지금 값으로 읽는다
            ((json["cardReasons"] as? JsonObject) ?: JsonObject(emptyMap())).mapNotNull { (id, value) ->
                val reason = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(CardReason::fromKey)
                reason?.let { id to it }
            }.toMap(),
            json.optionalString("otherReport"), json.optionalString("matchNumber"),
            json.optionalString("assistant1"), json.optionalString("assistant2"), json.optionalString("fourthOfficial"),
            ((json["penaltyGoals"] as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet(),
        )
    }
}

internal const val NOTES_FILE_NAME = "notes.json"

private val MatchLibrary.notesFile: File get() = File(directory, NOTES_FILE_NAME)

/**
 * notes.json 전체. 파일이 없거나 깨졌으면 null.
 * 형식은 Swift `[UUID: MatchNote]` 자동 Codable과 같다: 키, 값, 키, 값, ... 을 번갈아 담은 배열.
 */
internal fun MatchLibrary.readNotes(): Map<UUID, MatchNote>? {
    val file = notesFile
    if (!file.exists()) return null
    return try {
        val items = parseJson(file.readBytes()) as? JsonArray ?: return null
        if (items.size % 2 != 0) return null
        val notes = LinkedHashMap<UUID, MatchNote>()
        for (i in items.indices step 2) {
            val id = parseUuid(items[i].asString()) ?: return null
            val value = items[i + 1] as? JsonObject ?: return null
            notes[id] = MatchNote.fromJson(value)
        }
        notes
    } catch (e: Exception) {
        null
    }
}

private fun JsonElement.asString(): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw SerializationException("노트 키가 문자열이 아님")

internal fun MatchLibrary.writeNotes(notes: Map<UUID, MatchNote>) {
    directory.mkdirs()
    val items = notes.flatMap { (id, note) -> listOf(JsonPrimitive(id.swiftString), note.toJson()) }
    writeFileAtomically(notesFile, JsonArray(items).toString().toByteArray(Charsets.UTF_8))
}

/** 모든 경기의 노트 (목록 요약에 팀 색을 합칠 때). 파일이 없거나 깨졌으면 빈 사전 */
fun MatchLibrary.notes(): Map<UUID, MatchNote> = readNotes() ?: emptyMap()

fun MatchLibrary.note(id: UUID): MatchNote? = readNotes()?.get(id)

/** 빈 노트면 항목을 지운다. 파일이 깨졌으면 새 사전으로 시작한다 */
fun MatchLibrary.saveNote(note: MatchNote, id: UUID) {
    val notes = LinkedHashMap(readNotes() ?: emptyMap())
    if (note.isEmpty) notes.remove(id) else notes[id] = note
    writeNotes(notes)
}
