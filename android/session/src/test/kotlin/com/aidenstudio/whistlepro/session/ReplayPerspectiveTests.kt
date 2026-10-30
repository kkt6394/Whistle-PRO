package com.aidenstudio.whistlepro.session

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ReplayPerspectiveTests {
    private val pitch = Pitch(GeoPoint(37.5, 127.0), length = 100.0, width = 64.0, bearing = 20.0)

    @Test fun farSideIsSmaller() {
        val perspective = ReplayPerspective(pitch, 390.0, 600.0)
        val a = perspective.point(pitch.geo(PitchPoint(32.0, 0.0)))
        val b = perspective.point(pitch.geo(PitchPoint(32.0, 100.0)))
        assertTrue(b.scale < a.scale)
        assertTrue(b.y < a.y)
    }

    @Test fun centerIsNearMiddle() {
        val perspective = ReplayPerspective(pitch, 390.0, 600.0)
        val c = perspective.point(pitch.center)
        assertTrue(abs(c.x - 195) < 1e-9)
        assertTrue(abs(c.y - 600 * 0.44) < 1e-9)
    }
}
