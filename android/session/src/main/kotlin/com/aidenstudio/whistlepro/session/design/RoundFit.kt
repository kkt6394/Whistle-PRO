package com.aidenstudio.whistlepro.session.design

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 원형 화면에서 경기 밖 화면(홈·동전)의 여백과 줄 폭. 애플 `bottomRowInset`(둥근 사각 화면 모서리)의 원형판.
 * 값은 지름 비율이다.
 */
object RoundFit {
    /** 좌우 여백: 지름의 5.2% (Wear 목록 지침) */
    const val SIDE_MARGIN = 0.052
    /** 위 여백: 시스템 시각(TimeText) 아래 */
    const val TOP_MARGIN = 0.15
    /** 아래 여백 */
    const val BOTTOM_MARGIN = 0.104

    /**
     * 화면 위(또는 아래) 가장자리에서 [edgeInset]만큼 떨어진 변을 가진 둥근 사각형(모서리 반지름 [corner])이
     * 원 안에 들어가는 최대 폭. 변이 중심을 넘어가면 지름 전체.
     */
    fun width(diameter: Double, edgeInset: Double, corner: Double): Double {
        val radius = diameter / 2
        val c = corner.coerceIn(0.0, radius)
        // 모서리 원 중심의 화면 중심에서의 세로 거리
        val depth = radius - edgeInset - c
        if (depth <= 0) return diameter
        val inner = radius - c
        return 2 * (c + sqrt(max(0.0, inner * inner - depth * depth)))
    }

    /** 동그라미 버튼 지름의 최소 (터치 대상) */
    const val MIN_CIRCLE = 44.0

    /**
     * 화면 위에서 [top]만큼 내려온 곳에 윗점을 둔 동그라미 버튼의 지름: 받은 높이 [height] 안에서,
     * 좌우 여백까지 화면 원 안에 들어가는 가장 큰 값 (최소 [minimum]).
     */
    fun circleDiameter(diameter: Double, top: Double, height: Double, minimum: Double = MIN_CIRCLE): Double {
        // 중심이 화면 중심 아래일 때 아랫점이 원 안(여백 포함)에 닿는 한계. 윗점이 위쪽 여백 안이면 어차피 안 맞아 높이로만 줄인다
        val edge = diameter - diameter * SIDE_MARGIN - top
        return max(minimum, minOf(height, edge))
    }

    /**
     * 화면 위에서 [centerY] 높이의 줄 폭: 그 높이의 현 폭에서 좌우 여백을 뺀 값 (가운데 정렬 글자는 이 폭 안이면 원 안).
     * 좌우 여백 안쪽으로 제한한다.
     */
    fun rowWidth(diameter: Double, centerY: Double): Double {
        val radius = diameter / 2
        val dy = abs(centerY - radius)
        val chord = if (dy >= radius) 0.0 else 2 * sqrt(radius * radius - dy * dy)
        return max(0.0, minOf(chord - 2 * diameter * SIDE_MARGIN, diameter * (1 - 2 * SIDE_MARGIN)))
    }
}
