package com.aidenstudio.whistlepro.session.design

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingGeometryTests {
    private val share = RingGeometry.REGULATION_SHARE

    @Test fun ringStartsBottomLeftPassesTopEndsBottomRight() {
        assertEquals(125.0, RingGeometry.angle(0.0))
        assertEquals(270.0, RingGeometry.angle(0.5))
        assertEquals(415.0, RingGeometry.angle(1.0))
    }

    @Test fun kickoffIsEmpty() {
        assertEquals(RingFill(0.0, null), RingGeometry.period(0, 2700))
    }

    @Test fun halfwayFillsHalfOfRegulationShare() {
        val fill = RingGeometry.period(1350, 2700)
        assertTrue(abs(fill.main - share / 2) < 1e-9)
        assertNull(fill.added)
    }

    @Test fun regulationEndFillsShareWithoutAddedSegment() {
        val fill = RingGeometry.period(2700, 2700)
        assertTrue(abs(fill.main - share) < 1e-9)
        assertNull(fill.added)
    }

    @Test fun addedTimeGrowsFromShare() {
        // 규정 45분의 10% = 270초. 135초면 남은 구간의 절반
        val fill = RingGeometry.period(2835, 2700)
        assertTrue(abs(fill.main - share) < 1e-9)
        val added = assertNotNull(fill.added)
        assertTrue(abs(added.start - share) < 1e-9)
        assertTrue(abs(added.endInclusive - (share + (1 - share) / 2)) < 1e-9)
    }

    @Test fun addedTimeCapsAtRingEnd() {
        val fill = RingGeometry.period(2700 + 900, 2700)
        assertTrue(abs((fill.added?.endInclusive ?: 0.0) - 1) < 1e-9)
    }

    @Test fun zeroLengthGivesEmptyRing() {
        assertEquals(RingFill(0.0, null), RingGeometry.period(100, 0))
        assertEquals(RingFill(0.0, null), RingGeometry.period(-5, 2700))
    }

    @Test fun restShrinksWithRemainingTime() {
        assertEquals(RingFill(0.5, null), RingGeometry.rest(450, 900))
        assertEquals(RingFill(1.0, null), RingGeometry.rest(900, 900))
        assertEquals(RingFill(0.0, null), RingGeometry.rest(0, 900))
    }

    @Test fun restWithZeroTotalIsEmpty() {
        assertEquals(RingFill(0.0, null), RingGeometry.rest(0, 0))
        assertEquals(RingFill(1.0, null), RingGeometry.rest(2000, 900))
    }
}
