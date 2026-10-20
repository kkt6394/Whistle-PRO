package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchReportTests {
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

    /** 백로그: 공유 파일 이름이 "WhistlePRO-1791945042"처럼 숫자였다 → 날짜와 팀 이름 */
    @Test fun fileStemHasDateAndTeams() {
        val setup = MatchSetup(homeName = "FC 서울숲", awayName = "한강/유나이티드:")
        // 2026-10-15 13:00 UTC
        val report = MatchReport(MatchBundle(makeRecord(setup, listOf(CommandKind.StartPeriod to 1_792_069_200.0)), null))
        assertEquals("WhistlePRO 2026-10-15 FC 서울숲 vs 한강-유나이티드-", report.fileStem(ZoneId.of("Asia/Seoul")))
        assertTrue(report.fileStem(ZoneId.of("America/Los_Angeles")).startsWith("WhistlePRO 2026-10-15 "))
    }

    /** 제출용 보고서의 "경기 방식" 줄은 기간 수·길이·연장·승부차기를 규칙에서 읽는다 */
    @Test fun reportCarriesRules() {
        val rules = RuleSet(periodCount = 4, periodLength = 1500, extraTime = true, penalties = true)
        val report = MatchReport(MatchBundle(makeRecord(MatchSetup(rules = rules), listOf(CommandKind.StartPeriod to 0.0)), null))
        assertEquals(rules, report.rules)
    }

    @Test fun reportSummarizesMatch() {
        val report = MatchReport(MatchBundle(makeRecord(match, createdAt = -10.0), null))
        assertEquals(ReportOutcome.Completed, report.outcome)
        assertEquals(TeamPair(2, 0), report.score)
        assertNull(report.shootout)
        assertEquals(0.0, report.startedAt)
        assertEquals(listOf(EventMinute.Regular(11), EventMinute.Regular(67)), report.goals.map { it.minute })
        assertEquals(listOf(ResolvedEventKind.GOAL, ResolvedEventKind.OWN_GOAL), report.goals.map { it.kind })
        assertEquals(listOf(ResolvedEventKind.YELLOW, ResolvedEventKind.SECOND_YELLOW), report.discipline.map { it.kind })
        assertEquals(listOf("#7 → #14"), report.substitutions.map { it.detail })
        assertEquals(
            listOf(PeriodSummary("1H", 2820, 90, 120), PeriodSummary("2H", 2700, 0, 0)),
            report.periods,
        )
        assertTrue(report.ruleDeviations.isEmpty())
    }

    @Test fun reportWithoutWorkout() {
        val report = MatchReport(MatchBundle(makeRecord(match), null))
        assertNull(report.workoutOverall)
        assertTrue(report.workoutPeriods.isEmpty())
    }

    @Test fun reportWithWorkoutSplitsPeriods() {
        val record = makeRecord(match)
        val workout = WorkoutLog(
            record.id,
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2820.0, 5000.0), WorkoutSample(6420.0, 9800.0)),
        )
        val report = MatchReport(MatchBundle(record, workout))
        assertEquals(listOf(5000.0, 4800.0), report.workoutPeriods.map { it.distance })
        assertEquals(9800.0, report.workoutOverall?.distance)
    }

    @Test fun abandonedOutcome() {
        val report = MatchReport(MatchBundle(
            makeRecord(listOf(CommandKind.StartPeriod to 0.0, CommandKind.AbandonMatch("폭우") to 1200.0)), null))
        assertEquals(ReportOutcome.Abandoned("폭우"), report.outcome)
        assertEquals(listOf(1200L), report.periods.map { it.played })
    }

    @Test fun shootoutInReport() {
        val setup = MatchSetup(rules = RuleSet(penalties = true))
        val commands = listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
            CommandKind.SetShootoutFirstTeam(Team.HOME) to 6400.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.HOME, null, true)) to 6410.0,
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.AWAY, null, false)) to 6420.0,
        )
        val report = MatchReport(MatchBundle(makeRecord(setup, commands), null))
        assertEquals(TeamPair(1, 0), report.shootout)
        assertEquals(listOf(RuleLine(RuleSet.Field.penalties, "true")), report.ruleDeviations)
    }
}
