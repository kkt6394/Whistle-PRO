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

/** 경기마다 폰에서 적는 메모 (대회명·장소). 폰 전용이라 워치로는 보내지 않는다. (Swift MatchNote와 같은 JSON 키) */
data class MatchNote(
    val competition: String? = null,
    val venue: String? = null,
) {
    val isEmpty: Boolean get() = competition == null && venue == null

    fun toJson(): JsonObject = buildJsonObject {
        competition?.let { put("competition", JsonPrimitive(it)) }
        venue?.let { put("venue", JsonPrimitive(it)) }
    }

    companion object {
        fun fromJson(json: JsonObject) = MatchNote(json.optionalString("competition"), json.optionalString("venue"))
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

fun MatchLibrary.note(id: UUID): MatchNote? = readNotes()?.get(id)

/** 빈 노트면 항목을 지운다. 파일이 깨졌으면 새 사전으로 시작한다 */
fun MatchLibrary.saveNote(note: MatchNote, id: UUID) {
    val notes = LinkedHashMap(readNotes() ?: emptyMap())
    if (note.isEmpty) notes.remove(id) else notes[id] = note
    writeNotes(notes)
}
