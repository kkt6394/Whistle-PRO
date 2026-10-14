package com.aidenstudio.whistlepro.session.design

import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.session.SinBinBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainStatusTests {
    private val early = SinBinBadge(Team.AWAY, 8, "2:00", 120)
    private val late = SinBinBadge(Team.HOME, 4, "7:30", 450)

    @Test fun nothingToShow() {
        assertNull(MainStatus.select(emptyList(), emptyList()))
    }

    @Test fun shortHandedWinsOverSinBin() {
        assertEquals(MainStatus.ShortHanded(listOf(Team.AWAY)), MainStatus.select(listOf(Team.AWAY), listOf(early)))
    }

    @Test fun earliestEndingSinBinWithCountOfOthers() {
        assertEquals(MainStatus.SinBin(early, 1), MainStatus.select(emptyList(), listOf(late, early)))
        assertEquals(MainStatus.SinBin(late, 0), MainStatus.select(emptyList(), listOf(late)))
    }

    @Test fun tieKeepsFirstListed() {
        val twin = SinBinBadge(Team.HOME, 2, "2:00", 120)
        assertEquals(MainStatus.SinBin(early, 1), MainStatus.select(emptyList(), listOf(early, twin)))
    }
}

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
