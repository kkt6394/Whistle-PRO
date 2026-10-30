package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 리플레이 영상 한 프레임에 그릴 내용. */
data class ReplayFrame(
    /** 경기 시각(Unix epoch 초) */
    val t: Double,
    /** 기간 번호 (0부터) */
    val period: Int,
    /** null이면 그 순간 GPS가 없다 (점·꼬리 숨김) */
    val position: GeoPoint?,
    /** 점 뒤에 남는 꼬리 (최근 5분, 같은 기간 안) */
    val tail: List<GeoPoint>,
    /** `points` 중 `t` 이하 개수 (렌더러는 이전 프레임 값부터 이 값까지 새로 칠한다) */
    val heatCount: Int,
    /** 점·꼬리 불투명도 0…1 (기간 경계 가까이서 줄어든다) */
    val fade: Double,
    /** m */
    val distance: Double,
    /** 경기 기간 누적 초 */
    val elapsed: Double,
    val sprints: Int,
    /** WorkoutStats.averageHeartRate와 같은 자료형 */
    val averageHeartRate: Long?,
)

/** 경기 기간만 이어 붙인 15초 영상의 시간축. 하프타임은 건너뛰고 승부차기는 기간이 아니라 들어가지 않는다. */
class ReplayTimeline private constructor(
    private val log: WorkoutLog,
    private val periods: List<Period>,
    /** 기간 중 + 정확도 유효 지점, 시간순 */
    val points: List<RoutePoint>,
) {
    private class Period(val start: Double, val end: Double) {
        val length get() = end - start
    }

    /** 움직이는 마지막 프레임 번호 (이후는 붙잡기) */
    private val lastMovingFrame = ((DURATION - HOLD) * FPS).toInt() - 1 // 404
    val frameCount = (DURATION * FPS).toInt() // 450
    private val totalLength = periods.sumOf { it.length }
    private val snapshots: List<WorkoutStats>
    private val finalStats: WorkoutStats

    init {
        finalStats = statsUpTo(locate(lastMovingFrame).t)
        snapshots = (0 until lastMovingFrame step STATS_STEP).map { statsUpTo(locate(it).t) }
    }

    /** 프레임의 경기 시각·기간 번호·누적 경기 초 */
    private class Located(val t: Double, val period: Int, val elapsed: Double)

    private fun locate(frame: Int): Located {
        val index = frame.coerceIn(0, lastMovingFrame)
        val elapsed = index.toDouble() / lastMovingFrame * totalLength
        var before = 0.0
        for ((i, period) in periods.withIndex()) {
            // 경계는 앞 기간 끝으로 본다
            if (elapsed <= before + period.length || i == periods.size - 1) {
                return Located(min(period.start + (elapsed - before), period.end), i, elapsed)
            }
            before += period.length
        }
        return Located(periods[0].start, 0, 0.0)
    }

    /** 이미 시작한 기간마다 [시작, min(끝, t)] 구간의 값 (구역 계산 없음) */
    private fun statsUpTo(t: Double): WorkoutStats =
        WorkoutAnalysis.stats(log, periods.filter { it.start <= t }.map { it.start to min(it.end, t) })

    fun frame(index: Int): ReplayFrame {
        val at = locate(index)
        val stats = if (index >= lastMovingFrame) finalStats else snapshots[index / STATS_STEP]
        val (position, tail) = route(at.t, periods[at.period])
        return ReplayFrame(
            t = at.t, period = at.period, position = position, tail = if (position == null) emptyList() else tail,
            heatCount = count(at.t), fade = fade(index), distance = stats.distance,
            elapsed = at.elapsed, sprints = stats.sprints, averageHeartRate = stats.averageHeartRate,
        )
    }

    /** `t` 이하 첫 위치 (points는 시간순) */
    private fun count(t: Double): Int {
        var low = 0
        var high = points.size
        while (low < high) {
            val mid = (low + high) / 2
            if (points[mid].t <= t) low = mid + 1 else high = mid
        }
        return low
    }

    private fun route(t: Double, period: Period): Pair<GeoPoint?, List<GeoPoint>> {
        val end = count(t)
        // 같은 기간 안 점의 시작 위치
        var start = end
        while (start > 0 && points[start - 1].t >= period.start) start--
        if (end <= start) return null to emptyList()
        val previous = points[end - 1]
        val tail = points.subList(start, end).filter { it.t >= t - TAIL_SECONDS }.map { GeoPoint(it.latitude, it.longitude) }
        val here = GeoPoint(previous.latitude, previous.longitude)
        if (previous.t == t) return here to tail
        // 뒤 점: 같은 기간 안에서 t 다음 점
        if (end < points.size && points[end].t <= period.end) {
            val next = points[end]
            val gap = next.t - previous.t
            if (gap > WorkoutAnalysis.MAXIMUM_GAP) return null to emptyList()
            val f = (t - previous.t) / gap
            return GeoPoint(
                previous.latitude + (next.latitude - previous.latitude) * f,
                previous.longitude + (next.longitude - previous.longitude) * f,
            ) to tail
        }
        // 기간의 마지막 점 뒤: 가까우면 마지막 점에 머문다
        return if (t - previous.t > WorkoutAnalysis.MAXIMUM_GAP) null to emptyList() else here to tail
    }

    /** 기간 경계(첫 시작·마지막 끝 제외)까지의 영상 시간 거리 / FADE_SECONDS, 1로 자른다. */
    private fun fade(index: Int): Double {
        val videoTime = min(index, lastMovingFrame).toDouble() / FPS
        val videoLength = lastMovingFrame.toDouble() / FPS
        var before = 0.0
        var nearest = Double.POSITIVE_INFINITY
        for (period in periods.dropLast(1)) {
            before += period.length
            nearest = min(nearest, abs(videoTime - before / totalLength * videoLength))
        }
        return min(1.0, nearest / FADE_SECONDS)
    }

    companion object {
        const val FPS = 30
        const val DURATION = 15.0
        /** 끝 장면을 붙잡아 두는 시간 (초) */
        const val HOLD = 1.5
        const val TAIL_SECONDS = 300.0
        const val FADE_SECONDS = 0.25
        const val MINIMUM_POINTS = 10
        /** 영상 0.5초마다 통계를 미리 구한다 */
        private const val STATS_STEP = 15

        fun make(log: WorkoutLog, state: MatchState, now: Double): ReplayTimeline? {
            val periods = state.periods.map { Period(it.startedAt, it.endedAt ?: now) }.filter { it.length > 0 }
            if (periods.isEmpty()) return null
            val points = log.route
                .filter { p ->
                    p.accuracy >= 0 && p.accuracy <= WorkoutAnalysis.MAXIMUM_ACCURACY &&
                        periods.any { p.t >= it.start && p.t <= it.end }
                }
                .sortedBy { it.t }
            if (points.size < MINIMUM_POINTS) return null
            return ReplayTimeline(log, periods, points)
        }

        /** 크기는 `max(길이, 100)`×`max(폭, 64)` m. 심판 이동 범위는 실제 경기장보다 작아서 그대로 쓰면 작게 그려진다. */
        fun drawnPitch(points: List<GeoPoint>): Pitch? {
            val estimate = PitchEstimate.of(points) ?: return null
            return Pitch(estimate.center, max(estimate.length, 100.0), max(estimate.width, 64.0), estimate.bearing)
        }
    }
}
