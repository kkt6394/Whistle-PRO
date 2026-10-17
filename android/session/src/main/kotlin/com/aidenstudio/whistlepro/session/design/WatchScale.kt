package com.aidenstudio.whistlepro.session.design

import kotlin.math.max

/**
 * 경기 중 화면 크기 비율 (B2 스펙 §3). 가장 작은 Wear 원형(지름 192dp)을 1로 보고 큰 화면만큼 키운다.
 * 1보다 작아지지 않는다. Swift `WatchScale(width:)`는 40mm 폭 162pt가 기준이다.
 */
class WatchScale(diameter: Double) {
    val factor: Double = max(1.0, diameter / BASE_DIAMETER)

    fun scaled(base: Double): Double = base * factor

    companion object {
        const val BASE_DIAMETER = 192.0
    }
}
