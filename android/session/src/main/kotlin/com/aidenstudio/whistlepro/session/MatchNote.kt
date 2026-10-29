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

/** 경기마다 폰에서 적는 메모 (대회명·장소·팀 색). 폰 전용이라 워치로는 보내지 않는다. (Swift MatchNote와 같은 JSON 키) */
data class MatchNote(
    val competition: String? = null,
    val venue: String? = null,
    /** 이 경기에서 바꾼 팀 색 (없으면 기록의 색). 예전 notes.json에는 이 키가 없다 */
    val colors: TeamColors? = null,
) {
    val isEmpty: Boolean get() = competition == null && venue == null && colors == null

    fun toJson(): JsonObject = buildJsonObject {
        competition?.let { put("competition", JsonPrimitive(it)) }
        venue?.let { put("venue", JsonPrimitive(it)) }
        colors?.let { put("colors", it.toJson()) }
    }

    companion object {
        /**
         * 경기 정보 편집기에서 고른 값으로 만든 노트. 고른 네 색이 기록의 색과 같으면 덮어쓰지 않는다 (colors null).
         * 두 번째 색이 주 색과 같으면 없는 것으로 정리한 뒤 비교한다 (Swift `MatchNote.edited`)
         */
        fun edited(competition: String?, venue: String?, colors: TeamColors, recorded: TeamColors): MatchNote {
            val picked = colors.normalized()
            return MatchNote(competition, venue, picked.takeIf { it != recorded.normalized() })
        }

        fun fromJson(json: JsonObject) = MatchNote(
            json.optionalString("competition"), json.optionalString("venue"),
            (json["colors"] as? JsonObject)?.let(TeamColors::fromJson),
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
