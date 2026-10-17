package com.aidenstudio.whistlepro.session.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KickMarksTests {
    @Test fun beforeFirstKickAllPending() {
        assertEquals(List(5) { KickMark.PENDING }, KickMarks.marks(emptyList(), 5, 7))
    }

    @Test fun takenKicksThenRemaining() {
        assertEquals(
            listOf(KickMark.SCORED, KickMark.MISSED, KickMark.PENDING, KickMark.PENDING, KickMark.PENDING),
            KickMarks.marks(listOf(true, false), 5, 7),
        )
    }

    @Test fun longShootoutKeepsLatestMarks() {
        val results = listOf(true, true, false, true, true, true, false, true)
        assertEquals(
            listOf(KickMark.MISSED, KickMark.SCORED, KickMark.SCORED, KickMark.SCORED, KickMark.MISSED, KickMark.SCORED),
            KickMarks.marks(results, 5, 6),
        )
    }

    @Test fun zeroOrNegativeLimitIsEmpty() {
        assertTrue(KickMarks.marks(listOf(true), 0, 0).isEmpty())
        assertTrue(KickMarks.marks(listOf(true), 5, -1).isEmpty())
    }
}
