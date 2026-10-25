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

    private fun LocalDate.epoch(zone: ZoneId): Double = atStartOfDay(zone).toEpochSecond().toDouble()
}
