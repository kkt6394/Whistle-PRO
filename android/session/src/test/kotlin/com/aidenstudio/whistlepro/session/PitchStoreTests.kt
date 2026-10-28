package com.aidenstudio.whistlepro.session

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val METERS_PER_DEGREE = 111_320.0

private fun meters(a: GeoPoint, b: GeoPoint): Double {
    val north = (b.latitude - a.latitude) * METERS_PER_DEGREE
    val east = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos((a.latitude + b.latitude) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}

private val center = GeoPoint(37.5, 127.0)
private fun basePitch(bearing: Double = 30.0) = Pitch(center, 105.0, 68.0, bearing)

/** center에서 북쪽으로 north m 옮긴 점 */
private fun north(p: GeoPoint, north: Double) = GeoPoint(p.latitude + north / METERS_PER_DEGREE, p.longitude)

/** 경기장 한가운데 [inside]개 + 멀리(1 km) [outside]개 */
private fun track(pitch: Pitch, inside: Int, outside: Int): List<GeoPoint> =
    List(inside) { pitch.center } + List(outside) { north(pitch.center, 1000.0) }

private val anyPoint = listOf(GeoPoint(0.0, 0.0))

class PitchStoreTests {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()

    private fun assertSame(expected: Pitch, actual: Pitch?, tolerance: Double = 0.5) {
        val p = assertNotNull(actual)
        assertEquals(expected.length, p.length, tolerance)
        assertEquals(expected.width, p.width, tolerance)
        assertTrue(meters(expected.center, p.center) < tolerance)
    }

    @Test fun savedPitchIsLinkedToMatch() {
        val library = MatchLibrary(tempDirectory())
        library.savePitch(a, basePitch().corners, 100.0)
        assertSame(basePitch(), library.pitch(a, anyPoint))
    }

    @Test fun otherMatchOnSamePitchIsMatchedAutomatically() {
        val library = MatchLibrary(tempDirectory())
        library.savePitch(a, basePitch().corners, 100.0)
        assertSame(basePitch(), library.pitch(b, track(basePitch(), 6, 4)))
        assertTrue(library.readPitches()!!.links[b] == null)
    }

    @Test fun matchMostlyOutsideIsNotMatched() {
        val library = MatchLibrary(tempDirectory())
        library.savePitch(a, basePitch().corners, 100.0)
        assertNull(library.pitch(c, track(basePitch(), 4, 6)))
    }

    @Test fun marginOf15MetersCounts() {
        val library = MatchLibrary(tempDirectory())
        val pitch = basePitch()
        library.savePitch(a, pitch.corners, 100.0)
        // 터치라인(x=0) 밖으로 10 m / 20 m
        fun outside(distance: Double) = List(10) { pitch.geo(PitchPoint(-distance, 52.0)) }
        assertSame(pitch, library.pitch(b, outside(10.0)))
        assertNull(library.pitch(b, outside(20.0)))
    }

    @Test fun bestOfSeveralPitchesWins() {
        val library = MatchLibrary(tempDirectory())
        val p1 = basePitch(0.0)
        val p2 = Pitch(north(center, 50.0), 105.0, 68.0, 0.0)
        library.savePitch(a, p1.corners, 100.0)
        library.savePitch(b, p2.corners, 200.0)
        assertEquals(2, library.readPitches()!!.pitches.size)
        // 둘 다 안 7개, P1만 4개, P2만 9개 → P1 55%, P2 80%
        val points = List(7) { center } + List(4) { north(center, -60.0) } + List(9) { north(center, 100.0) }
        assertSame(p2, library.pitch(c, points))
    }

    @Test fun clearedMatchStaysCleared() {
        val library = MatchLibrary(tempDirectory())
        library.savePitch(a, basePitch().corners, 100.0)
        library.clearPitch(b)
        assertNull(library.pitch(b, track(basePitch(), 10, 0)))
        assertSame(basePitch(), library.pitch(c, track(basePitch(), 10, 0)))
    }

    @Test fun refittingNearbyUpdatesSharedPitch() {
        val library = MatchLibrary(tempDirectory())
        library.savePitch(a, basePitch().corners, 100.0)
        val moved = Pitch(north(center, 10.0), 105.0, 68.0, 30.0)
        library.savePitch(a, moved.corners, 200.0)
        assertEquals(1, library.readPitches()!!.pitches.size)
        assertSame(moved, library.pitch(b, track(moved, 10, 0)))
    }

    @Test fun refittingFarAwayCreatesNewPitch() {
        val library = MatchLibrary(tempDirectory())
        library.savePitch(a, basePitch().corners, 100.0)
        val moved = Pitch(north(center, 40.0), 105.0, 68.0, 30.0)
        library.savePitch(a, moved.corners, 200.0)
        assertEquals(2, library.readPitches()!!.pitches.size)
        assertSame(moved, library.pitch(a, anyPoint))
        // 연결 없는 경기 B는 원래 구장(중심 가까이 점만 있다)
        assertSame(basePitch(), library.pitch(b, listOf(basePitch().geo(PitchPoint(34.0, 2.0)))))
    }

    @Test fun corruptFileActsEmptyAndSaveRecovers() {
        val dir = tempDirectory()
        val library = MatchLibrary(dir)
        dir.mkdirs()
        File(dir, PITCHES_FILE_NAME).writeText("garbage")
        assertNull(library.pitch(a, anyPoint))
        assertNotNull(library.savePitch(a, basePitch().corners, 100.0))
        assertSame(basePitch(), library.pitch(a, anyPoint))
    }

    @Test fun degenerateCornersAreNotSaved() {
        val library = MatchLibrary(tempDirectory())
        assertNull(library.savePitch(a, listOf(center, center, center, center), 100.0))
        assertNull(library.readPitches())
    }

    @Test fun jsonMatchesSwiftShape() {
        val dir = tempDirectory()
        val library = MatchLibrary(dir)
        dir.mkdirs()
        // 스위프트 테스트와 같은 고정 문자열
        File(dir, PITCHES_FILE_NAME).writeText(FIXED_PITCHES_JSON)
        val file = assertNotNull(library.readPitches())
        assertEquals(1, file.pitches.size)
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), file.pitches[0].id)
        assertEquals(GeoPoint(37.5, 127.0), file.pitches[0].corners[0])
        assertEquals(1793000000.0, file.pitches[0].createdAt)
        assertEquals(PitchLink(file.pitches[0].id), file.links[a0])
        assertEquals(PitchLink(null), file.links[b0])

        // 다시 써도 같은 모양
        library.writePitches(file)
        val root = parseJson(File(dir, PITCHES_FILE_NAME).readBytes()) as JsonObject
        val pitch = (root["pitches"] as JsonArray)[0] as JsonObject
        assertEquals(setOf("id", "corners", "createdAt"), pitch.keys)
        assertEquals("11111111-2222-3333-4444-555555555555".uppercase(), (pitch["id"] as JsonPrimitive).content)
        assertEquals(1793000000.0, (pitch["createdAt"] as JsonPrimitive).double)
        val corner = (pitch["corners"] as JsonArray)[2] as JsonObject
        assertEquals(setOf("latitude", "longitude"), corner.keys)
        assertEquals(127.001, (corner["longitude"] as JsonPrimitive).double)
        val links = root["links"] as JsonArray
        assertEquals(4, links.size)
        assertEquals(a0.swiftString, (links[0] as JsonPrimitive).content)
        assertEquals(setOf("pitch"), (links[1] as JsonObject).keys)
        assertEquals(b0.swiftString, (links[2] as JsonPrimitive).content)
        assertEquals(emptySet(), (links[3] as JsonObject).keys)
    }
}

private val a0 = UUID.fromString("AAAAAAAA-0000-0000-0000-000000000001")
private val b0 = UUID.fromString("BBBBBBBB-0000-0000-0000-000000000002")

private val FIXED_PITCHES_JSON = """
{"pitches":[{"id":"11111111-2222-3333-4444-555555555555","corners":[{"latitude":37.5,"longitude":127.0},{"latitude":37.5,"longitude":127.001},{"latitude":37.501,"longitude":127.001},{"latitude":37.501,"longitude":127.0}],"createdAt":1793000000.0}],
 "links":["AAAAAAAA-0000-0000-0000-000000000001",{"pitch":"11111111-2222-3333-4444-555555555555"},"BBBBBBBB-0000-0000-0000-000000000002",{}]}
""".trimIndent()
