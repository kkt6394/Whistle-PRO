package com.aidenstudio.whistlepro.session.design

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 원형 화면: 위·아래 가장자리 가까운 둥근 줄이 원 밖으로 나가지 않는 폭 (홈 [새 경기]·[지난 경기]) */
class RoundFitTests {
    @Test fun bottomCapsuleFitsInsideCircle() {
        // 지름 192, 아래 여백 20, 높이 44 캡슐(모서리 22)
        val width = RoundFit.width(diameter = 192.0, edgeInset = 20.0, corner = 22.0)
        assertEquals(145.19, width, 0.01)
        // 모서리 원의 중심 + 반지름이 화면 원 안
        val r = 96.0
        val cornerCenter = hypot(width / 2 - 22.0, r - 20.0 - 22.0)
        assertTrue(cornerCenter + 22.0 <= r + 1e-9)
    }

    @Test fun deeperRowsAreWider() {
        val shallow = RoundFit.width(227.0, 10.0, 22.0)
        val deep = RoundFit.width(227.0, 40.0, 22.0)
        assertTrue(deep > shallow)
    }

    @Test fun rowReachingCenterUsesFullDiameter() {
        assertEquals(192.0, RoundFit.width(192.0, 80.0, 28.0))
    }

    @Test fun cornerLargerThanRadiusIsClamped() {
        assertEquals(192.0, RoundFit.width(192.0, 0.0, 500.0))
    }
}
