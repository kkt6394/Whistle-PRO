package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val sinBinRules = RuleSet(sinBin = true, sinBinLength = 600)

class SinBinTests {
    @Test fun countsDownWithPlayedTime() {
        val s = play(sinBinRules, listOf(cmd(start, 0.0), card(Team.AWAY, 8, CardKind.SIN_BIN, 300.0, id = "s")))
        assertEquals(listOf(ActiveSinBin("s", Team.AWAY, 8, 500.0)), s.activeSinBins(400.0))
        assertEquals(TeamPair(11L, 10L), s.playersOnField(400.0))
    }

    @Test fun expiresAfterLength() {
        val s = play(sinBinRules, listOf(cmd(start, 0.0), card(Team.AWAY, 8, CardKind.SIN_BIN, 300.0)))
        assertTrue(s.activeSinBins(900.0).isEmpty())
        assertEquals(TeamPair(11L, 11L), s.playersOnField(900.0))
    }

    @Test fun pausesDuringInterval() {
        val s = play(sinBinRules, listOf(cmd(start, 0.0), card(Team.HOME, 3, CardKind.SIN_BIN, 2500.0), cmd(end, 2700.0), cmd(start, 3600.0)))
        assertEquals(400L, s.activeSinBins(3000.0).firstOrNull()?.remaining)
        assertEquals(300L, s.activeSinBins(3700.0).firstOrNull()?.remaining)
    }

    @Test fun issuedDuringIntervalStartsAtNextPeriod() {
        val s = play(sinBinRules, listOf(cmd(start, 0.0), cmd(end, 2700.0), card(Team.HOME, 3, CardKind.SIN_BIN, 2800.0), cmd(start, 3600.0)))
        assertEquals(600L, s.activeSinBins(3600.0).firstOrNull()?.remaining)
        assertEquals(500L, s.activeSinBins(3700.0).firstOrNull()?.remaining)
    }

    @Test fun fractionalTimestampsRoundRemainingUp() {
        val s = play(sinBinRules, listOf(cmd(start, 0.0), card(Team.HOME, 3, CardKind.SIN_BIN, 10.4)))
        assertEquals(591L, s.activeSinBins(20.0).firstOrNull()?.remaining)
        assertEquals(1L, s.activeSinBins(610.3).firstOrNull()?.remaining)
    }
}
