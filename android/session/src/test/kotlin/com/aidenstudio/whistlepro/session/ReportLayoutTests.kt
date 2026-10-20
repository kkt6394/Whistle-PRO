package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportLayoutTests {
    private val match = listOf(
        CommandKind.StartPeriod to 0.0,
        CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 600.0,
        CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 900.0,
        CommandKind.ToggleStoppage to 1000.0, CommandKind.ToggleStoppage to 1090.0,
        CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 1200.0,
        CommandKind.EndPeriod to 2820.0,
        CommandKind.StartPeriod to 3720.0,
        CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 14)) to 4000.0,
        CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 5, true)) to 5000.0,
        CommandKind.EndPeriod to 6420.0,
    )

    private fun report(commands: List<Pair<CommandKind, Double>>, setup: MatchSetup = MatchSetup()) =
        MatchReport(MatchBundle(makeRecord(setup, commands), null))

    @Test fun periodScoresSplitByPeriod() {
        // 전반: 홈 골 1, 후반: 원정 자책골 → 홈 득점
        assertEquals(listOf(TeamPair(1, 0), TeamPair(1, 0)), report(match).periodScores)
    }

    /** 휴식 중에는 골을 기록할 수 없다(엔진이 막는다). 교체·카드만 들어가고 기간 점수에는 영향이 없다. */
    @Test fun breakEventsDoNotAffectPeriodScores() {
        val commands = listOf(
            CommandKind.StartPeriod to 0.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 3, false)) to 100.0,
            CommandKind.EndPeriod to 2700.0,
            CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 1, 2)) to 2750.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
        )
        assertEquals(listOf(TeamPair(0, 1), TeamPair(0, 0)), report(commands).periodScores)
    }

    @Test fun shootoutGoalsNotInPeriodScores() {
        val commands = listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
            CommandKind.SetShootoutFirstTeam(Team.HOME) to 6400.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.HOME, null, true)) to 6410.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.AWAY, null, false)) to 6420.0,
        )
        val r = report(commands, MatchSetup(rules = RuleSet(penalties = true)))
        assertEquals(listOf(TeamPair(0, 0), TeamPair(0, 0)), r.periodScores)
    }

    @Test fun totalsCountCardsAndSubs() {
        val r = report(match)
        assertEquals(ReportTotals(goals = 2, yellows = 0, dismissals = 0, substitutions = 1), r.totals.home)
        assertEquals(ReportTotals(goals = 0, yellows = 2, dismissals = 1, substitutions = 0), r.totals.away)
    }

    @Test fun ownGoalCountsForOpponent() {
        val r = report(match)
        assertEquals(
            listOf(ResolvedEventKind.GOAL, ResolvedEventKind.SUBSTITUTION, ResolvedEventKind.OWN_GOAL),
            r.columnRows.mapNotNull { it.home }.map { it.kind })
        assertEquals(
            listOf(ResolvedEventKind.YELLOW, ResolvedEventKind.SECOND_YELLOW),
            r.columnRows.mapNotNull { it.away }.map { it.kind })
        assertEquals(3, r.columnRows.size)
    }

    @Test fun columnRowsEmptyWhenNoEvents() {
        assertTrue(report(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0)).columnRows.isEmpty())
    }

    @Test fun chipsSplitAtEight() {
        val eight = List(8) { ReportLine(EventMinute.Regular(1), Team.HOME, ResolvedEventKind.GOAL, "#1") }
        assertTrue(SNSCardChips.split(eight).first.size == 8 && SNSCardChips.split(eight).second == 0)
        val twelve = eight + eight.take(4)
        assertTrue(SNSCardChips.split(twelve).first.size == 7 && SNSCardChips.split(twelve).second == 5)
    }
}
