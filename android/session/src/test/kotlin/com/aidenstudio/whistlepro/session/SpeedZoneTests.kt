package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 북쪽으로 meters만큼 간 지점 (위도 0 근처라 1도 ≈ 111,320m) */
private fun point(t: Double, north: Double, kmh: Double, accuracy: Double = 5.0) =
    RoutePoint(t, north / 111_320, 0.0, if (kmh < 0) -1.0 else kmh / 3.6, accuracy)

private val halves = listOf(
    CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
)

private fun near(expected: Double, actual: Double?) = assertTrue(actual != null && abs(actual - expected) < 0.01, "$expected ≠ $actual")

class SpeedZoneTests {
    @Test fun segmentsGoToAverageSpeedZone() {
        val route = listOf(point(0.0, 0.0, 5.0), point(1.0, 1.0, 5.0), // 걷기 1m
            point(2.0, 4.0, 10.0), point(3.0, 7.0, 10.0), // 5→10 평균 7.5 조깅 3m, 조깅 3m
            point(4.0, 11.0, 16.0), point(5.0, 15.0, 16.0), // 13 달리기 4m, 달리기 4m
            point(6.0, 21.0, 24.0)) // 20 스프린트 6m
        val zones = assertNotNull(WorkoutAnalysis.speedZones(route))
        near(1.0, zones.walking)
        near(6.0, zones.jogging)
        near(8.0, zones.running)
        near(6.0, zones.sprinting)
    }

    @Test fun unknownSpeedUsesDistanceOverTime() {
        val zones = assertNotNull(WorkoutAnalysis.speedZones(listOf(point(0.0, 0.0, -1.0), point(2.0, 12.0, 5.0))))
        // 12m / 2초 = 6m/s = 21.6km/h → 스프린트
        near(12.0, zones.sprinting)
    }

    @Test fun gapsAndInaccuratePointsAreSkipped() {
        val route = listOf(point(0.0, 0.0, 5.0), point(5.0, 5.0, 5.0),
            point(6.0, 6.0, 5.0, 30.0), point(7.0, 7.0, 5.0, -1.0))
        assertNull(WorkoutAnalysis.speedZones(route))
    }

    @Test fun emptyRouteHasNoZones() {
        assertNull(WorkoutAnalysis.speedZones(emptyList()))
        val log = WorkoutLog(UUID.randomUUID(), distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2700.0, 5000.0)))
        assertNull(WorkoutAnalysis.overall(log, makeState(halves), 7000.0).zones)
    }

    /** GPS가 전반에만 있으면 전반 거리에만 맞춘다 (워치 거리가 GPS보다 크면 워치 거리) */
    @Test fun zonesScaleOnlyToPeriodsWithGPS() {
        val log = WorkoutLog(
            UUID.randomUUID(),
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2700.0, 3000.0), WorkoutSample(3600.0, 3000.0), WorkoutSample(6300.0, 7000.0)),
            route = listOf(point(0.0, 0.0, 5.0), point(1.0, 10.0, 5.0)),
        )
        near(3000.0, assertNotNull(WorkoutAnalysis.overall(log, makeState(halves), 7000.0).zones).total)
    }

    /** 삼성 워치: GPS를 끈 축구 운동은 워치 거리가 거의 0이다. GPS 거리가 더 크면 그것을 쓴다 */
    @Test fun zeroWatchDistanceUsesGPS() {
        val log = WorkoutLog(
            UUID.randomUUID(),
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2700.0, 0.0)),
            route = listOf(point(0.0, 0.0, 5.0), point(1.0, 10.0, 5.0)),
        )
        val overall = WorkoutAnalysis.overall(log, makeState(halves), 7000.0)
        near(10.0, overall.distance)
        near(10.0, assertNotNull(overall.zones).walking)
    }

    @Test fun stationaryGPSAndNoWatchDistanceHasNoZones() {
        val log = WorkoutLog(UUID.randomUUID(), route = listOf(point(0.0, 0.0, 5.0), point(1.0, 0.0, 5.0)))
        val overall = WorkoutAnalysis.overall(log, makeState(halves), 7000.0)
        assertEquals(0.0, overall.distance)
        assertNull(overall.zones)
    }

    @Test fun zonesScaleToWorkoutDistance() {
        val log = WorkoutLog(
            UUID.randomUUID(),
            distance = listOf(WorkoutSample(0.0, 0.0), WorkoutSample(2700.0, 60.0)),
            route = listOf(point(0.0, 0.0, 5.0), point(1.0, 10.0, 5.0), point(2.0, 20.0, 24.0), point(3.0, 30.0, 24.0)),
        )
        val stats = WorkoutAnalysis.perPeriod(log, makeState(halves), 7000.0)
        val zones = assertNotNull(stats.first().zones)
        // GPS: 걷기 10m, 달리기 10m(평균 14.5km/h), 스프린트 10m → 워치 거리 60m에 맞춰 두 배
        near(60.0, zones.total)
        near(20.0, zones.walking)
        near(20.0, zones.running)
        near(20.0, zones.sprinting)
    }

    @Test fun zonesSplitByPeriodAndExcludeBreaks() {
        val log = WorkoutLog(
            UUID.randomUUID(),
            route = listOf(point(100.0, 0.0, 5.0), point(101.0, 1.0, 5.0),
                point(3000.0, 0.0, 24.0), point(3001.0, 50.0, 24.0), // 하프타임: 빼야 한다
                point(4000.0, 0.0, 24.0), point(4001.0, 7.0, 24.0)),
        )
        val state = makeState(halves)
        val stats = WorkoutAnalysis.perPeriod(log, state, 7000.0)
        near(1.0, stats[0].zones?.walking ?: 0.0)
        assertEquals(0.0, stats[0].zones?.sprinting)
        near(7.0, stats[1].zones?.sprinting ?: 0.0)
        val overall = assertNotNull(WorkoutAnalysis.overall(log, state, 7000.0).zones)
        near(8.0, overall.total) // 거리 샘플이 없으면 GPS 값 그대로
    }

    @Test fun scaledKeepsRatioAndIgnoresZero() {
        val zones = SpeedZones(1.0, 1.0, 1.0, 1.0)
        assertEquals(SpeedZones(2.0, 2.0, 2.0, 2.0), zones.scaled(to = 8.0))
        assertEquals(zones, zones.scaled(to = 0.0))
        assertEquals(SpeedZones(), SpeedZones().scaled(to = 10.0))
    }
}
