package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchState
import kotlin.math.ceil
import kotlin.math.floor

data class WorkoutStats(
    /** 기간 라벨 (전체 통계면 null) */
    val label: String?,
    val averageHeartRate: Long?,
    val maxHeartRate: Long?,
    /** m */
    val distance: Double,
    /** kcal */
    val energy: Double,
    val sprints: Int,
)

object WorkoutAnalysis {
    /** 스프린트 기준 속도 20km/h (m/s) */
    const val SPRINT_SPEED = 20.0 / 3.6
    /** 이보다 부정확한 GPS 지점은 버린다 (m) */
    const val MAXIMUM_ACCURACY = 20.0
    /** 이보다 길게 지점이 비면 구간을 끊는다 (초) */
    const val MAXIMUM_GAP = 3.0
    /** 이보다 짧은 고속 구간은 GPS 튐으로 보고 세지 않는다 (초, 1Hz에서 3지점) */
    const val MINIMUM_SPRINT_DURATION = 2.0

    /** 20km/h 이상이 2초 이상 이어진 구간 수. route는 시간순이어야 한다. */
    fun sprintCount(route: List<RoutePoint>): Int {
        var count = 0
        var runStart: Double? = null
        var runEnd = 0.0
        var lastTime: Double? = null
        fun closeRun() {
            val start = runStart
            if (start != null && runEnd - start >= MINIMUM_SPRINT_DURATION) count += 1
            runStart = null
        }
        for (point in route) {
            if (!(point.accuracy >= 0 && point.accuracy <= MAXIMUM_ACCURACY)) continue
            val last = lastTime
            if (last != null && point.t - last > MAXIMUM_GAP) closeRun()
            if (point.speed >= SPRINT_SPEED) {
                if (runStart == null) runStart = point.t
                runEnd = point.t
            } else {
                closeRun()
            }
            lastTime = point.t
        }
        closeRun()
        return count
    }

    fun stats(log: WorkoutLog, start: Double, end: Double, label: String?): WorkoutStats =
        stats(log, listOf(start to end), label)

    fun perPeriod(log: WorkoutLog, state: MatchState, now: Double): List<WorkoutStats> = state.periods.map { p ->
        stats(log, p.startedAt, p.endedAt ?: now, p.ref.label(state.rules.periodCount))
    }

    /** 기간 진행 중의 값만 합친다 (휴식 제외). */
    fun overall(log: WorkoutLog, state: MatchState, now: Double): WorkoutStats =
        stats(log, state.periods.map { it.startedAt to (it.endedAt ?: now) }, null)

    private fun stats(log: WorkoutLog, ranges: List<Pair<Double, Double>>, label: String?): WorkoutStats {
        fun inRanges(t: Double) = ranges.any { t >= it.first && t <= it.second }
        val heartRates = log.heartRate.filter { inRanges(it.t) }.map { it.value }
        return WorkoutStats(
            label = label,
            averageHeartRate = if (heartRates.isEmpty()) null else roundedSeconds(heartRates.sum() / heartRates.size),
            maxHeartRate = heartRates.maxOrNull()?.let(::roundedSeconds),
            distance = ranges.sumOf { delta(log.distance, it.first, it.second) },
            energy = ranges.sumOf { delta(log.energy, it.first, it.second) },
            sprints = ranges.sumOf { range -> sprintCount(log.route.filter { it.t >= range.first && it.t <= range.second }) },
        )
    }

    /** Swift wholeSeconds(_, rounding: .toNearestOrAwayFromZero)와 같다: 무한대·NaN은 0, ±1조로 자른 뒤 반올림(0에서 먼 쪽) */
    private fun roundedSeconds(value: Double): Long {
        if (!value.isFinite()) return 0
        val v = value.coerceIn(-1e12, 1e12)
        return (if (v >= 0) floor(v + 0.5) else ceil(v - 0.5)).toLong()
    }

    /** 누적값 샘플에서 구간 동안 늘어난 양. 값이 줄어들면 운동 세션이 다시 시작돼 0부터 쌓인 것으로 본다. */
    private fun delta(samples: List<WorkoutSample>, start: Double, end: Double): Double {
        var previous = 0.0
        var total = 0.0
        for (sample in samples) {
            val increment = if (sample.value >= previous) sample.value - previous else sample.value
            if (sample.t > start && sample.t <= end) total += increment
            previous = sample.value
        }
        return total
    }
}
