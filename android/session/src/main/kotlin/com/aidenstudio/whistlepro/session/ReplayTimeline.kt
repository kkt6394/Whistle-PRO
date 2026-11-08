package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
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
    /** 점 뒤에 남는 꼬리 (최근 90초, 같은 기간 안) */
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
    /** 그 순간까지의 점수 (정규·연장, 자책골은 상대 득점) */
    val score: TeamPair<Int>,
    /** 방금 들어간 골 (골 뒤 [ReplayTimeline.GOAL_SECONDS] 동안). 없으면 null */
    val goal: ReplayGoal?,
    /** 지금 기간 라벨 (1H·2H, Q1~Q4, ET1·ET2) */
    val periodLabel: String,
)

/** 영상에 잠깐 띄우는 골 표시 */
data class ReplayGoal(
    /** 득점한 팀 (자책골이면 상대 팀) */
    val team: Team,
    /** "78'", "45+2'" */
    val minute: String,
    /** 골 뒤 지난 영상 시간 (초, 0부터) */
    val age: Double,
    /** 골을 기록한 순간의 심판 위치 (그 자리에 표시를 고정한다 — 점을 따라가지 않게, 사용자 11-09). GPS가 없으면 null */
    val position: GeoPoint? = null,
)

/** 경기 기간만 이어 붙인 15초 영상의 시간축. 하프타임은 건너뛰고 승부차기는 기간이 아니라 들어가지 않는다. */
class ReplayTimeline private constructor(
    private val log: WorkoutLog,
    private val periods: List<Period>,
    /** 기간 중 + 정확도 유효 지점, 시간순 */
    val points: List<RoutePoint>,
    private val periodLabels: List<String>,
    goalTimes: List<GoalTime>,
) {
    private class GoalTime(val t: Double, val team: Team, val minute: String)
    private class GoalFrame(val frame: Int, val team: Team, val minute: String, var position: GeoPoint? = null)
    private class Period(val start: Double, val end: Double) {
        val length get() = end - start
    }

    /** 움직이는 마지막 프레임 번호 (이후는 붙잡기) */
    private val lastMovingFrame = ((DURATION - HOLD) * FPS).toInt() - 1 // 404
    val frameCount = (DURATION * FPS).toInt() // 450
    private val totalLength = periods.sumOf { it.length }
    private val snapshots: List<WorkoutStats>
    private val finalStats: WorkoutStats
    /** 골마다 처음 보이는 프레임 번호, 시간순 */
    private val goals: List<GoalFrame>

    init {
        finalStats = statsUpTo(locate(lastMovingFrame).t)
        snapshots = (0 until lastMovingFrame step STATS_STEP).map { statsUpTo(locate(it).t) }
        // 골 시각 → 누적 경기 초 → 프레임. 휴식 중 기록은 앞 기간 끝, 첫 킥오프 전은 0
        goals = goalTimes.map { goal ->
            var elapsed = 0.0
            for (period in periods) {
                if (goal.t < period.start) break
                elapsed += min(goal.t, period.end) - period.start
            }
            val frame = kotlin.math.ceil(elapsed / totalLength * lastMovingFrame).toInt()
            GoalFrame(min(frame, lastMovingFrame), goal.team, goal.minute)
        }.sortedBy { it.frame }
        // 골 프레임의 심판 위치 (기록한 순간의 자리)
        for (goal in goals) {
            val at = locate(goal.frame)
            goal.position = route(at.t, periods[at.period]).first
        }
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
        var home = 0
        var away = 0
        var goal: ReplayGoal? = null
        for (g in goals) {
            if (g.frame > index) break
            if (g.team == Team.HOME) home++ else away++
            val age = (index - g.frame).toDouble() / FPS
            if (age < GOAL_SECONDS) goal = ReplayGoal(g.team, g.minute, age, g.position)
        }
        return ReplayFrame(
            t = at.t, period = at.period, position = position, tail = if (position == null) emptyList() else tail,
            heatCount = count(at.t), fade = fade(index), distance = stats.distance,
            elapsed = at.elapsed, sprints = stats.sprints, averageHeartRate = stats.averageHeartRate,
            score = TeamPair(home, away), goal = goal, periodLabel = periodLabels.getOrElse(at.period) { "" },
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
        val recent = points.subList(start, end).filter { it.t >= t - TAIL_SECONDS }
        // 사람이 낼 수 없는 속도로 튄 곳(위치 출처가 바뀐 순간 등)에서 꼬리를 끊는다: 그 뒤만 잇는다 (긴 직선이 그어졌다 — 2026-10-30)
        val cut = recent.indices.drop(1).lastOrNull { i ->
            val gap = recent[i].t - recent[i - 1].t
            gap > 0 && WorkoutAnalysis.distance(recent[i - 1], recent[i]) / gap > MAXIMUM_TAIL_SPEED
        } ?: 0
        val tail = recent.drop(cut).map { GeoPoint(it.latitude, it.longitude) }
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
        if (index > lastMovingFrame) {
            val held = (index - lastMovingFrame).toDouble() / FPS
            return max(0.0, 1 - held / HOLD_FADE_SECONDS)
        }
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
        /** 꼬리 길이 (경기 초). 5분은 영상 0.8초에 몰려 GPS 잔떨림이 흰 덩어리로 보였다 (critique 11-08) */
        const val TAIL_SECONDS = 90.0
        const val FADE_SECONDS = 0.25
        /** 끝 붙잡기 처음 이만큼(초) 점·꼬리가 사라진다: 마지막 장면은 히트맵 한 장 */
        const val HOLD_FADE_SECONDS = 0.5
        /** 골 표시를 띄우는 영상 시간 (초) */
        const val GOAL_SECONDS = 1.5
        const val MINIMUM_POINTS = 10
        /** 꼬리를 이을 수 있는 가장 빠른 이동 (m/s): 전력 질주(약 10 m/s)보다 조금 빠르게 */
        const val MAXIMUM_TAIL_SPEED = WorkoutAnalysis.MAXIMUM_SPEED
        /** 영상 0.5초마다 통계를 미리 구한다 */
        private const val STATS_STEP = 15

        fun make(log: WorkoutLog, state: MatchState, now: Double): ReplayTimeline? {
            val periods = state.periods.map { Period(it.startedAt, it.endedAt ?: now) }.filter { it.length > 0 }
            if (periods.isEmpty()) return null
            val points = WorkoutAnalysis.usableRoute(log.route).filter { p -> periods.any { p.t >= it.start && p.t <= it.end } }
            if (points.size < MINIMUM_POINTS) return null
            val labels = state.periods.filter { (it.endedAt ?: now) - it.startedAt > 0 }.map { it.ref.label(state.rules.periodCount) }
            val goals = state.events.mapNotNull { event ->
                val p = event.payload as? EventPayload.Goal ?: return@mapNotNull null
                GoalTime(event.timestamp, if (p.ownGoal) p.team.opponent else p.team, minuteText(state.minute(event.time)))
            }
            return ReplayTimeline(log, periods, points, labels, goals)
        }

        /** "23'", "45+2'", 기간 밖이면 기간 라벨 */
        fun minuteText(minute: EventMinute): String = when (minute) {
            is EventMinute.Regular -> "${minute.minute}'"
            is EventMinute.Added -> "${minute.base}+${minute.extra}'"
            is EventMinute.AfterPeriod -> minute.label
            EventMinute.Shootout -> "PK"
        }

        /** 크기는 `max(길이, 100)`×`max(폭, 64)` m. 심판 이동 범위는 실제 경기장보다 작아서 그대로 쓰면 작게 그려진다. */
        fun drawnPitch(points: List<GeoPoint>): Pitch? {
            val estimate = PitchEstimate.of(points) ?: return null
            return Pitch(estimate.center, max(estimate.length, 100.0), max(estimate.width, 64.0), estimate.bearing)
        }
    }
}
