package com.aidenstudio.whistlepro.session.design

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * 버튼은 화면 원 안에 있고, 링 선(둥근 끝 캡 포함)과 겹치지 않으며, 줄들 아래에 있다.
     * 네 모서리만 보면 버튼 위쪽 변이 링 끝 캡을 가로지르는 경우를 놓치므로 버튼 영역을 고르게 찍어 본다.
     */
    @Test fun roundButtonsAvoidRingAndStayOnScreen() {
        for (d in diameters) {
            val l = MainLayout.round(d)
            val c = l.ringCenter
            for (p in l.buttons.corners) {
                assertTrue(hypot(p.x - c.x, p.y - c.y) <= d / 2, "$d 버튼 모서리가 화면 밖 $p")
            }
            for (p in l.buttons.samples()) {
                assertFalse(l.ringStrokeContains(p), "$d 버튼이 링과 겹침 $p")
            }
            // 링 끝 둥근 캡의 가장 안쪽(rr·cos55° − lw/2)보다 버튼이 좁다
            val capInnerX = l.ringRadius * cos(Math.toRadians(90.0 - MainLayout.OPEN_HALF_DEGREES)) - l.lineWidth / 2
            assertTrue(l.buttons.width / 2 <= capInnerX, "$d 버튼 폭 ${l.buttons.width / 2} > 캡 안쪽 $capInnerX")
            assertTrue(l.rows.last().maxY <= l.buttons.minY, "$d 줄과 버튼이 겹침")
            assertTrue(l.buttons.height >= 22 && l.buttons.width >= 80, "$d 버튼이 너무 작음 ${l.buttons}")
        }
    }

    /** 검사 도구 자체: 링이 비어 있는 각도(57°)라도 끝 캡 안의 점은 링으로 본다, 모서리만 보면 놓치는 겹침을 찾는다 */
    @Test fun ringStrokeIncludesRoundEndCaps() {
        val l = MainLayout.round(192.0)
        val c = l.ringCenter
        val angle = Math.toRadians(90.0 - MainLayout.OPEN_HALF_DEGREES + 2)
        val inCap = Point(c.x + l.ringRadius * cos(angle), c.y + l.ringRadius * sin(angle))
        assertTrue(l.ringStrokeContains(inCap))
        assertFalse(l.ringStrokeContains(Point(c.x, c.y + l.ringRadius)))
        // 캡 바로 옆을 지나는 가로 막대: 네 모서리는 모두 링 밖이지만 가운데가 캡을 가로지른다
        val bar = Rect(c.x - l.ringRadius, inCap.y - 0.5, l.ringRadius, 1.0)
        assertTrue(bar.corners.none { l.ringStrokeContains(it) })
        assertTrue(bar.samples().any { l.ringStrokeContains(it) })
    }

    /** 작은 원형 워치에서도 시계가 크고, 상단 줄·상태 줄 글자가 10dp 이상 */
    @Test fun roundTextReadableOnSmallestWatch() {
        val l = MainLayout.round(192.0)
        assertTrue(l.clockFontSize >= 30, "${l.clockFontSize}")
        assertTrue(l.rows[0].height * 0.8 >= 10, "${l.rows[0].height}")
        assertTrue(l.rows[4].height * 0.8 >= 10, "${l.rows[4].height}")
    }
}

/** 링 선(290° 호, 굵기 lineWidth, 양 끝 둥근 캡)이 점을 덮는지 */
private fun MainLayout.ringStrokeContains(p: Point): Boolean {
    val c = ringCenter
    val half = lineWidth / 2
    val r = hypot(p.x - c.x, p.y - c.y)
    val degrees = Math.toDegrees(atan2(p.y - c.y, p.x - c.x))
    val open = 90.0 - MainLayout.OPEN_HALF_DEGREES..90.0 + MainLayout.OPEN_HALF_DEGREES
    if (r >= ringRadius - half && r <= ringRadius + half && degrees !in open) return true
    return listOf(open.start, open.endInclusive).any { end ->
        val a = Math.toRadians(end)
        hypot(p.x - (c.x + ringRadius * cos(a)), p.y - (c.y + ringRadius * sin(a))) <= half
    }
}

/** 사각형 안(변 포함)을 고르게 찍은 점들 */
private fun Rect.samples(columns: Int = 64, rows: Int = 32): List<Point> =
    (0..columns).flatMap { i -> (0..rows).map { j -> Point(minX + width * i / columns, minY + height * j / rows) } }
