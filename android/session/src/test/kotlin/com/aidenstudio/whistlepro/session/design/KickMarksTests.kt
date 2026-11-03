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

    @Test fun rowsBeforeFirstKickAllPending() {
        val rows = KickMarks.rows(emptyList(), emptyList(), 5, 7)
        assertEquals(List(5) { KickMark.PENDING }, rows.home)
        assertEquals(List(5) { KickMark.PENDING }, rows.away)
    }

    /** 서든데스: 두 팀 줄은 같은 킥 순번에서 잘라 같은 열이 같은 순번이다 (Swift rowsShareStartInSuddenDeath) */
    @Test fun rowsShareStartInSuddenDeath() {
        val home = listOf(true, true, false, true, true, true, true, true)
        val away = listOf(true, false, true, true, true, true, true)
        val rows = KickMarks.rows(home, away, 5, 7)
        val s = KickMark.SCORED
        val m = KickMark.MISSED
        assertEquals(listOf(s, m, s, s, s, s, s), rows.home)
        assertEquals(listOf(m, s, s, s, s, s, KickMark.PENDING), rows.away)
    }

    @Test fun rowsPadToRegulationKicks() {
        val rows = KickMarks.rows(listOf(true, false), listOf(true), 5, 7)
        val p = KickMark.PENDING
        assertEquals(listOf(KickMark.SCORED, KickMark.MISSED, p, p, p), rows.home)
        assertEquals(listOf(KickMark.SCORED, p, p, p, p), rows.away)
    }

    @Test fun rowsWithZeroOrNegativeLimitAreEmpty() {
        for (limit in listOf(0, -1)) {
            val rows = KickMarks.rows(listOf(true), emptyList(), 5, limit)
            assertTrue(rows.home.isEmpty() && rows.away.isEmpty())
        }
    }

    @Test fun hiddenCountOnlyWhenLongerThanLimit() {
        assertEquals(0, KickMarks.hiddenCount(listOf(true, false), listOf(true), 5, 5))
        val eight = List(8) { true }
        assertEquals(2, KickMarks.hiddenCount(eight, eight.dropLast(1), 5, 6))
    }

    /** 40mm(폭 134, 점 14)는 6칸, 넓으면 상한 7칸, 아주 좁아도 1칸 (Swift와 같은 값) */
    @Test fun fittingFollowsWidth() {
        assertEquals(6, KickMarks.fitting(134.0, 14.0, 7))
        assertEquals(7, KickMarks.fitting(177.0, 14.0, 7))
        assertEquals(1, KickMarks.fitting(10.0, 14.0, 7))
    }
}
