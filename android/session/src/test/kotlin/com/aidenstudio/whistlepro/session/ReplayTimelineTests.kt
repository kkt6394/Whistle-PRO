package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
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
        val last = timeline.frame(449)
        for (i in 405..449) assertEquals(last, timeline.frame(i))
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

    @Test fun tailStaysInPeriodAndFiveMinutes() {
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
            assertTrue(times(frame.tail).all { it >= frame.t - 300 })
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
