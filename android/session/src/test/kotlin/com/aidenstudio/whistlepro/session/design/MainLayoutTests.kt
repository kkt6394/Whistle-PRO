package com.aidenstudio.whistlepro.session.design

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainLayoutTests {
    companion object {
        /** watchOS 화면(pt): SE 40mm, 41, 42, 44, 45, 46, Ultra 49mm. 상단 상태 표시줄을 뺀 높이도 함께 검사한다. */
        val sizes: List<Pair<Double, Double>> = listOf(
            162.0 to 197.0, 176.0 to 215.0, 187.0 to 223.0, 184.0 to 224.0, 198.0 to 242.0, 208.0 to 248.0, 205.0 to 251.0,
        ).let { screens -> screens + screens.map { it.first to it.second - 30 } }
    }

    @Test fun rowsStayInsideRing() {
        for ((w, h) in sizes) {
            val l = MainLayout.rect(w, h)
            val inner = l.ringRadius - l.lineWidth / 2
            assertEquals(5, l.rows.size)
            for (row in l.rows) for (c in row.corners) {
                assertTrue(hypot(c.x - l.ringCenter.x, c.y - l.ringCenter.y) <= inner + 0.001, "$w×$h $row")
            }
        }
    }

    @Test fun rowsDoNotOverlap() {
        for ((w, h) in sizes) {
            val rows = MainLayout.rect(w, h).rows
            rows.zipWithNext { upper, lower -> assertTrue(upper.maxY <= lower.minY + 0.001, "$w×$h") }
            for (row in rows) assertTrue(row.height > 0 && row.width > 0, "$w×$h")
        }
    }

    @Test fun buttonsSitBelowRing() {
        for ((w, h) in sizes) {
            val l = MainLayout.rect(w, h)
            assertTrue(l.buttons.minY >= l.ringCenter.y + l.ringRadius + l.lineWidth / 2, "$w×$h")
            assertTrue(l.buttons.maxY <= h, "$w×$h")
            assertTrue(l.buttons.minX >= 0 && l.buttons.maxX <= w, "$w×$h")
        }
    }

    @Test fun ringFitsScreen() {
        for ((w, h) in sizes) {
            val l = MainLayout.rect(w, h)
            val outer = l.ringRadius + l.lineWidth / 2
            assertTrue(l.ringCenter.x - outer >= 0 && l.ringCenter.x + outer <= w, "$w×$h")
            assertTrue(l.ringCenter.y - outer >= 0, "$w×$h")
        }
    }

    /** 스펙 §3.2: 위쪽 안전 영역(약 30pt)을 뺀 높이에서 시계는 40mm 약 29pt ~ 49mm 약 40pt */
    @Test fun clockScalesFromSmallestToLargestWatch() {
        val small = MainLayout.rect(162.0, 197.0 - 30).clockFontSize
        val large = MainLayout.rect(205.0, 251.0 - 30).clockFontSize
        assertTrue(small in 28.0..31.0, "$small")
        assertTrue(large in 38.0..42.0, "$large")
    }

    /** 가장 작은 워치에서도 상단 줄·상태 줄 글자(줄 높이의 0.8)가 10pt 이상 */
    @Test fun secondaryRowsReadableOnSmallestWatch() {
        val rows = MainLayout.rect(162.0, 197.0 - 30).rows
        assertTrue(rows[0].height * 0.8 >= 10, "${rows[0].height}")
        assertTrue(rows[4].height * 0.8 >= 10, "${rows[4].height}")
    }

    @Test fun zeroSizeDoesNotCrash() {
        val l = MainLayout.rect(0.0, 0.0)
        assertEquals(5, l.rows.size)
        assertTrue(l.ringRadius >= 0)
        assertTrue(l.rows.all { it.width >= 0 && it.height >= 0 })
        val r = MainLayout.round(0.0)
        assertEquals(5, r.rows.size)
        assertTrue(r.rows.all { it.width >= 0 && it.height >= 0 } && r.buttons.width >= 0)
    }

    /** Always-On: 시계·추가시간·점수는 남기고 상단 줄·상태 줄은 숨긴다 */
    @Test fun alwaysOnKeepsClockAddedTimeAndScore() {
        assertEquals(listOf(1, 2, 3), (0 until 5).filter { MainLayout.showsRow(it, reduced = true) })
        assertEquals(listOf(0, 1, 2, 3, 4), (0 until 5).filter { MainLayout.showsRow(it, reduced = false) })
    }
}

/** Kotlin 추가: Wear OS 원형 화면 배치. 지름(dp): 작은 워치 ~ 큰 워치 */
class RoundLayoutTests {
    private val diameters = listOf(192.0, 200.0, 213.0, 227.0, 240.0)

    @Test fun roundRowsFitInsideCircle() {
        for (d in diameters) {
            val l = MainLayout.round(d)
            val inner = l.ringRadius - l.lineWidth / 2
            for (row in l.rows) for (c in row.corners) {
                assertTrue(hypot(c.x - l.ringCenter.x, c.y - l.ringCenter.y) <= inner + 0.001, "$d $row")
            }
            l.rows.zipWithNext { upper, lower -> assertTrue(upper.maxY <= lower.minY + 0.001, "$d") }
            assertTrue(l.rows.all { it.height > 0 && it.width > 0 }, "$d")
        }
    }

    @Test fun ringFitsRoundScreen() {
        for (d in diameters) {
            val l = MainLayout.round(d)
            assertTrue(l.ringRadius + l.lineWidth / 2 <= d / 2, "$d")
        }
    }

    /** 버튼은 화면 원 안에 있고, 링 선과 겹치지 않으며(겹치는 반지름이면 링이 비어 있는 아래 70° 안), 줄들 아래에 있다 */
    @Test fun roundButtonsAvoidRingAndStayOnScreen() {
        for (d in diameters) {
            val l = MainLayout.round(d)
            val c = l.ringCenter
            for (p in l.buttons.corners) {
                val r = hypot(p.x - c.x, p.y - c.y)
                assertTrue(r <= d / 2, "$d 버튼 모서리가 화면 밖 $p")
                if (r >= l.ringRadius - l.lineWidth / 2 && r <= l.ringRadius + l.lineWidth / 2) {
                    val degrees = Math.toDegrees(atan2(p.y - c.y, p.x - c.x))
                    assertTrue(degrees in 55.0..125.0, "$d 버튼이 링과 겹침 $p ($degrees°)")
                }
            }
            // 버튼 위쪽 변이 링 선을 가로지르지 않도록: 버튼 폭이 링 끝 안쪽 모서리보다 좁다
            val endInnerX = (l.ringRadius - l.lineWidth / 2) * kotlin.math.cos(Math.toRadians(55.0))
            assertTrue(l.buttons.width / 2 <= endInnerX, "$d")
            assertTrue(l.rows.last().maxY <= l.buttons.minY, "$d 줄과 버튼이 겹침")
            assertTrue(l.buttons.height >= 22 && l.buttons.width >= 80, "$d 버튼이 너무 작음 ${l.buttons}")
        }
    }

    /** 작은 원형 워치에서도 시계가 크고, 상단 줄·상태 줄 글자가 10dp 이상 */
    @Test fun roundTextReadableOnSmallestWatch() {
        val l = MainLayout.round(192.0)
        assertTrue(l.clockFontSize >= 30, "${l.clockFontSize}")
        assertTrue(l.rows[0].height * 0.8 >= 10, "${l.rows[0].height}")
        assertTrue(l.rows[4].height * 0.8 >= 10, "${l.rows[4].height}")
    }
}
