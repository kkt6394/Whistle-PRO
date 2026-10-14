package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val start = CommandKind.StartPeriod
private val end = CommandKind.EndPeriod
private val E = ClockDisplay.ELAPSED
private val R = ClockDisplay.REMAINING

class MatchDisplayTests {
    @Test fun notStartedShowsZeroOrFullLength() {
        val s = makeState(emptyList())
        val elapsed = MatchDisplay(s, 0.0, E)
        assertEquals(MatchDisplay.Screen.NOT_STARTED, elapsed.screen)
        assertEquals("00:00", elapsed.mainClock)
        assertEquals("45:00", MatchDisplay(s, 0.0, R).mainClock)
    }

    @Test fun runningClockInBothModes() {
        val s = makeState(listOf(start to 0.0))
        val d = MatchDisplay(s, 754.0, E)
        assertEquals(MatchDisplay.Screen.PERIOD, d.screen)
        assertEquals("1H", d.periodLabel)
        assertEquals("12:34", d.mainClock)
        assertNull(d.addedTime)
        assertEquals("32:26", MatchDisplay(s, 754.0, R).mainClock)
    }

    @Test fun addedTimeFreezesMainClockAtRegulation() {
        val s = makeState(listOf(start to 0.0))
        val d = MatchDisplay(s, 2833.0, E)
        assertEquals("45:00", d.mainClock)
        assertEquals("+2:13", d.addedTime)
        assertEquals("00:00", MatchDisplay(s, 2833.0, R).mainClock)
    }

    @Test fun secondHalfContinuesFromFirstHalfLength() {
        val s = makeState(listOf(start to 0.0, end to 2760.0, start to 3660.0))
        assertEquals("46:00", MatchDisplay(s, 3720.0, E).mainClock)
    }

    @Test fun stoppageShownWhileRunningOrAccumulated() {
        val running = makeState(listOf(start to 0.0, CommandKind.ToggleStoppage to 100.0))
        val d = MatchDisplay(running, 130.0, E)
        assertEquals("0:30", d.stoppage)
        assertTrue(d.stoppageRunning)
        assertNull(MatchDisplay(makeState(listOf(start to 0.0)), 130.0, E).stoppage)
    }

    @Test fun intervalShowsCountdownAndNextPeriod() {
        val s = makeState(listOf(CommandKind.SetKickoffTeam(Team.HOME) to 0.0, start to 0.0, end to 2700.0))
        val d = MatchDisplay(s, 2760.0, E)
        assertEquals(MatchDisplay.Screen.INTERVAL, d.screen)
        assertEquals("14:00", d.intervalClock)
        assertEquals("2H", d.nextPeriodLabel)
        assertEquals(Team.AWAY, d.kickoffTeam)
        assertEquals("00:00", MatchDisplay(s, 9999.0, E).intervalClock)
    }

    @Test fun longMatchesKeepThreeDigitMinutes() {
        assertEquals("100:00", MatchDisplay.format(6000))
        assertEquals("01:05", MatchDisplay.format(65))
        assertEquals("1:05", MatchDisplay.formatShort(65))
    }

    @Test fun finishedScreen() {
        val s = makeState(listOf(start to 0.0, end to 2700.0, start to 3600.0, end to 6300.0))
        val d = MatchDisplay(s, 7000.0, E)
        assertEquals(MatchDisplay.Screen.FINISHED, d.screen)
        assertEquals("2H", d.periodLabel)
    }

    @Test fun periodProgressDuringPeriodOnly() {
        val s = makeState(listOf(start to 0.0))
        assertEquals(ClockProgress(754, 2700), MatchDisplay(s, 754.0, E).periodProgress)
        assertEquals(ClockProgress(2833, 2700), MatchDisplay(s, 2833.0, R).periodProgress)
        assertNull(MatchDisplay(makeState(emptyList()), 0.0, E).periodProgress)
    }

    @Test fun intervalProgressDuringBreakOnly() {
        val s = makeState(listOf(start to 0.0, end to 2760.0))
        val d = MatchDisplay(s, 2760.0 + 300, E)
        assertEquals(ClockProgress(300, 900), d.intervalProgress)
        assertNull(d.periodProgress)
        assertNull(MatchDisplay(makeState(listOf(start to 0.0)), 10.0, E).intervalProgress)
    }

    @Test fun shootoutResultsInKickOrder() {
        val rules = RuleSet(penalties = true, penaltyKicksPerTeam = 3)
        val s = makeState(rules, listOf(start to 0.0, end to 2700.0, start to 3600.0, end to 6300.0,
            CommandKind.SetShootoutFirstTeam(Team.HOME) to 6400.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.HOME, null, true)) to 6410.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.AWAY, null, false)) to 6420.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.HOME, null, false)) to 6430.0))
        val shootout = MatchDisplay(s, 6440.0, E).shootout
        assertEquals(TeamPair(listOf(true, false), listOf(false)), shootout?.results)
        assertEquals(3, shootout?.kicksPerTeam)
    }
}
