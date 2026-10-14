package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClockTests {
    @Test fun readsElapsedRemainingAndAddedTime() {
        val s = play(listOf(cmd(start, 100.0)))
        val c = assertNotNull(s.clock(700.9))
        assertEquals(600L, c.elapsed)
        assertEquals(600L, c.matchSecond)
        assertEquals(2100L, c.remaining)
        assertEquals(0L, c.addedTime)

        val late = assertNotNull(s.clock(100.0 + 2833))
        assertEquals(0L, late.remaining)
        assertEquals(133L, late.addedTime)
    }

    @Test fun secondHalfMatchSecondStartsAtFirstHalfLength() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2800.0), cmd(start, 3700.0)))
        val c = assertNotNull(s.clock(3760.0))
        assertEquals(regular(2), c.period)
        assertEquals(60L, c.elapsed)
        assertEquals(2760L, c.matchSecond)
    }

    @Test fun clockFreezesAfterPeriodEnds() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2800.0)))
        val c = assertNotNull(s.clock(3000.0))
        assertEquals(2800L, c.elapsed)
        assertEquals(100L, c.addedTime)
    }

    @Test fun nowBeforeStartClampsToZero() {
        val s = play(listOf(cmd(start, 100.0)))
        val c = assertNotNull(s.clock(50.0))
        assertEquals(0L, c.elapsed)
        assertEquals(2700L, c.remaining)
    }

    @Test fun noClockBeforeKickoff() {
        assertNull(play(emptyList()).clock(0.0))
    }

    @Test fun stoppageAccumulatesAndClosesAtPeriodEnd() {
        val running = play(listOf(cmd(start, 0.0), cmd(toggle, 100.0), cmd(toggle, 160.0), cmd(toggle, 300.0)))
        val c = assertNotNull(running.clock(330.0))
        assertEquals(90L, c.stoppageTotal)
        assertTrue(c.stoppageRunning)

        val ended = play(listOf(cmd(start, 0.0), cmd(toggle, 100.0), cmd(toggle, 160.0), cmd(toggle, 300.0), cmd(end, 400.0)))
        assertEquals(listOf(160L), ended.stoppageTotals(1000.0))
        assertEquals(false, ended.clock(1000.0)?.stoppageRunning)
    }

    @Test fun stoppageToggleOutsidePeriodIsIgnored() {
        val s = play(listOf(cmd(toggle, 0.0, id = "t")))
        assertEquals(listOf("t"), s.ignoredCommandIds)
    }

    @Test fun adjustPeriodLengthBeforeStart() {
        val s = play(listOf(cmd(CommandKind.AdjustPeriodLength(1800), 0.0), cmd(start, 0.0)))
        assertEquals(1800L, s.clock(0.0)?.remaining)
    }

    @Test fun adjustPeriodLengthDuringPeriodAffectsCurrentAndLater() {
        val s = play(listOf(cmd(start, 0.0), cmd(CommandKind.AdjustPeriodLength(1500), 60.0), cmd(end, 1500.0), cmd(start, 2400.0)))
        assertEquals(1500, s.periods[0].nominalLength)
        assertEquals(1500, s.periods[1].nominalLength)
        assertEquals(1500, s.periods[1].offset)
    }

    @Test fun adjustDuringPreExtraTimeChangesExtraLength() {
        val s = play(RuleSet(extraTime = true), twoHalves() + listOf(cmd(CommandKind.AdjustPeriodLength(600), 6400.0), cmd(start, 6500.0)))
        assertEquals(600, s.periods[2].nominalLength)
        assertEquals(2700, s.regularPeriodLength)
    }

    @Test fun adjustPeriodLengthRejectsOutOfRange() {
        val s = play(listOf(cmd(CommandKind.AdjustPeriodLength(30), 0.0, id = "a")))
        assertEquals(listOf("a"), s.ignoredCommandIds)
    }

    @Test fun intervalCountsDown() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2700.0)))
        assertEquals(800L, s.intervalRemaining(2800.0))
        assertEquals(0L, s.intervalRemaining(3700.0))
        assertNull(play(listOf(cmd(start, 0.0))).intervalRemaining(10.0))
    }

    /** Kotlin 추가: ±1조 초 제한이 Long으로 넘치지 않고, 무한대·NaN은 0 */
    @Test fun hugeTimestampsDoNotOverflow() {
        val s = play(listOf(cmd(start, 0.0)))
        assertEquals(1_000_000_000_000L, s.clock(5e12)?.elapsed)
        assertEquals(1_000_000_000_000L - 2700, s.clock(5e12)?.addedTime)
        assertEquals(0L, s.clock(Double.POSITIVE_INFINITY)?.elapsed)
        assertEquals(0L, s.clock(Double.NaN)?.elapsed)
        assertEquals(-1_000_000_000_000L, wholeSeconds(-5e12))
        assertEquals(3L, wholeSeconds(2.1, roundUp = true))
    }
}
