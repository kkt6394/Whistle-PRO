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

    @Test fun circleTakesGivenHeightWhenItFits() {
        assertEquals(85.0, RoundFit.circleDiameter(192.0, 28.8, 85.0), 1e-9)
    }

    @Test fun circleIsLimitedByRoundScreenMargin() {
        // 높이가 넉넉해도 아래가 좌우 여백까지 원 안: 192 - 여백 9.984 - 위 28.8
        assertEquals(153.216, RoundFit.circleDiameter(192.0, 28.8, 200.0), 1e-9)
        // 그 크기의 원은 정말 화면 원 안
        val d = RoundFit.circleDiameter(192.0, 28.8, 200.0)
        val center = 28.8 + d / 2
        assertTrue(kotlin.math.abs(center - 96.0) + d / 2 <= 96.0 - 192.0 * RoundFit.SIDE_MARGIN + 1e-9)
    }

    @Test fun circleHasMinimumSize() {
        assertEquals(44.0, RoundFit.circleDiameter(192.0, 28.8, 30.0))
    }

    @Test fun circleMinimumCanBeRaised() {
        assertEquals(67.2, RoundFit.circleDiameter(192.0, 28.8, 30.0, minimum = 67.2), 1e-9)
    }

    @Test fun rowWidthNearCenterUsesSideMarginWidth() {
        assertEquals(192.0 * (1 - 2 * RoundFit.SIDE_MARGIN), RoundFit.rowWidth(192.0, 96.0), 1e-9)
    }

    @Test fun rowWidthNearEdgeSubtractsMarginFromChord() {
        // y=150 (중심에서 54): 현 158.74 - 좌우 여백 2*9.984
        assertEquals(158.74 - 2 * 192.0 * RoundFit.SIDE_MARGIN, RoundFit.rowWidth(192.0, 150.0), 0.01)
    }
}
