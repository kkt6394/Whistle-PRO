package com.aidenstudio.whistlepro.core

import com.aidenstudio.whistlepro.core.ResolvedEventKind.GOAL
import com.aidenstudio.whistlepro.core.ResolvedEventKind.OWN_GOAL
import com.aidenstudio.whistlepro.core.ResolvedEventKind.SECOND_YELLOW
import com.aidenstudio.whistlepro.core.ResolvedEventKind.YELLOW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTests {
    @Test fun goalsAndOwnGoalsCount() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0), goal(Team.AWAY, 200.0),
            cmd(CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 4, true)), 300.0)))
        assertEquals(TeamPair(2, 1), s.score)
        assertEquals(listOf(GOAL, GOAL, OWN_GOAL), s.resolvedEvents.map { it.kind })
    }

    @Test fun eventStoresPeriodAndMatchSecond() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2760.0), cmd(start, 3660.0), goal(Team.HOME, 3700.0)))
        assertEquals(EventTime(regular(2), true, 2740), s.events[0].time)
    }

    @Test fun goalOutsidePlayIsRejectedButCardsAreAllowedInInterval() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2700.0),
            goal(Team.HOME, 2800.0, id = "g"), card(Team.HOME, 5, CardKind.YELLOW, 2800.0, id = "c")))
        assertEquals(listOf("g"), s.ignoredCommandIds)
        assertEquals(1, s.events.size)
        assertEquals(EventTime(regular(1), false, 2700), s.events[0].time)
    }

    @Test fun eventsBeforeKickoffAreRejected() {
        val s = play(listOf(card(Team.HOME, 5, CardKind.YELLOW, 0.0, id = "c")))
        assertEquals(listOf("c"), s.ignoredCommandIds)
    }

    @Test fun secondYellowForSamePlayerIsSendingOff() {
        val s = play(listOf(cmd(start, 0.0), card(Team.HOME, 4, CardKind.YELLOW, 100.0), card(Team.AWAY, 4, CardKind.YELLOW, 150.0),
            card(Team.HOME, 4, CardKind.YELLOW, 200.0)))
        assertEquals(listOf(YELLOW, YELLOW, SECOND_YELLOW), s.resolvedEvents.map { it.kind })
        assertEquals(TeamPair(1, 0), s.sentOffCounts)
    }

    @Test fun secondYellowRuleCanBeDisabled() {
        val s = play(RuleSet(secondYellowSendOff = false),
            listOf(cmd(start, 0.0), card(Team.HOME, 4, CardKind.YELLOW, 100.0), card(Team.HOME, 4, CardKind.YELLOW, 200.0)))
        assertEquals(listOf(YELLOW, YELLOW), s.resolvedEvents.map { it.kind })
        assertEquals(TeamPair(0, 0), s.sentOffCounts)
    }

    @Test fun yellowWithoutNumberNeverAccumulates() {
        val s = play(listOf(cmd(start, 0.0), card(Team.HOME, null, CardKind.YELLOW, 100.0), card(Team.HOME, null, CardKind.YELLOW, 200.0)))
        assertEquals(listOf(YELLOW, YELLOW), s.resolvedEvents.map { it.kind })
    }

    @Test fun editingNumberReResolvesCards() {
        val s = play(listOf(cmd(start, 0.0), card(Team.HOME, 4, CardKind.YELLOW, 100.0), card(Team.HOME, 5, CardKind.YELLOW, 200.0, id = "y2"),
            cmd(CommandKind.EditEvent("y2", EventPayload.Card(Team.HOME, 4, CardKind.YELLOW)), 300.0)))
        assertEquals(listOf(YELLOW, SECOND_YELLOW), s.resolvedEvents.map { it.kind })
        assertEquals(200L, s.events[1].time.matchSecond)
    }

    @Test fun editCannotChangeEventType() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0, id = "g"),
            cmd(CommandKind.EditEvent("g", EventPayload.Card(Team.HOME, 9, CardKind.RED)), 200.0, id = "e")))
        assertEquals(listOf("e"), s.ignoredCommandIds)
        assertEquals(EventPayload.Goal(Team.HOME, null, false), s.events[0].payload)
    }

    @Test fun deleteRemovesEventAndUnknownIdIsIgnored() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0, id = "g"), cmd(CommandKind.DeleteEvent("g"), 150.0),
            cmd(CommandKind.DeleteEvent("nope"), 160.0, id = "d")))
        assertTrue(s.events.isEmpty())
        assertEquals(listOf("d"), s.ignoredCommandIds)
    }

    @Test fun sinBinCardRequiresSinBinRule() {
        val s = play(listOf(cmd(start, 0.0), card(Team.HOME, 4, CardKind.SIN_BIN, 100.0, id = "s")))
        assertEquals(listOf("s"), s.ignoredCommandIds)
    }

    @Test fun invalidShirtNumberIsRejected() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0, number = 100, id = "g")))
        assertEquals(listOf("g"), s.ignoredCommandIds)
    }

    @Test fun substitutionLimitWarnsWithoutBlocking() {
        val s = play(RuleSet(substitutionLimit = 1), listOf(cmd(start, 0.0), sub(Team.HOME, 100.0), sub(Team.HOME, 200.0), sub(Team.AWAY, 300.0)))
        assertEquals(3, s.events.size)
        assertEquals(listOf(emptyList(), listOf(EventWarning.SUBSTITUTION_LIMIT_EXCEEDED), emptyList()), s.resolvedEvents.map { it.warnings })
        assertEquals(TeamPair(2, 1), s.substitutionCounts)
    }

    @Test fun returnSubstitutionsDisableLimit() {
        val s = play(RuleSet(substitutionLimit = 1, returnSubstitutions = true),
            listOf(cmd(start, 0.0), sub(Team.HOME, 100.0), sub(Team.HOME, 200.0)))
        assertTrue(s.resolvedEvents.all { it.warnings.isEmpty() })
    }

    @Test fun extraTimeAddsSubstitutionAllowance() {
        val rules = RuleSet(extraTime = true, substitutionLimit = 1, extraTimeExtraSubs = 1)
        val s = play(rules, listOf(cmd(start, 0.0), sub(Team.HOME, 100.0), cmd(end, 2700.0), cmd(start, 3600.0),
            cmd(end, 6300.0), sub(Team.HOME, 6400.0), sub(Team.HOME, 6500.0)))
        assertEquals(Phase.PreExtraTime, s.phase)
        assertEquals(listOf(emptyList(), emptyList(), listOf(EventWarning.SUBSTITUTION_LIMIT_EXCEEDED)), s.resolvedEvents.map { it.warnings })
    }

    @Test fun belowMinimumPlayersWarns() {
        val rules = RuleSet(playersPerTeam = 7, minimumPlayers = 6)
        val s = play(rules, listOf(cmd(start, 0.0), card(Team.HOME, 1, CardKind.RED, 100.0), card(Team.HOME, 2, CardKind.RED, 200.0)))
        assertEquals(TeamPair(5L, 7L), s.playersOnField(300.0))
        assertEquals(listOf<MatchWarning>(MatchWarning.BelowMinimumPlayers(Team.HOME)), s.matchWarnings(300.0))
        assertEquals(listOf("belowMinimumPlayers:home"), s.matchWarnings(300.0).map { it.code })
        assertTrue(play(RuleSet(playersPerTeam = 7, minimumPlayers = null),
            listOf(cmd(start, 0.0), card(Team.HOME, 1, CardKind.RED, 100.0))).matchWarnings(300.0).isEmpty())
    }
}
