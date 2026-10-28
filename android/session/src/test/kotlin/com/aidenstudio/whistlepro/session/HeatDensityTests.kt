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
}
