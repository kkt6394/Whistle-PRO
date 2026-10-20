package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertTests {
    @Test fun periodHasWarningThenEnd() {
        val s = play(listOf(cmd(start, 0.0)))
        assertEquals(listOf(
            MatchAlert(AlertKind.WARNING_BEFORE_END, 2400.0),
            MatchAlert(AlertKind.PERIOD_END, 2700.0),
        ), s.alerts(0.0, 300, 300))
        assertEquals(MatchAlert(AlertKind.PERIOD_END, 2700.0), s.nextAlert(2400.0, 300, 300))
        assertNull(s.nextAlert(2700.0, 300, 300))
    }

    @Test fun warningCanBeDisabledOrTooLong() {
        val s = play(RuleSet(periodLength = 240), listOf(cmd(start, 0.0)))
        assertEquals(listOf(MatchAlert(AlertKind.PERIOD_END, 240.0)), s.alerts(0.0, 0, 300))
        assertEquals(listOf(MatchAlert(AlertKind.PERIOD_END, 240.0)), s.alerts(0.0, 300, 300))
    }

    @Test fun intervalHasEndingSoonThenEnd() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2750.0)))
        assertEquals(listOf(
            MatchAlert(AlertKind.BREAK_ENDING_SOON, 3350.0),
            MatchAlert(AlertKind.BREAK_END, 3650.0),
        ), s.alerts(2750.0, 300, 300))
    }

    @Test fun breakWarningCanBeChangedOrDisabled() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2750.0)))
        assertEquals(listOf(
            MatchAlert(AlertKind.BREAK_ENDING_SOON, 3590.0),
            MatchAlert(AlertKind.BREAK_END, 3650.0),
        ), s.alerts(2750.0, 300, 60))
        assertEquals(listOf(MatchAlert(AlertKind.BREAK_END, 3650.0)), s.alerts(2750.0, 300, 0))
    }

    @Test fun shortIntervalSkipsEndingSoon() {
        val s = play(RuleSet(halfTimeLength = 90), listOf(cmd(start, 0.0), cmd(end, 2700.0)))
        assertEquals(listOf(MatchAlert(AlertKind.BREAK_END, 2790.0)), s.alerts(2700.0, 300, 300))
    }

    @Test fun zeroIntervalHasNoAlerts() {
        val s = play(RuleSet(halfTimeLength = 0), listOf(cmd(start, 0.0), cmd(end, 2700.0)))
        assertTrue(s.alerts(2700.0, 300, 300).isEmpty())
    }

    @Test fun sinBinEndIsAlerted() {
        val s = play(RuleSet(sinBin = true), listOf(cmd(start, 0.0), card(Team.HOME, 4, CardKind.SIN_BIN, 100.0)))
        assertEquals(MatchAlert(AlertKind.SIN_BIN_END, 700.0), s.nextAlert(100.0, 300, 300))
    }

    @Test fun noAlertsWhenFinished() {
        assertTrue(play(twoHalves()).alerts(6300.0, 300, 300).isEmpty())
    }
}
