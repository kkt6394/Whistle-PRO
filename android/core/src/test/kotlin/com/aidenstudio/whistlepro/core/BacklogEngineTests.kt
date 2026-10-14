package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val pkRules = RuleSet(penalties = true, sinBin = true)

private fun kick(team: Team, scored: Boolean, at: Double, id: String) =
    cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(team, null, scored)), at, id)

/** home O O O, away X X X → 3:0 결정 (마지막 킥 id "last") */
private fun decidedShootout(): List<Command> = twoHalves() + listOf(
    cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6400.0),
    kick(Team.HOME, true, 6410.0, "h1"), kick(Team.AWAY, false, 6420.0, "a1"),
    kick(Team.HOME, true, 6430.0, "h2"), kick(Team.AWAY, false, 6440.0, "a2"),
    kick(Team.HOME, true, 6450.0, "h3"), kick(Team.AWAY, false, 6460.0, "last"),
)

class BacklogEngineTests {
    @Test fun sinBinsEndWhenShootoutStarts() {
        val s = play(pkRules, listOf(cmd(start, 0.0), cmd(end, 2700.0), cmd(start, 3600.0),
            card(Team.HOME, 5, CardKind.SIN_BIN, 6200.0), cmd(end, 6300.0)))
        assertEquals(Phase.Penalties, s.phase)
        assertTrue(s.activeSinBins(6300.0).isEmpty())
        assertEquals(TeamPair(11, 11), s.playersOnField(6300.0))
    }

    @Test fun sinBinsEndWhenMatchFinishes() {
        val s = play(RuleSet(sinBin = true), listOf(cmd(start, 0.0), card(Team.HOME, 5, CardKind.SIN_BIN, 2600.0), cmd(end, 2700.0),
            cmd(start, 3600.0), cmd(CommandKind.AbandonMatch("x"), 3610.0)))
        assertTrue(s.activeSinBins(3610.0).isEmpty())
    }

    @Test fun shootoutLabelIsPK() {
        assertEquals("PK", play(pkRules, twoHalves()).currentPeriodLabel)
        assertEquals("PK", play(pkRules, decidedShootout()).currentPeriodLabel)
        assertEquals("2H", play(twoHalves()).currentPeriodLabel)
    }

    @Test fun hugeTimestampsDoNotTrap() {
        val s = play(listOf(cmd(start, 0.0), cmd(toggle, 1e300)))
        s.snapshot(Double.POSITIVE_INFINITY, 300)
        s.snapshot(1e300, 300)
        s.snapshot(Double.NaN, 300)
        assertEquals(0L, wholeSeconds(Double.POSITIVE_INFINITY))
        assertEquals(1_000_000_000_000L, wholeSeconds(1e300))
        assertEquals(-6L, wholeSeconds(-5.5))
    }

    @Test fun deletingDecisiveKickReopensShootout() {
        val reopened = play(pkRules, decidedShootout() + cmd(CommandKind.DeleteEvent("last"), 6500.0))
        assertEquals(Phase.Penalties, reopened.phase)
        assertEquals(Team.AWAY, reopened.shootout.next)
    }

    @Test fun editingDecisiveKickAfterFinishReopensShootout() {
        // h3를 실패로 고치면 2:0, away 2회 남음 → 미정
        val edited = play(pkRules, decidedShootout() +
            cmd(CommandKind.EditEvent("h3", EventPayload.PenaltyKick(Team.HOME, null, false)), 6500.0))
        assertEquals(Phase.Penalties, edited.phase)
        assertEquals(TeamPair(2, 0), edited.shootout.goals)
    }

    @Test fun kickNumberCanBeFixedAfterFinish() {
        val s = play(pkRules, decidedShootout() +
            cmd(CommandKind.EditEvent("h1", EventPayload.PenaltyKick(Team.HOME, 9, true)), 6500.0, id = "e"))
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(completed, s.phase)
    }
}
