package com.aidenstudio.whistlepro.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 시즌 통계 기간 (화면 이름은 앱에서). 첫 킥오프 시각 기준 [from, to) */
enum class StatsRange {
    THIS_YEAR, LAST_30_DAYS, ALL, CUSTOM;

    fun bounds(
        customFrom: LocalDate,
        customTo: LocalDate,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Double?, Double?> = when (this) {
        // LocalDate.ofInstant는 Android 14(API 34)부터라 Android 12·13에서 NoSuchMethodError로 튕겼다 → atZone 경유 (API 26)
        THIS_YEAR -> now.atZone(zone).toLocalDate().withDayOfYear(1).epoch(zone) to null
        LAST_30_DAYS -> (now.epochSecond - 30 * 86_400L + now.nano / 1e9) to null
        ALL -> null to null
        // 끝 날짜는 그날 하루 전체를 포함한다. 끝이 시작보다 앞이면 시작 날 하루로 본다 (M7a 리뷰)
        CUSTOM -> {
            val end = if (customTo.isBefore(customFrom)) customFrom else customTo
            customFrom.epoch(zone) to end.plusDays(1).epoch(zone)
        }
    }

    /**
     * 화면에 적는 기간의 첫날·끝날 (2026-11-03 critique: 지금 보는 기간이 언제부터 언제까지인지 보이지 않았다).
     * 올해·30일은 오늘까지, 전체는 첫 경기 날~마지막 경기 날(경기가 없으면 null), 직접은 고른 두 날(끝이 앞이면 시작 날 하루)
     */
    fun shownDays(
        customFrom: LocalDate,
        customTo: LocalDate,
        matchStarts: List<Double>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<LocalDate, LocalDate>? {
        val today = now.atZone(zone).toLocalDate()
        return when (this) {
            THIS_YEAR -> today.withDayOfYear(1) to today
            LAST_30_DAYS -> now.minusSeconds(30 * 86_400L).atZone(zone).toLocalDate() to today
            ALL -> {
                val first = matchStarts.minOrNull() ?: return null
                val last = matchStarts.max()
                first.day(zone) to last.day(zone)
            }
            CUSTOM -> customFrom to if (customTo.isBefore(customFrom)) customFrom else customTo
        }
    }

    private fun Double.day(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(Math.round(this * 1000)).atZone(zone).toLocalDate()

    private fun LocalDate.epoch(zone: ZoneId): Double = atStartOfDay(zone).toEpochSecond().toDouble()
}
