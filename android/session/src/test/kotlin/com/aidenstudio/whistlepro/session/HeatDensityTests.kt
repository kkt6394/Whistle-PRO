package com.aidenstudio.whistlepro.session

import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 아이폰 HeatDensityTests와 같은 검사 */
class HeatDensityTests {
    private val metersPerDegree = 111_320.0

    private fun point(north: Double, east: Double) = RoutePoint(
        t = 0.0, latitude = 37.5 + north / metersPerDegree,
        longitude = 127.0 + east / (metersPerDegree * cos(37.5 * Math.PI / 180)), speed = 3.0, accuracy = 5.0,
    )

    @Test fun emptyCellsHaveNoDensity() {
        assertNull(Heatmap.density(emptyList()))
    }

    @Test fun peakIsOneAtTheBusiestPlaceAndFadesOutward() {
        val route = List(9) { point(0.0, 0.0) } + point(0.0, 40.0)
        val d = assertNotNull(Heatmap.density(Heatmap.cells(route)))
        assertEquals(d.width * d.height, d.values.size)
        assertEquals(1.0, d.values.max())
        assertTrue(d.values.min() >= 0)
        val peakIndex = d.values.indexOfFirst { it == 1.0 }
        assertTrue(peakIndex % d.width < d.width / 2)
        assertTrue(d.value(0, 0) < 0.02)
        assertTrue(d.value(d.width - 1, d.height - 1) < 0.02)
    }

    @Test fun northIsTheFirstRow() {
        val route = List(3) { point(30.0, 0.0) } + point(0.0, 0.0)
        val d = assertNotNull(Heatmap.density(Heatmap.cells(route)))
        val peakRow = d.values.indexOfFirst { it == 1.0 } / d.width
        assertTrue(peakRow < d.height / 2)
        assertTrue(d.maxLatitude > 37.5 + 30 / metersPerDegree)
    }

    @Test fun largeAreaStaysWithinMaximumSide() {
        val route = listOf(point(0.0, 0.0), point(900.0, 1200.0))
        val d = assertNotNull(Heatmap.density(Heatmap.cells(route), maximumSide = 256))
        assertTrue(d.width <= 256 && d.height <= 256)
        assertTrue(d.width > d.height)
    }

    // 경기장 좌표 히트 격자
    private val pitch = Pitch(GeoPoint(37.5, 127.0), length = 100.0, width = 64.0, bearing = 0.0)

    /** 경기장 중심에서 (앞 m, 오른쪽 m)만큼 떨어진 칸 하나. 방위 0이라 앞=북, 오른쪽=동 */
    private fun pitchCell(forward: Double, right: Double): List<HeatCell> {
        val latitude = pitch.center.latitude + forward / metersPerDegree
        val longitude = pitch.center.longitude + right / (metersPerDegree * cos(pitch.center.latitude * Math.PI / 180))
        return listOf(HeatCell(latitude, longitude, latitude, longitude, count = 1, intensity = 1.0))
    }

    @Test fun pitchHeatCentersOnPitchCenter() {
        val d = assertNotNull(PitchHeat.density(pitchCell(0.0, 0.0), pitch))
        val peak = d.values.indexOfFirst { it == 1.0 }
        assertEquals(1.0, d.values.max())
        assertTrue(kotlin.math.abs(peak % d.width - d.width / 2) <= 1)
        assertTrue(kotlin.math.abs(peak / d.width - d.height / 2) <= 1)
        assertEquals(-PitchHeat.MARGIN, d.minLatitude)
        assertEquals(pitch.length + PitchHeat.MARGIN, d.maxLatitude)
        assertEquals(-PitchHeat.MARGIN, d.minLongitude)
        assertEquals(pitch.width + PitchHeat.MARGIN, d.maxLongitude)
    }

    @Test fun pitchHeatTopRowIsGoalLineA() {
        // 방위 0이면 A는 남쪽 골라인: 중심에서 남쪽으로 45 m = 골라인 A에서 5 m
        val d = assertNotNull(PitchHeat.density(pitchCell(-45.0, 0.0), pitch))
        val peakRow = d.values.indexOfFirst { it == 1.0 } / d.width
        assertTrue(peakRow < d.height / 4)
    }

    @Test fun heatOutsideMarginIsDropped() {
        assertNull(PitchHeat.density(pitchCell(0.0, 32.0 + 30.0), pitch))
    }
}
