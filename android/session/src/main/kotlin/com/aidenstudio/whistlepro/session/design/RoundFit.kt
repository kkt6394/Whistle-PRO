package com.aidenstudio.whistlepro.session.design

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
}
