package com.aidenstudio.whistlepro.session.design

import com.aidenstudio.whistlepro.session.design.FillItem.Role
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FillLayoutTests {
    @Test fun heroTakesSpare() {
        val r = FillLayout.layout(200.0, 4.0, listOf(FillItem(20.0), FillItem(44.0, Role.Hero)), heroCap = null)
        assertEquals(listOf(20.0, 176.0), r.heights)
        assertTrue(r.fits && r.spacing == 4.0)
    }

    /** 주인공은 상한에서 멈추고, 남는 높이는 두 번째 요소, 그다음 간격이 받는다 (B2 스펙 §2.4) */
    @Test fun capThenSecondThenSpacing() {
        val r = FillLayout.layout(200.0, 4.0, listOf(FillItem(40.0, Role.Second), FillItem(44.0, Role.Hero)), heroCap = 60.0)
        assertEquals(listOf(136.0, 60.0), r.heights)
        val noSecond = FillLayout.layout(200.0, 4.0, listOf(FillItem(20.0), FillItem(44.0, Role.Hero)), heroCap = 60.0)
        assertEquals(listOf(20.0, 60.0), noSecond.heights)
        assertTrue(abs(noSecond.spacing - 120) < 0.001) // 4 + (200 - 20 - 60 - 4)
    }

    @Test fun heroesShareEqually() {
        val r = FillLayout.layout(120.0, 0.0, listOf(FillItem(20.0), FillItem(44.0, Role.Hero), FillItem(44.0, Role.Hero)), heroCap = null)
        assertEquals(listOf(20.0, 50.0, 50.0), r.heights)
    }

    /** 모자라면 정한 순서대로 숨긴다. 들어가면 더 숨기지 않는다 (B2 스펙 §2.5) */
    @Test fun hidesInOrderUntilFits() {
        val items = listOf(FillItem(20.0, Role.Hideable(1)), FillItem(14.0, Role.Hideable(2)), FillItem(40.0), FillItem(44.0, Role.Hero))
        val r = FillLayout.layout(100.0, 0.0, items)
        assertEquals(listOf(true, false, false, false), r.hidden)
        assertEquals(listOf(0.0, 14.0, 40.0, 46.0), r.heights)
        assertTrue(r.fits)
    }

    @Test fun doesNotHideWhenItFits() {
        val r = FillLayout.layout(300.0, 4.0, listOf(FillItem(20.0, Role.Hideable(1)), FillItem(44.0, Role.Hero)))
        assertEquals(listOf(false, false), r.hidden)
    }

    /** 다 숨겨도 안 들어가면 최소 높이 그대로, fits = false (화면이 스크롤로 바꾼다) */
    @Test fun reportsWhenItCannotFit() {
        val r = FillLayout.layout(100.0, 4.0, listOf(FillItem(20.0, Role.Hideable(1)), FillItem(60.0), FillItem(44.0, Role.Hero)))
        assertFalse(r.fits)
        assertEquals(listOf(true, false, false), r.hidden)
        assertEquals(listOf(0.0, 60.0, 44.0), r.heights)
    }

    /** 홈: 알림이 늘면 [새 경기]가 최소까지 줄어 양보한다 */
    @Test fun heroShrinksToMinimumBeforeHiding() {
        val r = FillLayout.layout(140.0, 6.0, listOf(FillItem(44.0, Role.Hero), FillItem(40.0), FillItem(44.0)), heroCap = null)
        assertEquals(listOf(44.0, 40.0, 44.0), r.heights)
        assertTrue(r.fits)
    }

    @Test fun emptyAndNegativeAreSafe() {
        assertEquals(emptyList(), FillLayout.layout(100.0, 4.0, emptyList()).heights)
        val r = FillLayout.layout(-10.0, 4.0, listOf(FillItem(44.0, Role.Hero)))
        assertTrue(r.heights == listOf(44.0) && !r.fits)
    }

    /** 조건부로 빠진 항목(높이 0, 고정)은 그리지 않으므로 간격도 세지 않는다 */
    @Test fun zeroHeightFixedItemsTakeNoSpacing() {
        val r = FillLayout.layout(100.0, 10.0, listOf(FillItem(40.0, Role.Hero), FillItem(0.0), FillItem(0.0), FillItem(40.0)), heroCap = null)
        assertTrue(r.fits)
        assertEquals(listOf(50.0, 0.0, 0.0, 40.0), r.heights)
        assertEquals(10.0, r.spacing)
    }

    /** 주인공 최소 높이가 달라 하나가 먼저 상한에 닿으면, 남은 몫은 다른 주인공이 받는다 */
    @Test fun heroLeftoverGoesToOtherHeroes() {
        val r = FillLayout.layout(120.0, 0.0, listOf(FillItem(50.0, Role.Hero), FillItem(30.0, Role.Hero)), heroCap = 60.0)
        assertEquals(listOf(60.0, 60.0), r.heights)
        assertEquals(0.0, r.spacing)
    }
}
