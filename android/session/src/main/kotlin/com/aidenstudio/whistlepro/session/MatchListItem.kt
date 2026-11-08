package com.aidenstudio.whistlepro.session

import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.util.Locale
import java.util.UUID
import kotlin.math.floor

/** 목록 점수판 카드 아래 칩 (디자인 개편 §5.1): 0이거나 없는 값은 만들지 않는다 */
sealed interface ListChip {
    data class Shootout(val home: Long, val away: Long) : ListChip
    data class Goals(val count: Int) : ListChip
    data class Yellows(val count: Int) : ListChip
    data class Reds(val count: Int) : ListChip
    data class Distance(val meters: Double) : ListChip
}

/** 목록 한 줄에 필요한 값만 (그릴 때 경기 기록을 다시 재생하지 않는다) */
data class MatchListItem(
    val id: UUID,
    val startedAt: Double,
    val homeName: String,
    val awayName: String,
    val homeColor: TeamColor,
    val awayColor: TeamColor,
    val homeColor2: TeamColor?,
    val awayColor2: TeamColor?,
    val homeScore: Int,
    val awayScore: Int,
    val abandoned: Boolean,
    val abandonReason: String?,
    /** "25분 × 4"의 (기간 수, 분). v3 이하 요약은 null */
    val ruleText: Pair<Long, Long>?,
    val chips: List<ListChip>,
) {
    companion object {
        fun of(s: MatchSummary): MatchListItem {
            val outcome = s.outcome
            val count = s.periodCount
            val minutes = s.periodMinutes
            val chips = mutableListOf<ListChip>()
            s.shootout?.let { pk -> if (pk.size == 2) chips += ListChip.Shootout(pk[0].toLong(), pk[1].toLong()) }
            s.goals?.let { if (it > 0) chips += ListChip.Goals(it) }
            s.yellows?.let { if (it > 0) chips += ListChip.Yellows(it) }
            s.reds?.let { if (it > 0) chips += ListChip.Reds(it) }
            s.distance?.let { if (it > 0) chips += ListChip.Distance(it) }
            return MatchListItem(
                id = s.id, startedAt = s.startedAt,
                homeName = s.homeName, awayName = s.awayName,
                homeColor = s.homeColor, awayColor = s.awayColor,
                homeColor2 = s.homeColor2, awayColor2 = s.awayColor2,
                homeScore = s.homeScore, awayScore = s.awayScore,
                abandoned = outcome is ReportOutcome.Abandoned,
                abandonReason = (outcome as? ReportOutcome.Abandoned)?.reason,
                ruleText = if (count != null && minutes != null) count.toLong() to minutes.toLong() else null,
                chips = chips,
            )
        }

        /** 목록 날짜에 연도를 붙일지: 올해(기기 달력) 경기가 아니면 붙인다 ("2025년 12월 3일 (수)") */
        fun showsYear(startedAt: Double, now: Instant, zone: ZoneId): Boolean =
            Instant.ofEpochMilli(floor(startedAt * 1000).toLong()).atZone(zone).year != now.atZone(zone).year
    }
}

/** 목록 맨 위 이번 달 요약 (기기 달력 기준). 평균 규칙은 SeasonStats와 같다 */
data class MonthSummary(val matches: Int, val distance: Double, val cardsPerMatch: Double?) {
    companion object {
        /**
         * 요약 카드의 거리 숫자 (km, 단위 없이). 10km 미만은 소수 한 자리("0.4", "9.8"), 이상은 정수.
         * 정수로만 반올림하면 0.4km가 "0"으로 보였다 (디자인 개편 리뷰)
         */
        fun distanceText(meters: Double, locale: Locale = Locale.getDefault()): String {
            val km = meters / 1000
            val tenths = Math.round(km * 10).toDouble() / 10
            return if (tenths < 10) NumberText.decimal(tenths, 1, locale) else Math.round(km).toString()
        }

        fun of(summaries: List<MatchSummary>, now: Instant, zone: ZoneId): MonthSummary {
            val month = YearMonth.from(now.atZone(zone))
            val from = month.atDay(1).atStartOfDay(zone).toEpochSecond().toDouble()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toEpochSecond().toDouble()
            val stats = SeasonStats.aggregate(summaries, from, to)
            return MonthSummary(stats.all.matches, stats.all.distance, stats.cardsPerMatch)
        }
    }
}
