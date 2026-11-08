package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.RuleSet
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val METERS_PER_DEGREE = 111_320.0

/** 전반 0~2700, 하프타임, 후반 3600~6300 */
private val regularCommands = listOf(
    CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
    CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
)

/** 기간 안에서 2초 간격 경로(오가며 달리고 가끔 스프린트), 심박 1초 간격, 거리 누적 샘플 */
private fun makeLog(periods: List<Pair<Double, Double>>, routePeriods: List<Int>? = null): WorkoutLog {
    val route = mutableListOf<RoutePoint>()
    val heartRate = mutableListOf<WorkoutSample>()
    val distance = mutableListOf<WorkoutSample>()
    var total = 0.0
    for ((index, period) in periods.withIndex()) {
        var t = period.first
        while (t <= period.second) {
            val north = 30 * sin(t / 40)
            val east = 20 * cos(t / 55)
            if (routePeriods == null || index in routePeriods) {
                val sprinting = (t / 10).toInt() % 9 == 0
                route += RoutePoint(
                    t, 37.5 + north / METERS_PER_DEGREE,
                    127.0 + east / (METERS_PER_DEGREE * cos(37.5 * Math.PI / 180)),
                    if (sprinting) 6.5 else 3.0, 5.0,
                )
            }
            t += 2
        }
        var s = period.first
        while (s <= period.second) {
            heartRate += WorkoutSample(s, 120 + (s / 30) % 40)
            if (s.toInt() % 5 == 0) {
                total += 10
                distance += WorkoutSample(s, total)
            }
            s += 1
        }
        total += 5
    }
    return WorkoutLog(UUID.randomUUID(), heartRate = heartRate, distance = distance, route = route)
}

private fun regular(): Pair<WorkoutLog, MatchState> =
    makeLog(listOf(0.0 to 2700.0, 3600.0 to 6300.0)) to makeState(regularCommands)

private fun timeline(log: WorkoutLog, state: MatchState, now: Double = 7000.0): ReplayTimeline =
    assertNotNull(ReplayTimeline.make(log, state, now))

class ReplayTimelineTests {
    @Test fun alwaysFourHundredFiftyFrames() {
        val (log, state) = regular()
        assertEquals(450, timeline(log, state).frameCount)
        val shortState = makeState(
            RuleSet(periodLength = 600),
            listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 600.0, CommandKind.StartPeriod to 700.0, CommandKind.EndPeriod to 1300.0),
        )
        assertEquals(450, timeline(makeLog(listOf(0.0 to 600.0, 700.0 to 1300.0)), shortState, 2000.0).frameCount)
        val extraState = makeState(
            RuleSet(extraTime = true),
            listOf(
                CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
                CommandKind.StartPeriod to 6600.0, CommandKind.EndPeriod to 7500.0, CommandKind.StartPeriod to 7600.0, CommandKind.EndPeriod to 8500.0,
            ),
        )
        assertEquals(4, extraState.periods.size)
        val extraLog = makeLog(listOf(0.0 to 2700.0, 3600.0 to 6300.0, 6600.0 to 7500.0, 7600.0 to 8500.0))
        assertEquals(450, timeline(extraLog, extraState, 9000.0).frameCount)
    }

    @Test fun skipsHalftime() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        for (i in 0 until timeline.frameCount) {
            val t = timeline.frame(i).t
            assertTrue(!(t > 2700 && t < 3600))
        }
    }

    @Test fun firstFrameStartsAtKickoff() {
        val (log, state) = regular()
        val frame = timeline(log, state).frame(0)
        assertEquals(0.0, frame.t)
        assertEquals(0.0, frame.distance)
        assertEquals(0.0, frame.elapsed)
    }

    @Test fun lastFrameMatchesOverall() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        val overall = WorkoutAnalysis.overall(log, state, 7000.0)
        val frame = timeline.frame(449)
        assertEquals(overall.distance, frame.distance)
        assertEquals(overall.sprints, frame.sprints)
        assertEquals(overall.averageHeartRate, frame.averageHeartRate)
        assertTrue(overall.sprints > 0)
        assertEquals(timeline.points.size, frame.heatCount)
        assertEquals(5400.0, frame.elapsed)
    }

    @Test fun holdFramesRepeatLast() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        val last = timeline.frame(449).copy(fade = 0.0)
        for (i in 405..449) assertEquals(last, timeline.frame(i).copy(fade = 0.0))
    }

    /** 끝 붙잡기 처음 0.5초(15프레임) 동안 점·꼬리가 사라지고, 그 뒤는 히트맵만 */
    @Test fun holdFadesOutDot() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        assertEquals(1.0, timeline.frame(404).fade)
        assertTrue(timeline.frame(412).fade > 0 && timeline.frame(412).fade < 1)
        assertEquals(0.0, timeline.frame(419).fade)
        assertEquals(0.0, timeline.frame(449).fade)
    }

    /** 점수는 골 프레임부터 오르고, 골 표시는 1.5초(45프레임) 동안 뜬다. 자책골은 상대 득점 (아이폰과 같은 값) */
    @Test fun scoreAndGoalFlash() {
        val log = makeLog(listOf(0.0 to 2700.0, 3600.0 to 6300.0))
        val state = makeState(
            listOf(
                CommandKind.StartPeriod to 0.0, CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 600.0,
                CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0,
                CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 4, true)) to 5000.0, CommandKind.EndPeriod to 6300.0,
            ),
        )
        val timeline = timeline(log, state)
        assertEquals(TeamPair(0, 0), timeline.frame(44).score)
        assertNull(timeline.frame(44).goal)
        val first = timeline.frame(45)
        assertEquals(TeamPair(1, 0), first.score)
        assertEquals(ReplayGoal(Team.HOME, "11'", 0.0), first.goal)
        assertNotNull(timeline.frame(89).goal)
        assertNull(timeline.frame(90).goal)
        val last = timeline.frame(449)
        assertEquals(TeamPair(1, 1), last.score)
        assertNull(last.goal)
        val own = (0 until 450).first { timeline.frame(it).score.away == 1 }
        assertEquals(Team.AWAY, timeline.frame(own).goal?.team)
    }

    @Test fun periodLabels() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        assertEquals("1H", timeline.frame(0).periodLabel)
        assertEquals("2H", timeline.frame(449).periodLabel)
    }

    @Test fun distanceNeverDecreases() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        var previous = timeline.frame(0)
        for (i in 1 until timeline.frameCount) {
            val frame = timeline.frame(i)
            assertTrue(frame.distance >= previous.distance)
            assertTrue(frame.sprints >= previous.sprints)
            assertTrue(frame.heatCount >= previous.heatCount)
            previous = frame
        }
    }

    @Test fun tailStaysInPeriodAndNinetySeconds() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        val timeOf = timeline.points.associate { GeoPoint(it.latitude, it.longitude) to it.t }
        fun times(tail: List<GeoPoint>) = tail.mapNotNull { timeOf[it] }
        // 후반 첫 프레임: 전반 점이 꼬리에 없다
        val secondHalfStart = (0 until 450).first { timeline.frame(it).period == 1 }
        val first = timeline.frame(secondHalfStart)
        assertTrue(first.tail.isNotEmpty())
        assertTrue(times(first.tail).all { it > 2700 })
        for (i in 0 until 450) {
            val frame = timeline.frame(i)
            assertTrue(times(frame.tail).all { it >= frame.t - 90 })
        }
    }

    @Test fun fadesAtBoundary() {
        val (log, state) = regular()
        val timeline = timeline(log, state)
        val nearest = (0 until 405).minByOrNull { kotlin.math.abs(timeline.frame(it).t - 2700) }!!
        assertTrue(timeline.frame(nearest).fade < 0.2)
        assertEquals(1.0, timeline.frame(100).fade)
        assertEquals(1.0, timeline.frame(0).fade)
    }

    @Test fun gapPeriodHidesDotKeepsCounting() {
        val log = makeLog(listOf(0.0 to 2700.0, 3600.0 to 6300.0), routePeriods = listOf(0))
        val state = makeState(regularCommands)
        val timeline = timeline(log, state)
        var previousDistance = 0.0
        for (i in 0 until timeline.frameCount) {
            val frame = timeline.frame(i)
            if (frame.period == 1) {
                assertNull(frame.position)
                assertTrue(frame.tail.isEmpty())
            }
            assertTrue(frame.distance >= previousDistance)
            previousDistance = frame.distance
        }
        assertEquals(WorkoutAnalysis.overall(log, state, 7000.0).distance, timeline.frame(449).distance)
        assertTrue(timeline.frame(449).distance > timeline.frame(200).distance)
    }

    /** 위치가 수백 m 튀면(휴대폰 위치가 섞인 순간 등) 꼬리는 튄 뒤부터만 잇는다 — 긴 직선을 그리지 않는다 */
    @Test fun tailCutsAtImpossibleJump() {
        val (log, state) = regular()
        // 후반 4000초에 동쪽으로 300m 튄 점 하나
        val jump = RoutePoint(4001.0, 37.5, 127.0 + 300 / (METERS_PER_DEGREE * cos(37.5 * Math.PI / 180)), -1.0, 5.0)
        val route = (log.route + jump).sortedBy { it.t }
        val timeline = timeline(log.copy(route = route), state)
        val jumped = GeoPoint(jump.latitude, jump.longitude)
        for (i in 0 until 450) {
            val frame = timeline.frame(i)
            val at = frame.tail.indexOf(jumped)
            // 튄 점이 꼬리에 있으면 그 앞(튀기 전) 점과 잇지 않는다: 튄 점에서 시작하거나, 튄 점 뒤에서 다시 끊긴다
            if (at > 0) error("프레임 $i: 튄 점 앞의 점과 이어졌다")
        }
    }

    @Test fun openLastPeriodUsesNow() {
        val log = makeLog(listOf(0.0 to 2700.0, 3600.0 to 6000.0))
        val state = makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0))
        assertEquals(6000.0, timeline(log, state, 6000.0).frame(449).t)
    }

    @Test fun nilWithFewPoints() {
        val (log, state) = regular()
        assertNull(ReplayTimeline.make(log.copy(route = log.route.take(9)), state, 7000.0))
    }

    @Test fun drawnPitchHasMinimumSize() {
        // 40×30m 범위를 훑는 점
        val points = (0..20).flatMap { i ->
            listOf(0.0, 30.0).map { e ->
                GeoPoint(37.5 + i * 2.0 / METERS_PER_DEGREE, 127.0 + e / (METERS_PER_DEGREE * cos(37.5 * Math.PI / 180)))
            }
        }
        val drawn = assertNotNull(ReplayTimeline.drawnPitch(points))
        val estimate = assertNotNull(PitchEstimate.of(points))
        assertEquals(100.0, drawn.length)
        assertEquals(64.0, drawn.width)
        assertEquals(estimate.center, drawn.center)
        assertEquals(estimate.bearing, drawn.bearing)
    }
}
