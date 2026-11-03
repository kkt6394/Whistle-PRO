package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.EventWarning
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val start = CommandKind.StartPeriod
private val end = CommandKind.EndPeriod
private fun event(p: EventPayload) = CommandKind.RecordEvent(p)

class EventPresentationTests {
    @Test fun rowsAreNewestFirstWithFootballMinutes() {
        val s = makeState(listOf(start to 0.0,
            event(EventPayload.Goal(Team.HOME, 9, false)) to 0.0,
            event(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 1379.0,
            event(EventPayload.Substitution(Team.HOME, 7, 14)) to 2760.0))
        val rows = s.eventRows
        assertEquals(listOf(ResolvedEventKind.SUBSTITUTION, ResolvedEventKind.YELLOW, ResolvedEventKind.GOAL), rows.map { it.kind })
        assertEquals(listOf(EventMinute.Added(45, 2), EventMinute.Regular(23), EventMinute.Regular(1)), rows.map { it.minute })
        assertEquals(listOf("#7 → #14", "#4", "#9"), rows.map { it.detail })
    }

    @Test fun lastEventRowIsNewestEvent() {
        val s = makeState(listOf(start to 0.0,
            event(EventPayload.Goal(Team.HOME, 9, false)) to 0.0,
            event(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 1379.0))
        val row = assertNotNull(s.lastEventRow(MatchSetup()))
        assertEquals(ResolvedEventKind.YELLOW, row.kind)
        assertEquals("#4", row.detail)
        assertEquals(s.eventRows(MatchSetup()).first(), row)
    }

    @Test fun lastEventRowNullWithoutEvents() {
        assertNull(makeState(listOf(start to 0.0)).lastEventRow(MatchSetup()))
    }

    @Test fun lastEventRowFallsBackAfterUndo() {
        val s = makeState(listOf(start to 0.0,
            event(EventPayload.Goal(Team.HOME, 9, false)) to 0.0,
            event(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 1379.0,
            CommandKind.Undo to 1380.0))
        assertEquals(ResolvedEventKind.GOAL, s.lastEventRow(MatchSetup())?.kind)
    }

    @Test fun secondHalfMinutesContinue() {
        val s = makeState(listOf(start to 0.0, end to 2700.0, start to 3600.0,
            event(EventPayload.Goal(Team.AWAY, null, false)) to 4200.0))
        assertEquals(EventMinute.Regular(56), s.eventRows.first().minute)
        assertEquals("", s.eventRows.first().detail)
    }

    @Test fun eventsOutsidePlayAndShootout() {
        val s = makeState(RuleSet(penalties = true), listOf(
            start to 0.0, end to 2700.0,
            event(EventPayload.Card(Team.HOME, null, CardKind.YELLOW)) to 2800.0,
            start to 3600.0, end to 6300.0,
            CommandKind.SetShootoutFirstTeam(Team.AWAY) to 6400.0,
            event(EventPayload.PenaltyKick(Team.AWAY, 10, false)) to 6410.0,
        ))
        val rows = s.eventRows
        assertEquals(listOf(EventMinute.Shootout, EventMinute.AfterPeriod("1H")), rows.map { it.minute })
        assertEquals(false, rows[0].scored)
        assertEquals("#10", rows[0].detail)
    }

    @Test fun feedbackForSecondYellowAndWarnings() {
        val commands = listOf(
            Command("s", 0.0, start),
            Command("y1", 10.0, event(EventPayload.Card(Team.HOME, 4, CardKind.YELLOW))),
            Command("y2", 20.0, event(EventPayload.Card(Team.HOME, 4, CardKind.YELLOW))),
            Command("r", 21.0, event(EventPayload.Card(Team.HOME, 4, CardKind.RED))),
        )
        val s = MatchEngine.reduce(RuleSet.FIFA_STANDARD, commands)
        assertEquals(RecordFeedback.None, s.feedback(forEvent = "y1"))
        assertEquals(RecordFeedback.SecondYellow("y2"), s.feedback(forEvent = "y2"))
        assertEquals(RecordFeedback.Warning(EventWarning.PLAYER_ALREADY_SENT_OFF, "r"), s.feedback(forEvent = "r"))
        assertEquals(RecordFeedback.None, s.feedback(forEvent = "missing"))
    }

    @Test fun sinBinBadgesAndShortHandedTeams() {
        val rules = RuleSet(playersPerTeam = 7, minimumPlayers = 6, sinBin = true)
        val s = makeState(rules, listOf(start to 0.0,
            event(EventPayload.Card(Team.AWAY, 8, CardKind.SIN_BIN)) to 100.0,
            event(EventPayload.Card(Team.AWAY, 5, CardKind.RED)) to 120.0))
        val d = MatchDisplay(s, 160.0, ClockDisplay.ELAPSED)
        assertEquals(listOf(SinBinBadge(Team.AWAY, 8, "9:00", 540)), d.sinBins)
        assertEquals(listOf(Team.AWAY), d.shortHandedTeams)
    }

    @Test fun shootoutDisplay() {
        val rules = RuleSet(penalties = true)
        val halves = listOf(start to 0.0, end to 2700.0, start to 3600.0, end to 6300.0)
        val waiting = MatchDisplay(makeState(rules, halves), 6400.0, ClockDisplay.ELAPSED)
        assertEquals(MatchDisplay.Screen.PENALTIES, waiting.screen)
        assertEquals(ShootoutDisplay(TeamPair(0, 0), null, true, TeamPair(emptyList(), emptyList()), 5), waiting.shootout)

        val kicked = MatchDisplay(
            makeState(rules, halves + listOf(CommandKind.SetShootoutFirstTeam(Team.HOME) to 6400.0,
                event(EventPayload.PenaltyKick(Team.HOME, null, true)) to 6410.0)),
            6420.0, ClockDisplay.ELAPSED)
        assertEquals(ShootoutDisplay(TeamPair(1, 0), Team.AWAY, false, TeamPair(listOf(true), emptyList()), 5), kicked.shootout)
        assertNull(MatchDisplay(makeState(halves), 6400.0, ClockDisplay.ELAPSED).shootout)
    }

    @Test fun preExtraTimeShowsWaitingTime() {
        val s = makeState(RuleSet(extraTime = true), listOf(start to 0.0, end to 2700.0, start to 3600.0, end to 6300.0))
        val d = MatchDisplay(s, 6390.0, ClockDisplay.ELAPSED)
        assertEquals(MatchDisplay.Screen.PRE_EXTRA_TIME, d.screen)
        assertEquals("+1:30", d.intervalClock)
        assertEquals(IntervalKind.PRE_EXTRA_TIME, d.intervalKind)
        assertEquals(IntervalClockTone.WAITING, d.intervalTone)
        assertEquals("ET1", d.nextPeriodLabel)
    }
}

class LastCommandTests {
    @Test fun lastCommandIDTracksAcceptedCommandsOnly() {
        val s = MatchSession.start(MatchSetup(), MemoryStore(), now = { 0.0 })
        assertNull(s.lastCommandID)
        s.send(start)
        val id = assertNotNull(s.lastCommandID)
        assertEquals(s.record.commands.last().id, id)
        s.send(start)
        assertEquals(id, s.lastCommandID)
    }
}
