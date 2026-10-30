package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun point(t: Double, kmh: Double, accuracy: Double = 5.0) = RoutePoint(t, 0.0, 0.0, kmh / 3.6, accuracy)

private val halves = listOf(
    CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
)

class WorkoutAnalysisTests {
    /** 혼자 300m 튄 점(앞·뒤 모두와 12 m/s 초과)은 빼고, 꾸준히 움직인 점과 진짜 빠른 이동은 남긴다 */
    @Test fun usableRouteDropsLoneSpike() {
        fun at(t: Double, east: Double) = RoutePoint(t, 37.5, 127.0 + east / (111_320.0 * kotlin.math.cos(37.5 * Math.PI / 180)), -1.0, 5.0)
        val route = listOf(at(0.0, 0.0), at(1.0, 3.0), at(2.0, 300.0), at(3.0, 6.0), at(4.0, 9.0))
        val usable = WorkoutAnalysis.usableRoute(route)
        assertEquals(listOf(0.0, 1.0, 3.0, 4.0), usable.map { it.t })
        // 튄 점이 빠지니 거리도 9m 남짓 (튄 점을 넣으면 600m 가까이)
        val zones = WorkoutAnalysis.speedZones(route)!!
        assert(zones.walking + zones.jogging + zones.running + zones.sprinting < 20)
    }

    @Test fun sprintCountsSeparateRuns() {
        val route = listOf(point(0.0, 10.0), point(1.0, 22.0), point(2.0, 23.0), point(3.0, 24.0), point(4.0, 10.0),
            point(5.0, 25.0), point(6.0, 26.0), point(7.0, 27.0), point(8.0, 10.0))
        assertEquals(2, WorkoutAnalysis.sprintCount(route))
    }

    @Test fun shortBurstIsNotASprint() {
        assertEquals(0, WorkoutAnalysis.sprintCount(listOf(point(0.0, 10.0), point(1.0, 30.0), point(2.0, 10.0))))
    }

    @Test fun inaccuratePointsAreIgnored() {
        val route = listOf(point(0.0, 10.0), point(1.0, 25.0, 50.0), point(2.0, 25.0, 50.0), point(3.0, 10.0))
        assertEquals(0, WorkoutAnalysis.sprintCount(route))
    }

    @Test fun longGapSplitsRuns() {
        val route = listOf(point(0.0, 25.0), point(1.0, 25.0), point(2.0, 25.0), point(7.0, 25.0), point(8.0, 25.0), point(9.0, 25.0))
        assertEquals(2, WorkoutAnalysis.sprintCount(route))
    }

    @Test fun perPeriodSplitsDistanceEnergyAndHeartRate() {
        val log = WorkoutLog(
            UUID.randomUUID(),
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2700.0, 5000.0), WorkoutSample(3600.0, 5200.0), WorkoutSample(6300.0, 10200.0)),
            energy = listOf(WorkoutSample(2700.0, 300.0), WorkoutSample(6300.0, 650.0)),
            heartRate = listOf(WorkoutSample(100.0, 120.0), WorkoutSample(200.0, 160.0), WorkoutSample(4000.0, 150.0)),
        )
        val stats = WorkoutAnalysis.perPeriod(log, makeState(halves), 7000.0)
        assertEquals(listOf("1H", "2H"), stats.map { it.label })
        assertEquals(listOf(5000.0, 5000.0), stats.map { it.distance })
        assertEquals(listOf(300.0, 350.0), stats.map { it.energy })
        assertEquals(140L, stats[0].averageHeartRate)
        assertEquals(160L, stats[0].maxHeartRate)
        assertEquals(150L, stats[1].averageHeartRate)
    }

    /** Swift .toNearestOrAwayFromZero와 같게: floor(v + 0.5)는 0.49999999999999994를 1로 올린다 */
    @Test fun heartRateRoundingMatchesSwiftAtBoundary() {
        fun max(value: Double) = WorkoutAnalysis.perPeriod(
            WorkoutLog(UUID.randomUUID(), heartRate = listOf(WorkoutSample(100.0, value))), makeState(halves), 7000.0,
        )[0].maxHeartRate
        assertEquals(0L, max(0.49999999999999994))
        assertEquals(0L, max(-0.49999999999999994))
        assertEquals(1L, max(0.5))
        assertEquals(-1L, max(-0.5))
        assertEquals(3L, max(2.5))
        assertEquals(-3L, max(-2.5))
    }

    @Test fun overallExcludesHalfTime() {
        val log = WorkoutLog(
            UUID.randomUUID(),
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2700.0, 5000.0), WorkoutSample(3600.0, 5200.0), WorkoutSample(6300.0, 10200.0)),
            heartRate = listOf(WorkoutSample(100.0, 120.0), WorkoutSample(3000.0, 90.0), WorkoutSample(4000.0, 150.0)),
            route = listOf(point(100.0, 25.0), point(101.0, 25.0), point(102.0, 25.0), point(3000.0, 25.0), point(3001.0, 25.0), point(3002.0, 25.0)),
        )
        val overall = WorkoutAnalysis.overall(log, makeState(halves), 7000.0)
        assertNull(overall.label)
        assertEquals(10000.0, overall.distance)
        assertEquals(135L, overall.averageHeartRate)
        assertEquals(150L, overall.maxHeartRate)
        assertEquals(1, overall.sprints)
    }

    @Test fun runningPeriodUsesNow() {
        val log = WorkoutLog(UUID.randomUUID(), distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(600.0, 1200.0)))
        val stats = WorkoutAnalysis.perPeriod(log, makeState(listOf(CommandKind.StartPeriod to 0.0)), 650.0)
        assertEquals(listOf(1200.0), stats.map { it.distance })
    }

    @Test fun noPeriodsGivesZeroStats() {
        val overall = WorkoutAnalysis.overall(WorkoutLog(UUID.randomUUID()), makeState(emptyList()), 0.0)
        assertEquals(WorkoutStats(null, null, null, 0.0, 0.0, 0), overall)
    }
}
