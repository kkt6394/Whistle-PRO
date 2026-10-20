package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 아이폰 디자인 개편: 목록 줄 값, 이번 달 요약, 요약 v4, 상세 탭 */
class PhoneRedesignTests {
    private fun summary(
        start: Double, goals: Int = 0, yellows: Int = 0, reds: Int = 0, distance: Double? = null,
        shootout: List<Int>? = null, outcome: ReportOutcome = ReportOutcome.Completed,
    ): MatchSummary = MatchSummary.of(makeRecord(emptyList())).copy(
        startedAt = start, goals = goals, yellows = yellows, reds = reds, distance = distance,
        shootout = shootout, outcome = outcome, homeScore = 2, awayScore = 1,
    )

    @Test fun chipsSkipZeroAndMissingDistance() {
        val item = MatchListItem.of(summary(0.0, goals = 3, yellows = 0, reds = 1))
        assertEquals(listOf(ListChip.Goals(3), ListChip.Reds(1)), item.chips)
    }

    @Test fun chipsIncludeShootoutFirstAndDistance() {
        val item = MatchListItem.of(summary(0.0, goals = 0, distance = 9800.0, shootout = listOf(4, 3)))
        assertEquals(listOf(ListChip.Shootout(4, 3), ListChip.Distance(9800.0)), item.chips)
    }

    @Test fun zeroDistanceIsNotAChip() {
        assertEquals(emptyList(), MatchListItem.of(summary(0.0, distance = 0.0)).chips)
    }

    @Test fun abandonedFlag() {
        assertTrue(MatchListItem.of(summary(0.0, outcome = ReportOutcome.Abandoned("심판 판단"))).abandoned)
        assertFalse(MatchListItem.of(summary(0.0)).abandoned)
    }

    @Test fun ruleTextFromV4AndNilForOld() {
        var s = summary(0.0)
        assertEquals(2L to 45L, MatchListItem.of(s).ruleText)   // MatchSummary.of(record)가 FIFA 표준 규칙을 채운다
        s = s.copy(periodCount = 4, periodMinutes = 25)
        assertEquals(4L to 25L, MatchListItem.of(s).ruleText)
        s = s.copy(periodCount = null, periodMinutes = null)
        assertNull(MatchListItem.of(s).ruleText)
    }

    @Test fun monthBoundaries() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = LocalDate.of(2026, 10, 16).atStartOfDay(zone).plusHours(12).toInstant()
        val oct1 = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toEpochSecond().toDouble()
        val summaries = listOf(
            summary(oct1 - 1, yellows = 5, distance = 10000.0),           // 9월 30일 23:59:59 → 빠짐
            summary(oct1, yellows = 2, distance = 12000.0),               // 10월 1일 0시 → 포함
            summary(oct1 + 86_400 * 31, yellows = 9, distance = 9000.0),  // 11월 → 빠짐
            summary(oct1 + 3600, yellows = 1, reds = 1, distance = 0.0, outcome = ReportOutcome.Abandoned("x")),
        )
        val m = MonthSummary.of(summaries, now, zone)
        assertEquals(2, m.matches)
        assertEquals(12000.0, m.distance)
        assertEquals(2.0, m.cardsPerMatch)   // 완료 경기만 평균 (SeasonStats 규칙)
    }

    @Test fun emptyMonth() {
        val m = MonthSummary.of(emptyList(), Instant.now(), ZoneId.systemDefault())
        assertTrue(m.matches == 0 && m.distance == 0.0 && m.cardsPerMatch == null)
    }

    @Test fun detailTabsFollowData() {
        assertEquals(listOf(DetailTab.RECORD, DetailTab.WORKOUT, DetailTab.MAP), DetailTab.available(hasWorkout = true, hasMap = true))
        assertEquals(listOf(DetailTab.RECORD, DetailTab.WORKOUT), DetailTab.available(hasWorkout = true, hasMap = false))
        assertEquals(listOf(DetailTab.RECORD), DetailTab.available(hasWorkout = false, hasMap = false))
        // GPS만 있고 운동 통계가 없는 경우는 없지만, 있으면 지도만 더한다
        assertEquals(listOf(DetailTab.RECORD, DetailTab.MAP), DetailTab.available(hasWorkout = false, hasMap = true))
    }

    /** 상세 [기록] 탭: 골·징계·교체를 기록한 순서대로 한 줄씩 */
    @Test fun timelineMergesInRecordedOrder() {
        val match = listOf(
            CommandKind.StartPeriod to 0.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 600.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 900.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 1200.0,
            CommandKind.EndPeriod to 2820.0,
            CommandKind.StartPeriod to 3720.0,
            CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 14)) to 4000.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 5, true)) to 5000.0,
            CommandKind.EndPeriod to 6420.0,
        )
        val report = MatchReport(MatchBundle(makeRecord(match), null))
        assertEquals(report.goals.size + report.discipline.size + report.substitutions.size, report.timeline.size)
        assertEquals(
            listOf(ResolvedEventKind.GOAL, ResolvedEventKind.YELLOW, ResolvedEventKind.SECOND_YELLOW,
                ResolvedEventKind.SUBSTITUTION, ResolvedEventKind.OWN_GOAL),
            report.timeline.map { it.kind },
        )
        assertEquals(report.goals.first(), report.timeline.first())
    }

    /** 상세 심박 선 그래프: 1분 칸 평균으로 줄이고, 기간이 다르면 같은 칸이어도 섞지 않는다 */
    @Test fun heartRateBucketsAveragePerMinuteWithinPeriod() {
        val points = listOf(
            ChartPoint(0.0, 120.0, 0),
            ChartPoint(0.5, 140.0, 0),    // 0분 칸 평균 130
            ChartPoint(1.2, 150.0, 0),    // 1분 칸
            ChartPoint(1.9, 100.0, 1),    // 같은 1분 칸이지만 다음 기간 → 따로
            ChartPoint(60.25, 160.0, 1),
            ChartPoint(60.75, 170.0, 1),
        )
        val smoothed = ChartPoint.bucketed(points, minutes = 1.0)
        assertEquals(listOf(130.0, 150.0, 100.0, 165.0), smoothed.map { it.value })
        assertEquals(listOf(0, 0, 1, 1), smoothed.map { it.period })
        assertEquals(listOf(0.25, 1.2, 1.9, 60.5), smoothed.map { it.minute })   // 칸 안 점들의 평균 시각
        assertTrue(ChartPoint.bucketed(emptyList(), minutes = 1.0).isEmpty())
    }

    @Test fun v3SummaryDecodesWithoutRuleFields() {
        val s = summary(0.0)
        val json = JsonObject(s.toJson().filterKeys { it != "periodCount" && it != "periodMinutes" } +
            ("summaryVersion" to JsonPrimitive(3)))
        val decoded = MatchSummary.fromJson(json)
        assertNull(decoded.periodCount)
        assertFalse(decoded.isCurrent)   // v4가 되어 폰 보관함이 다시 만든다
    }
}
