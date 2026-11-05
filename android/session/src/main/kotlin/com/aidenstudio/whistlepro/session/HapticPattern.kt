package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.AlertKind

/** 플랫폼 진동 종류에 대응하는 값 (watchOS: WKHapticType, Wear OS: 파형 표) */
enum class HapticTone { NOTIFICATION, DIRECTION_UP, SUCCESS, RETRY, START, STOP }

data class HapticPattern(
    val tone: HapticTone,
    val repeats: Int,
    /** 반복 사이 간격(초) */
    val interval: Double,
)

/**
 * 손목 감각만으로 구분되도록 종류마다 다른 패턴. 기간 종료가 가장 길다.
 * 예고도 강한 진동을 쓴다 (directionUp은 워치 SE에서 느껴지지 않았다).
 */
val AlertKind.hapticPattern: HapticPattern
    get() = when (this) {
        AlertKind.WARNING_BEFORE_END -> HapticPattern(HapticTone.NOTIFICATION, 2, 1.0)
        AlertKind.PERIOD_END -> HapticPattern(HapticTone.NOTIFICATION, 3, 0.8)
        AlertKind.BREAK_ENDING_SOON -> HapticPattern(HapticTone.RETRY, 1, 0.0)
        AlertKind.BREAK_END -> HapticPattern(HapticTone.NOTIFICATION, 2, 0.8)
        // 두 번째 일시 퇴장 끝 진동은 아직 일반 끝과 같다 (따로 정하는 일은 세션 작업, Swift와 같음)
        AlertKind.SIN_BIN_END, AlertKind.SECOND_SIN_BIN_END -> HapticPattern(HapticTone.RETRY, 2, 0.6)
    }
