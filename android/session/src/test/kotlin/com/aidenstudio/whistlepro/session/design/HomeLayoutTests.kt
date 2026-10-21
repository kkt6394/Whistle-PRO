package com.aidenstudio.whistlepro.session.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 홈 세로 배분: 지름 192·227, 글자 배율 1.0·2.0, 안내 0·1·2줄 */
class HomeLayoutTests {
    private val diameters = listOf(192.0, 227.0)
    private val scales = listOf(1.0, 2.0)
    private val noticeCounts = listOf(0, 1, 2)

    private fun each(check: (Double, Double, Int, HomeLayout.Result) -> Unit) {
        for (d in diameters) for (s in scales) for (n in noticeCounts) check(d, s, n, HomeLayout.compute(d, s, n))
    }

    @Test fun circleIsAtLeastThirtyFivePercentAndNeverBelow44() = each { d, s, n, r ->
        assertTrue(r.circle >= maxOf(44.0, 0.35 * d) - 1e-9, "d=$d s=$s n=$n circle=${r.circle}")
        assertTrue(r.slot >= r.circle - 1e-9, "칸이 원보다 작다: d=$d s=$s n=$n")
    }

    @Test fun noticeKeepsAtLeastOneLine() = each { d, s, n, r ->
        if (n > 0) assertTrue(r.notice >= HomeLayout.NOTICE_LINE * s - 1e-9, "d=$d s=$s n=$n notice=${r.notice}")
        else assertEquals(0.0, r.notice)
    }

    @Test fun pastKeepsTouchHeight() = each { d, s, n, r ->
        assertTrue(r.past >= 44.0 - 1e-9, "d=$d s=$s n=$n past=${r.past}")
    }

    @Test fun roomyLayoutGivesRemainderToCircle() {
        val r = HomeLayout.compute(227.0, 1.0, 0)
        val top = 227 * RoundFit.TOP_MARGIN
        val available = 227.0 - top - 227 * RoundFit.BOTTOM_MARGIN - FillLayout.SLACK
        assertEquals(available - HomeLayout.SPACING - 44.0, r.slot, 1e-9)
        assertEquals(44.0, r.past, 1e-9)
    }

    @Test fun noticeReservesOneLinePerNoticeNotTwo() {
        // 줄마다 두 줄(30)을 예약하지 않는다: 한 줄(16). 둘이면 최대 32
        assertEquals(HomeLayout.NOTICE_LINE, HomeLayout.compute(227.0, 1.0, 1).notice, 1e-9)
        assertTrue(HomeLayout.compute(227.0, 1.0, 2).notice <= 2 * HomeLayout.NOTICE_LINE + 1e-9)
    }

    @Test fun whenShortCircleHasPriorityOverNotice() {
        val r = HomeLayout.compute(192.0, 2.0, 2)
        assertEquals(HomeLayout.minCircle(192.0), r.circle, 1e-9)
        assertEquals(HomeLayout.NOTICE_LINE * 2.0, r.notice, 1e-9)
    }
}
