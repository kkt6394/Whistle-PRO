package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 시즌 통계 기간 경계 (M7a 리뷰: 앱 타깃에 있어 테스트하지 못했다) */
class StatsRangeTests {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun day(y: Int, m: Int, d: Int): Double = LocalDate.of(y, m, d).atStartOfDay(zone).toEpochSecond().toDouble()

    @Test fun customIncludesWholeEndDay() {
        val b = StatsRange.CUSTOM.bounds(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), zone = zone)
        assertEquals(day(2026, 10, 1), b.first)
        assertEquals(day(2026, 10, 4), b.second)
    }

    /** 시작을 끝 뒤로 옮기면 조용히 "경기가 없습니다"였다 → 시작 날 하루로 본다 */
    @Test fun customEndBeforeStartUsesStartDay() {
        val b = StatsRange.CUSTOM.bounds(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3), zone = zone)
        assertEquals(day(2026, 10, 5), b.first)
        assertEquals(day(2026, 10, 6), b.second)
    }

    /** 화면에 적는 기간 (2026-11-03 critique) */
    @Test fun shownDays() {
        val now = LocalDate.of(2026, 11, 3).atTime(17, 0).atZone(zone).toInstant()
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 11, 3), StatsRange.THIS_YEAR.shownDays(d, d, emptyList(), now, zone))
        assertEquals(LocalDate.of(2026, 10, 4) to LocalDate.of(2026, 11, 3), StatsRange.LAST_30_DAYS.shownDays(d, d, emptyList(), now, zone))
        val starts = listOf(day(2026, 5, 2) + 36_000, day(2025, 9, 1) + 72_000)
        assertEquals(LocalDate.of(2025, 9, 1) to LocalDate.of(2026, 5, 2), StatsRange.ALL.shownDays(d, d, starts, now, zone))
        assertNull(StatsRange.ALL.shownDays(d, d, emptyList(), now, zone))
        assertEquals(
            LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 5),
            StatsRange.CUSTOM.shownDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3), emptyList(), now, zone),
        )
    }

    @Test fun thisYearStartsOnJanuaryFirst() {
        val now: Instant = LocalDate.of(2026, 10, 16).atStartOfDay(zone).plusHours(12).toInstant()
        val today = LocalDate.of(2026, 10, 16)
        val b = StatsRange.THIS_YEAR.bounds(today, today, now, zone)
        assertEquals(day(2026, 1, 1), b.first)
        assertNull(b.second)
    }

    @Test fun last30DaysAndAll() {
        val now = Instant.ofEpochSecond(10_000_000)
        val today = LocalDate.of(2026, 10, 16)
        assertEquals(10_000_000.0 - 30 * 86_400, StatsRange.LAST_30_DAYS.bounds(today, today, now, zone).first)
        assertNull(StatsRange.LAST_30_DAYS.bounds(today, today, now, zone).second)
        assertEquals(null to null, StatsRange.ALL.bounds(today, today, now, zone))
    }

    /** 이전 앱 버전의 요약 디코더(통계 값 없음)가 새 요약(운동 값 zones 포함)을 읽는다 */
    @Test fun oldSummaryDecoderReadsNewSummary() {
        val route = (0 until 20).map { RoutePoint(it.toDouble(), 37 + it * 0.0001, 127.0, 3.0, 5.0) }
        val record = makeRecord(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0))
        val log = WorkoutLog(
            matchID = record.id,
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(100.0, 300.0)),
            route = route,
        )
        val summary = MatchSummary.of(MatchBundle(record, log))
        // 옛 디코더는 id·homeName·homeScore만 읽는다: 새 요약의 JSON에 그 키가 그대로 있고 새 키(zones)는 더해졌을 뿐이다
        val json = summary.toJson()
        assertEquals(record.id.swiftString, json["id"]!!.jsonPrimitive.content)
        assertEquals(summary.homeName, json["homeName"]!!.jsonPrimitive.content)
        assertEquals(summary.homeScore.toString(), json["homeScore"]!!.jsonPrimitive.content)
        assertTrue(json.containsKey("zones"))
        assertEquals(summary, MatchSummary.fromJson(json))
    }
}
