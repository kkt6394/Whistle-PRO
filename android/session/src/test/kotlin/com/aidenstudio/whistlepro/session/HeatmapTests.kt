package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val METERS_PER_DEGREE = 111_320.0

private fun point(north: Double, east: Double, accuracy: Double = 5.0): RoutePoint {
    val latitude = 37.5 + north / METERS_PER_DEGREE
    val longitude = 127.0 + east / (METERS_PER_DEGREE * cos(37.5 * Math.PI / 180))
    return RoutePoint(0.0, latitude, longitude, 3.0, accuracy)
}

class HeatmapTests {
    @Test fun groupsNearbyPointsIntoCells() {
        val route = listOf(point(1.0, 1.0), point(1.0, 1.0), point(1.0, 1.0), point(21.0, 1.0))
        val cells = Heatmap.cells(route, cellMeters = 4.0)
        assertEquals(2, cells.size)
        assertEquals(listOf(1, 3), cells.map { it.count }.sorted())
        assertEquals(1.0, cells.first { it.count == 3 }.intensity)
        assertTrue(abs(cells.first { it.count == 1 }.intensity - 1.0 / 3) < 1e-9)
    }

    @Test fun cellBoundsContainTheirPoints() {
        val p = point(1.0, 1.0)
        val cell = Heatmap.cells(listOf(p), cellMeters = 4.0).first()
        assertTrue(cell.minLatitude <= p.latitude && p.latitude < cell.maxLatitude)
        assertTrue(cell.minLongitude <= p.longitude && p.longitude < cell.maxLongitude)
    }

    @Test fun heatmapIgnoresInaccuratePoints() {
        assertTrue(Heatmap.cells(listOf(point(0.0, 0.0, accuracy = 50.0), point(1.0, 1.0, accuracy = -1.0))).isEmpty())
        assertTrue(Heatmap.cells(emptyList()).isEmpty())
    }

    @Test fun heartRateTimelineSkipsHalfTime() {
        val state = makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0))
        val log = WorkoutLog(UUID.randomUUID(),
            heartRate = listOf(WorkoutSample(60.0, 120.0), WorkoutSample(3000.0, 90.0), WorkoutSample(3660.0, 150.0)))
        assertEquals(listOf(ChartPoint(1.0, 120.0, 0), ChartPoint(61.0, 150.0, 1)),
            WorkoutAnalysis.heartRateTimeline(log, state, 7000.0))
    }

    /** 그래프가 기간마다 따로 선을 그려 하프타임을 잇지 않게 점에 기간 번호가 있다 (점검표 25) */
    @Test fun heartRatePointsKnowTheirPeriod() {
        val state = makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0))
        val log = WorkoutLog(UUID.randomUUID(), heartRate = listOf(WorkoutSample(2640.0, 150.0), WorkoutSample(3620.0, 140.0)))
        assertEquals(listOf(0, 1), WorkoutAnalysis.heartRateTimeline(log, state, 7000.0).map { it.period })
    }

    /** 하프타임·경기 전후에 걸은 길은 칠하지 않는다 (2026-10-28 리뷰) */
    @Test fun heatmapRouteSkipsHalfTimeAndAfterMatch() {
        val state = makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0))
        val route = listOf(-60.0, 60.0, 3000.0, 3660.0, 6400.0).map { point(0.0, 0.0).copy(t = it) }
        assertEquals(listOf(60.0, 3660.0), Heatmap.duringPeriods(route, state, 7000.0).map { it.t })
    }
}
