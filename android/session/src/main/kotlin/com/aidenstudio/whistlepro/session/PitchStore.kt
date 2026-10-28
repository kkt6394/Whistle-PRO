package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.double
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** 저장된 구장. 네 모서리만 담는다(다듬은 경기장은 읽을 때 다시 계산). (Swift StoredPitch와 같은 JSON 키) */
internal data class StoredPitch(val id: UUID, val corners: List<GeoPoint>, val createdAt: Double)

/** 경기와 구장의 연결. pitch가 null이면 "이 경기에서 지움"이다(자동 판정도 하지 않는다). */
internal data class PitchLink(val pitch: UUID?)

internal data class PitchFile(val pitches: List<StoredPitch>, val links: Map<UUID, PitchLink>)

internal const val PITCHES_FILE_NAME = "pitches.json"

private const val MATCH_RATIO = 0.5
private const val MATCH_MARGIN = 15.0
private const val SAME_PITCH_DISTANCE = 30.0
private const val METERS_PER_DEGREE = 111_320.0

private val MatchLibrary.pitchesFile: File get() = File(directory, PITCHES_FILE_NAME)

/** pitches.json 전체. 파일이 없거나 깨졌으면 null. links는 노트와 같은 "키, 값, 키, 값" 배열 */
internal fun MatchLibrary.readPitches(): PitchFile? {
    val file = pitchesFile
    if (!file.exists()) return null
    return try {
        val root = parseJson(file.readBytes()) as? JsonObject ?: return null
        val pitches = (root["pitches"] as? JsonArray ?: return null).map { element ->
            val item = element as? JsonObject ?: return null
            val corners = (item["corners"] as? JsonArray ?: return null).map { c ->
                val point = c as? JsonObject ?: return null
                GeoPoint(point.double("latitude"), point.double("longitude"))
            }
            StoredPitch(parseUuid(item.text("id")) ?: return null, corners, item.double("createdAt"))
        }
        val items = root["links"] as? JsonArray ?: return null
        if (items.size % 2 != 0) return null
        val links = LinkedHashMap<UUID, PitchLink>()
        for (i in items.indices step 2) {
            val matchID = parseUuid((items[i] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null) ?: return null
            val value = items[i + 1] as? JsonObject ?: return null
            links[matchID] = PitchLink(value["pitch"]?.let { parseUuid(it.text()) ?: return null })
        }
        PitchFile(pitches, links)
    } catch (e: Exception) {
        null
    }
}

private fun JsonObject.text(key: String): String = get(key).text()

private fun kotlinx.serialization.json.JsonElement?.text(): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("문자열이 아님")

internal fun MatchLibrary.writePitches(file: PitchFile) {
    directory.mkdirs()
    val json = buildJsonObject {
        put("pitches", JsonArray(file.pitches.map { p ->
            buildJsonObject {
                put("id", JsonPrimitive(p.id.swiftString))
                put("corners", JsonArray(p.corners.map {
                    buildJsonObject {
                        put("latitude", JsonPrimitive(it.latitude))
                        put("longitude", JsonPrimitive(it.longitude))
                    }
                }))
                put("createdAt", JsonPrimitive(p.createdAt))
            }
        }))
        put("links", JsonArray(file.links.flatMap { (matchID, link) ->
            listOf(
                JsonPrimitive(matchID.swiftString),
                buildJsonObject { link.pitch?.let { put("pitch", JsonPrimitive(it.swiftString)) } },
            )
        }))
    }
    requireFinite(json, "pitches.json")
    writeFileAtomically(pitchesFile, json.toString().toByteArray(Charsets.UTF_8))
}

private fun meters(a: GeoPoint, b: GeoPoint): Double {
    val north = (b.latitude - a.latitude) * METERS_PER_DEGREE
    val east = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos((a.latitude + b.latitude) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}

/** 점의 MATCH_RATIO 이상이 경기장(+MATCH_MARGIN) 안이면 그 비율, 아니면 null */
private fun ratioInside(pitch: Pitch, points: List<GeoPoint>): Double? {
    if (points.isEmpty()) return null
    val ratio = points.count { pitch.contains(it, MATCH_MARGIN) }.toDouble() / points.size
    return ratio.takeIf { it >= MATCH_RATIO }
}

/** 비율이 가장 높은 구장. 같으면 먼저 저장된 것 */
private fun PitchFile.bestMatch(points: List<GeoPoint>): Pair<StoredPitch, Pitch>? {
    var best: Triple<StoredPitch, Pitch, Double>? = null
    for (stored in pitches) {
        val pitch = Pitch.fromCorners(stored.corners) ?: continue
        val ratio = ratioInside(pitch, points) ?: continue
        if (best == null || ratio > best.third) best = Triple(stored, pitch, ratio)
    }
    return best?.let { it.first to it.second }
}

/**
 * 이 경기의 경기장. 연결된 구장 → 지운 표시면 null → 이동 경로 점(points)이 겹치는 저장 구장 순서.
 * 파일에 쓰지 않는다.
 */
fun MatchLibrary.pitch(matchID: UUID, points: List<GeoPoint>): Pitch? {
    val file = readPitches() ?: return null
    val link = file.links[matchID]
    if (link != null) {
        val id = link.pitch ?: return null
        file.pitches.firstOrNull { it.id == id }?.let { stored -> Pitch.fromCorners(stored.corners)?.let { return it } }
    }
    return file.bestMatch(points)?.second
}

/**
 * 찍은 모서리를 다듬어 이 경기에 연결한다. 다듬어지지 않으면 null이고 저장하지 않는다.
 * 기존 구장(이 경기에 연결된 것, 없으면 새 경기장과 겹쳐 자동 판정되는 것)의 중심이 30 m 이내로 옮겨졌으면
 * 그 구장을 고치고(같은 구장을 쓰는 다른 경기도 따라 바뀐다), 멀면 새 구장으로 저장한다.
 * 파일이 깨졌으면 새로 시작한다.
 */
fun MatchLibrary.savePitch(matchID: UUID, corners: List<GeoPoint>, now: Double): Pitch? {
    val pitch = Pitch.fromCorners(corners) ?: return null
    val file = readPitches() ?: PitchFile(emptyList(), emptyMap())
    val linked = file.links[matchID]?.pitch?.let { id -> file.pitches.firstOrNull { it.id == id } }
    val existing = linked ?: file.bestMatch(pitch.corners)?.first
    val existingPitch = existing?.let { Pitch.fromCorners(it.corners) }
    val pitches = file.pitches.toMutableList()
    val id: UUID
    if (existing != null && existingPitch != null && meters(existingPitch.center, pitch.center) <= SAME_PITCH_DISTANCE) {
        id = existing.id
        pitches[pitches.indexOf(existing)] = existing.copy(corners = pitch.corners)
    } else {
        id = UUID.randomUUID()
        pitches.add(StoredPitch(id, pitch.corners, now))
    }
    val links = LinkedHashMap(file.links)
    links[matchID] = PitchLink(id)
    writePitches(PitchFile(pitches, links))
    return pitch
}

/** 이 경기에서 구장을 지운다. 구장 자체는 남고, 이 경기는 자동 판정도 하지 않는다 */
fun MatchLibrary.clearPitch(matchID: UUID) {
    val file = readPitches() ?: PitchFile(emptyList(), emptyMap())
    val links = LinkedHashMap(file.links)
    links[matchID] = PitchLink(null)
    writePitches(PitchFile(file.pitches, links))
}
