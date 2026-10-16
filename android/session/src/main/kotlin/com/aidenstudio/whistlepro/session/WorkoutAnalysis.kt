package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchState
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

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
    /** 속도 구간별 거리 (GPS 경로가 없으면 null) */
    val zones: SpeedZones? = null,
)

/** 속도 구간별 이동 거리 (m) */
data class SpeedZones(
    /** 7km/h 미만 */
    val walking: Double = 0.0,
    /** 7~13km/h */
    val jogging: Double = 0.0,
    /** 13~20km/h */
    val running: Double = 0.0,
    /** 20km/h 이상 (스프린트 기준과 같다) */
    val sprinting: Double = 0.0,
) {
    val total: Double get() = walking + jogging + running + sprinting

    /** 비율은 그대로 두고 합이 total이 되게 (GPS 거리와 워치 거리 차이를 맞춘다) */
    fun scaled(to: Double): SpeedZones {
        if (!(total > 0 && to > 0)) return this
        val factor = to / total
        return SpeedZones(walking * factor, jogging * factor, running * factor, sprinting * factor)
    }

    operator fun plus(b: SpeedZones) =
        SpeedZones(walking + b.walking, jogging + b.jogging, running + b.running, sprinting + b.sprinting)
}

object WorkoutAnalysis {
    /** 스프린트 기준 속도 20km/h (m/s) */
    const val SPRINT_SPEED = 20.0 / 3.6
    /** 이보다 부정확한 GPS 지점은 버린다 (m) */
    const val MAXIMUM_ACCURACY = 20.0
    /** 이보다 길게 지점이 비면 구간을 끊는다 (초) */
    const val MAXIMUM_GAP = 3.0
    /** 이보다 짧은 고속 구간은 GPS 튐으로 보고 세지 않는다 (초, 1Hz에서 3지점) */
    const val MINIMUM_SPRINT_DURATION = 2.0
    /** 조깅 시작 속도 7km/h (m/s) */
    const val JOGGING_SPEED = 7.0 / 3.6
    /** 달리기 시작 속도 13km/h (m/s) */
    const val RUNNING_SPEED = 13.0 / 3.6

    /**
     * GPS 경로의 속도 구간별 거리. 연속한 두 지점(정확도 20m 이하, 간격 3초 이하) 사이 거리를
     * 두 지점 평균 속도의 구간에 더한다. 속도를 모르면 거리÷시간을 쓴다. 쓸 구간이 없으면 null.
     */
    fun speedZones(route: List<RoutePoint>): SpeedZones? {
        var walking = 0.0
        var jogging = 0.0
        var running = 0.0
        var sprinting = 0.0
        var found = false
        var previous: RoutePoint? = null
        for (point in route) {
            if (!(point.accuracy >= 0 && point.accuracy <= MAXIMUM_ACCURACY)) continue
            val prev = previous
            previous = point
            if (prev == null) continue
            val dt = point.t - prev.t
            if (!(dt > 0 && dt <= MAXIMUM_GAP)) continue
            val meters = distance(prev, point)
            if (!meters.isFinite()) continue
            val speed = if (prev.speed >= 0 && point.speed >= 0) (prev.speed + point.speed) / 2 else meters / dt
            found = true
            when {
                speed < JOGGING_SPEED -> walking += meters
                speed < RUNNING_SPEED -> jogging += meters
                speed < SPRINT_SPEED -> running += meters
                else -> sprinting += meters
            }
        }
        return if (found) SpeedZones(walking, jogging, running, sprinting) else null
    }

    /** 두 지점 사이 거리(m). 경기장 크기라 평면 근사로 충분하다. */
    internal fun distance(a: RoutePoint, b: RoutePoint): Double {
        val metersPerDegree = 111_320.0
        val north = (b.latitude - a.latitude) * metersPerDegree
        val east = (b.longitude - a.longitude) * metersPerDegree * cos((a.latitude + b.latitude) / 2 * PI / 180)
        return sqrt(north * north + east * east)
    }

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
        val distances = ranges.map { delta(log.distance, log.restarts, it.first, it.second) }
        val distance = distances.sum()
        // Swift와 같다: 비율은 GPS로, 양은 그 기간의 워치 거리로. 워치 거리 0인 기간은 빼고, 거리 샘플이 아예 없으면 GPS 그대로
        val gpsZones = ranges.zip(distances).mapNotNull { (range, watchDistance) ->
            if (log.distance.isNotEmpty() && watchDistance <= 0) null
            else speedZones(log.route.filter { it.t >= range.first && it.t <= range.second })?.scaled(to = watchDistance)
        }
        val zones = if (gpsZones.isEmpty()) null else gpsZones.fold(SpeedZones()) { a, b -> a + b }
        return WorkoutStats(
            label = label,
            averageHeartRate = if (heartRates.isEmpty()) null else roundedSeconds(heartRates.sum() / heartRates.size),
            maxHeartRate = heartRates.maxOrNull()?.let(::roundedSeconds),
            distance = distance,
            energy = ranges.sumOf { delta(log.energy, log.restarts, it.first, it.second) },
            sprints = ranges.sumOf { range -> sprintCount(log.route.filter { it.t >= range.first && it.t <= range.second }) },
            zones = zones,
        )
    }

    /**
     * Swift wholeSeconds(_, rounding: .toNearestOrAwayFromZero)와 같다: 무한대·NaN은 0, ±1조로 자른 뒤 반올림(0에서 먼 쪽).
     * floor(v + 0.5)는 덧셈에서 반올림 오차가 나 0.49999999999999994를 1로 올리므로, 정수부를 뗀 나머지로 비교한다.
     */
    private fun roundedSeconds(value: Double): Long {
        if (!value.isFinite()) return 0
        val v = value.coerceIn(-1e12, 1e12)
        return if (v >= 0) {
            val f = floor(v)
            if (v - f >= 0.5) f + 1 else f
        } else {
            val c = ceil(v)
            if (c - v >= 0.5) c - 1 else c
        }.toLong()
    }

    /** 누적값 샘플에서 구간 동안 늘어난 양 (Swift와 같다). 재시작 시각을 지나면 0부터 다시, 그 밖에 값이 잠깐 줄면 늘어난 양 없음 */
    private fun delta(samples: List<WorkoutSample>, restarts: List<Double>?, start: Double, end: Double): Double {
        if (restarts == null) return legacyDelta(samples, start, end)
        var base = 0.0
        var previousT = Double.NEGATIVE_INFINITY
        var total = 0.0
        for (sample in samples) {
            if (restarts.any { it > previousT && it <= sample.t }) base = 0.0
            val increment = maxOf(0.0, sample.value - base)
            if (sample.t > start && sample.t <= end) total += increment
            base = maxOf(base, sample.value)
            previousT = sample.t
        }
        return total
    }

    /** 예전 기록(재시작 표시 없음): 값이 줄면 운동 세션이 다시 시작돼 0부터 쌓인 것으로 본다 */
    private fun legacyDelta(samples: List<WorkoutSample>, start: Double, end: Double): Double {
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
