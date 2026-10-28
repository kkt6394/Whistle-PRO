package com.aidenstudio.whistlepro.session

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val METERS_PER_DEGREE = 111_320.0

private fun meters(a: GeoPoint, b: GeoPoint): Double {
    val north = (b.latitude - a.latitude) * METERS_PER_DEGREE
    val east = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos((a.latitude + b.latitude) / 2 * Math.PI / 180)
    return sqrt(north * north + east * east)
}

private val center = GeoPoint(37.5, 127.0)
private fun basePitch() = Pitch(center, 105.0, 68.0, 30.0)

class PitchTests {
    @Test fun rectangleCornersGivePitch() {
        val pitch = assertNotNull(Pitch.fromCorners(basePitch().corners))
        assertEquals(105.0, pitch.length, 0.5)
        assertEquals(68.0, pitch.width, 0.5)
        assertEquals(30.0, pitch.bearing, 0.5)
        assertTrue(meters(center, pitch.center) < 0.5)
    }

    @Test fun cornersInAnyOrderGiveSamePitch() {
        val c = basePitch().corners
        for (order in listOf(listOf(0, 2, 1, 3), listOf(3, 2, 1, 0))) {
            val pitch = assertNotNull(Pitch.fromCorners(order.map { c[it] }))
            assertEquals(105.0, pitch.length, 0.5)
            assertEquals(68.0, pitch.width, 0.5)
            assertTrue(meters(center, pitch.center) < 0.5)
        }
    }

    @Test fun firstCornerSideIsGoalLineA() {
        val c = basePitch().corners
        for (first in 0..3) {
            val order = (0..3).map { c[(first + it) % 4] }
            val pitch = assertNotNull(Pitch.fromCorners(order))
            assertEquals(0.0, pitch.local(order[0]).y, 0.5)
        }
    }

    @Test fun skewedQuadIsSquared() {
        val c = basePitch().corners.toMutableList()
        c[2] = GeoPoint(c[2].latitude + 3.0 / METERS_PER_DEGREE, c[2].longitude)
        val pitch = assertNotNull(Pitch.fromCorners(c))
        assertEquals(105.0, pitch.length, 2.0)
        assertEquals(68.0, pitch.width, 2.0)
    }

    @Test fun degenerateCornersGiveNull() {
        val c = basePitch().corners
        assertNull(Pitch.fromCorners(c.take(3)))
        val line = (0..3).map { GeoPoint(37.5 + it * 0.0001, 127.0) }
        assertNull(Pitch.fromCorners(line))
    }

    @Test fun localAndGeoRoundTrip() {
        val pitch = basePitch()
        val p = GeoPoint(37.5004, 127.0003)
        val back = pitch.geo(pitch.local(p))
        assertTrue(meters(p, back) < 0.01)
        val expected = listOf(0.0 to 0.0, 68.0 to 0.0, 68.0 to 105.0, 0.0 to 105.0)
        pitch.corners.zip(expected).forEach { (corner, e) ->
            val l = pitch.local(corner)
            assertEquals(e.first, l.x, 0.5)
            assertEquals(e.second, l.y, 0.5)
        }
    }

    @Test fun localCoordinatesOutsidePitch() {
        val pitch = basePitch()
        val p = pitch.geo(PitchPoint(-5.0, 50.0))
        assertEquals(-5.0, pitch.local(p).x, 0.1)
        assertTrue(pitch.contains(p, 8.0))
        assertFalse(pitch.contains(p, 2.0))
    }

    @Test fun standardPitchKeepsMarkings() {
        assertEquals(1.0, PitchMarkings.of(105.0, 68.0).scale, 1e-9)
        assertEquals(1.0, PitchMarkings.of(90.0, 60.0).scale, 1e-9)
    }

    @Test fun smallPitchScalesMarkings() {
        val m = PitchMarkings.of(40.0, 20.0)
        assertEquals(0.397, m.scale, 0.01)
        assertEquals(16.0, m.penaltyAreaWidth, 0.1)
    }
}

/** 경기장 안을 1 m 간격으로 채운 점들. */
private fun filled(pitch: Pitch): List<GeoPoint> =
    (0..pitch.width.toInt()).flatMap { x -> (0..pitch.length.toInt()).map { y -> pitch.geo(PitchPoint(x.toDouble(), y.toDouble())) } }

class PitchEstimateTests {
    private val truth = Pitch(center, 100.0, 60.0, 30.0)

    private fun assertFits(pitch: Pitch, tolerance: Double) {
        assertEquals(100.0, pitch.length, tolerance)
        assertEquals(60.0, pitch.width, tolerance)
        val diff = abs(pitch.bearing - 30.0)
        assertTrue(diff <= 2.0 || abs(diff - 180) <= 2.0)
    }

    @Test fun estimateFitsCoveredRectangle() {
        assertFits(assertNotNull(PitchEstimate.of(filled(truth))), 3.0)
    }

    @Test fun estimateIgnoresOutliers() {
        val far = (0..2).map { GeoPoint(37.5 + (500.0 + it) / METERS_PER_DEGREE, 127.0) }
        assertFits(assertNotNull(PitchEstimate.of(filled(truth) + far)), 3.0)
    }

    @Test fun thinStripIsWidenedTo30() {
        val pitch = assertNotNull(PitchEstimate.of(filled(Pitch(center, 100.0, 4.0, 30.0))))
        assertEquals(30.0, pitch.width, 0.5)
        assertEquals(100.0, pitch.length, 3.0)
    }

    @Test fun tooFewPointsGiveNoEstimate() {
        assertNull(PitchEstimate.of(emptyList()))
        assertNull(PitchEstimate.of(filled(truth).take(9)))
    }
}
