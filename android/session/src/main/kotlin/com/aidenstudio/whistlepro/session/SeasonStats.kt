package com.aidenstudio.whistlepro.session

/**
 * 기간 안 경기들의 합계·경기당 평균. 경기 요약(MatchSummary)만으로 만든다.
 * 중단된 경기는 경기 수와 합계에는 넣고, 경기당 평균에서는 뺀다 (짧게 끝나 평균을 낮추므로).
 */
data class SeasonStats(
    /** 기간 안 모든 경기 */
    val all: Totals = Totals(),
    /** 완료된 경기만 (경기당 평균용) */
    val completed: Totals = Totals(),
    /** 중단된 경기 (진행 중 경기는 세지 않는다) */
    val abandoned: Int = 0,
) {
    data class Totals(
        val matches: Int = 0,
        val goals: Int = 0,
        val yellows: Int = 0,
        val reds: Int = 0,
        val sinBins: Int = 0,
        val addedSeconds: Int = 0,
        /** 운동 데이터가 있는 경기 수와 그 합 */
        val matchesWithWorkout: Int = 0,
        val distance: Double = 0.0,
        val sprints: Int = 0,
        /** 평균 심박이 있는 경기 수와 그 합 (경기마다 같은 비중) */
        val matchesWithHeartRate: Int = 0,
        val heartRateSum: Int = 0,
        val zones: SpeedZones? = null,
    ) {
        val cards: Int get() = yellows + reds + sinBins

        fun add(s: MatchSummary): Totals {
            // 거리 0(수집 실패 등)은 운동 경기로 세지 않는다: 경기당 이동거리를 낮췄다 (M7a 리뷰)
            val d = s.distance?.takeIf { it > 0 }
            val hasWorkout = d != null
            val hr = s.averageHeartRate
            return copy(
                matches = matches + 1,
                goals = goals + (s.goals ?: 0),
                yellows = yellows + (s.yellows ?: 0),
                reds = reds + (s.reds ?: 0),
                sinBins = sinBins + (s.sinBins ?: 0),
                addedSeconds = addedSeconds + (s.addedSeconds ?: 0),
                matchesWithWorkout = matchesWithWorkout + if (hasWorkout) 1 else 0,
                distance = distance + (d ?: 0.0),
                sprints = if (hasWorkout) sprints + (s.sprints ?: 0) else sprints,
                matchesWithHeartRate = matchesWithHeartRate + if (hr != null) 1 else 0,
                heartRateSum = heartRateSum + (hr ?: 0),
                zones = s.zones?.let { (zones ?: SpeedZones()) + it } ?: zones,
            )
        }
    }

    val goalsPerMatch: Double? get() = per(completed.goals, completed.matches)
    val cardsPerMatch: Double? get() = per(completed.cards, completed.matches)
    val addedSecondsPerMatch: Double? get() = per(completed.addedSeconds, completed.matches)
    val distancePerMatch: Double?
        get() = if (completed.matchesWithWorkout > 0) completed.distance / completed.matchesWithWorkout else null
    val sprintsPerMatch: Double? get() = per(completed.sprints, completed.matchesWithWorkout)
    val averageHeartRate: Int?
        get() = if (completed.matchesWithHeartRate > 0)
            Math.round(completed.heartRateSum.toDouble() / completed.matchesWithHeartRate).toInt() else null

    private fun per(total: Int, count: Int): Double? = if (count > 0) total.toDouble() / count else null

    /**
     * 표 줄 순서. 일시 퇴장은 있을 때만, 거리·스프린트는 운동 기록이 있는 경기가 있을 때만.
     * 추가시간·이동 거리도 합계와 경기당을 둘 다 채운다 (예전에는 한쪽 칸이 비어 있었다)
     */
    val lines: List<StatLine>
        get() {
            val lines = mutableListOf(
                StatLine(StatLine.Kind.GOALS, all.goals.toDouble(), goalsPerMatch),
                StatLine(StatLine.Kind.YELLOWS, all.yellows.toDouble(), perCompleted(completed.yellows)),
                StatLine(StatLine.Kind.REDS, all.reds.toDouble(), perCompleted(completed.reds)),
            )
            if (all.sinBins > 0) {
                lines += StatLine(StatLine.Kind.SIN_BINS, all.sinBins.toDouble(), perCompleted(completed.sinBins))
            }
            lines += StatLine(StatLine.Kind.ADDED_TIME, all.addedSeconds.toDouble(), addedSecondsPerMatch)
            if (all.matchesWithWorkout > 0) {
                lines += StatLine(StatLine.Kind.DISTANCE, all.distance, distancePerMatch)
                lines += StatLine(StatLine.Kind.SPRINTS, all.sprints.toDouble(), sprintsPerMatch)
            }
            return lines
        }

    private fun perCompleted(total: Int): Double? = if (completed.matches > 0) total.toDouble() / completed.matches else null

    companion object {
        /** 첫 킥오프 시각이 from 이상, to 미만인 경기 (null이면 그쪽 경계 없음) */
        fun aggregate(summaries: List<MatchSummary>, from: Double? = null, to: Double? = null): SeasonStats {
            var stats = SeasonStats()
            for (summary in summaries) {
                if (from != null && summary.startedAt < from) continue
                if (to != null && summary.startedAt >= to) continue
                stats = stats.copy(all = stats.all.add(summary))
                stats = when (summary.outcome) {
                    ReportOutcome.Completed -> stats.copy(completed = stats.completed.add(summary))
                    is ReportOutcome.Abandoned -> stats.copy(abandoned = stats.abandoned + 1)
                    ReportOutcome.InProgress -> stats
                }
            }
            return stats
        }
    }
}

/** 시즌 통계 표 한 줄: 이름 · 합계 · 경기당. 값이 없으면 null (화면에 "--") */
data class StatLine(val kind: Kind, val total: Double?, val perMatch: Double?) {
    enum class Kind {
        GOALS, YELLOWS, REDS, SIN_BINS,
        /** 초 */
        ADDED_TIME,
        /** 미터 */
        DISTANCE,
        SPRINTS,
    }
}
