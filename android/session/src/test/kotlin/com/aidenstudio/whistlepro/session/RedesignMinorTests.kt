package com.aidenstudio.whistlepro.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 디자인 개편 리뷰에서 남은 작은 문제들: 이번 달 거리 글자, 목록 날짜 연도, 시즌 통계 표 줄 */
class RedesignMinorTests {
    private fun summary(
        start: Double, outcome: ReportOutcome = ReportOutcome.Completed, goals: Int = 0, yellows: Int = 0,
        added: Int = 0, distance: Double? = null, sprints: Int? = null,
    ): MatchSummary = MatchSummary.of(makeRecord(emptyList(), createdAt = start)).copy(
        startedAt = start, outcome = outcome, goals = goals, yellows = yellows, reds = 0, sinBins = 0,
        addedSeconds = added, distance = distance, sprints = sprints,
    )

    // 1. 이번 달 요약 거리

    /** 정수 반올림이면 0.4km가 "0"으로 보였다: 10km 미만은 소수 한 자리 */
    @Test fun monthDistanceUnderTenShowsOneDecimal() {
        assertEquals("0.4", MonthSummary.distanceText(400.0))
        assertEquals("9.8", MonthSummary.distanceText(9800.0))
        assertEquals("0.0", MonthSummary.distanceText(0.0))
        assertEquals("1.0", MonthSummary.distanceText(1049.0))
    }

    @Test fun monthDistanceTenOrMoreIsWhole() {
        assertEquals("10", MonthSummary.distanceText(10_000.0))
        assertEquals("43", MonthSummary.distanceText(42_600.0))
        // 9.96km는 한 자리로 올리면 10.0이 되므로 정수 "10"
        assertEquals("10", MonthSummary.distanceText(9_960.0))
    }

    // 2. 목록 날짜 연도

    @Test fun yearShownOnlyForOtherYears() {
        val zone = ZoneId.of("Asia/Seoul")
        val now: Instant = LocalDate.of(2026, 10, 16).atStartOfDay(zone).plusHours(12).toInstant()
        val jan1 = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toEpochSecond().toDouble()
        val dec3 = LocalDate.of(2025, 12, 3).atStartOfDay(zone).plusHours(19).toEpochSecond().toDouble()
        assertFalse(MatchListItem.showsYear(jan1, now, zone))
        assertTrue(MatchListItem.showsYear(jan1 - 1, now, zone))   // 2025년 12월 31일 23:59:59
        assertTrue(MatchListItem.showsYear(dec3, now, zone))
        // 기기 시계가 틀려 내년 경기가 와도 연도를 붙인다
        val next = LocalDate.of(2027, 2, 1).atStartOfDay(zone).toEpochSecond().toDouble()
        assertTrue(MatchListItem.showsYear(next, now, zone))
    }

    // 6. 시즌 통계 표 줄

    @Test fun addedTimeAndDistanceHaveTotalAndPerMatch() {
        val stats = SeasonStats.aggregate(listOf(
            summary(1.0, goals = 2, yellows = 1, added = 240, distance = 10_000.0, sprints = 20),
            summary(2.0, goals = 1, added = 120, distance = 8_000.0, sprints = 10),
            summary(3.0, outcome = ReportOutcome.Abandoned("비"), added = 60, distance = 1_000.0, sprints = 1),
        ))
        val lines = stats.lines
        assertEquals(
            listOf(StatLine.Kind.GOALS, StatLine.Kind.YELLOWS, StatLine.Kind.REDS, StatLine.Kind.ADDED_TIME,
                StatLine.Kind.DISTANCE, StatLine.Kind.SPRINTS),
            lines.map { it.kind },
        )
        val added = lines.first { it.kind == StatLine.Kind.ADDED_TIME }
        assertEquals(420.0, added.total)      // 중단 경기까지 합계
        assertEquals(180.0, added.perMatch)   // 완료 경기만 평균
        val distance = lines.first { it.kind == StatLine.Kind.DISTANCE }
        assertEquals(19_000.0, distance.total)
        assertEquals(9_000.0, distance.perMatch)
        assertEquals(StatLine(StatLine.Kind.GOALS, 3.0, 1.5), lines.first { it.kind == StatLine.Kind.GOALS })
    }

    @Test fun linesWithoutWorkoutOrCompletedMatches() {
        // 중단 경기뿐: 합계는 있고 경기당은 없다 ("--")
        val stats = SeasonStats.aggregate(listOf(summary(1.0, outcome = ReportOutcome.Abandoned("x"), added = 90)))
        val lines = stats.lines
        // 운동 기록이 없으면 거리·스프린트 줄이 없다
        assertEquals(listOf(StatLine.Kind.GOALS, StatLine.Kind.YELLOWS, StatLine.Kind.REDS, StatLine.Kind.ADDED_TIME), lines.map { it.kind })
        assertEquals(StatLine(StatLine.Kind.ADDED_TIME, 90.0, null), lines.first { it.kind == StatLine.Kind.ADDED_TIME })
        assertTrue(lines.all { it.perMatch == null })
    }

    @Test fun sinBinLineOnlyWhenAny() {
        val s = summary(1.0).copy(sinBins = 2)
        assertEquals(
            listOf(StatLine.Kind.GOALS, StatLine.Kind.YELLOWS, StatLine.Kind.REDS, StatLine.Kind.SIN_BINS, StatLine.Kind.ADDED_TIME),
            SeasonStats.aggregate(listOf(s)).lines.map { it.kind },
        )
    }
}
