package com.aidenstudio.whistlepro.session

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 히트맵 전체가 보이는 지도 범위 (상세 화면 지도·SNS 카드 스냅숏이 함께 쓴다) */
class HeatmapBoundsTests {
    private fun cell(lat: Double, lon: Double) = HeatCell(lat, lon, lat + 0.0001, lon + 0.0001, 1, 1.0)

    private fun bounds(vararg cells: HeatCell, pins: List<Pair<Double, Double>> = emptyList()) =
        assertNotNull(Heatmap.bounds(cells.toList(), pins))

    @Test fun noCellsNoBounds() {
        assertNull(Heatmap.bounds(emptyList()))
    }

    @Test fun centerAndSizeCoverAllCellsAndPins() {
        val bounds = bounds(cell(37.5, 127.0), cell(37.501, 127.002), pins = listOf(37.4995 to 127.0))
        assertTrue(abs(bounds.centerLatitude - (37.4995 + 37.5011) / 2) < 1e-9)
        assertTrue(abs(bounds.centerLongitude - (127.0 + 127.0021) / 2) < 1e-9)
        // 남북 약 0.0016° ≈ 178m, 동서 0.0021° × cos(37.5°) ≈ 185m
        assertTrue(abs(bounds.northSouthMeters - 0.0016 * 111_320) < 1)
        assertTrue(bounds.eastWestMeters > 180 && bounds.eastWestMeters < 190)
    }

    /** 여백을 두고 보는 거리: 넓은 쪽의 2.5배, 최소 150m */
    @Test fun viewingDistanceHasMarginAndFloor() {
        val wide = bounds(cell(37.5, 127.0), cell(37.501, 127.002))
        assertTrue(abs(wide.viewingDistance - max(wide.northSouthMeters, wide.eastWestMeters) * 2.5) < 1e-6)
        assertEquals(150.0, bounds(cell(37.5, 127.0)).viewingDistance)
    }

    @Test fun koreaUsesStandardMap() {
        assertTrue(bounds(cell(37.5, 127.0)).inKorea)
        assertFalse(bounds(cell(51.5, -0.1)).inKorea)
    }

    /** M7a 리뷰: 한국 상자에 일본 남서부(후쿠오카·시모노세키·쓰시마)가 들어갔다 */
    @Test fun koreaBoxExcludesNearbyJapan() {
        for ((lat, lon) in listOf(35.18 to 129.07, 33.5 to 126.5, 37.48 to 130.9, 34.88 to 128.62)) {   // 부산·제주·울릉·거제
            assertTrue(bounds(cell(lat, lon)).inKorea, "$lat $lon")
        }
        for ((lat, lon) in listOf(33.59 to 130.40, 33.96 to 130.94, 34.40 to 129.30)) {   // 후쿠오카·시모노세키·쓰시마
            assertFalse(bounds(cell(lat, lon)).inKorea, "$lat $lon")
        }
    }
}
