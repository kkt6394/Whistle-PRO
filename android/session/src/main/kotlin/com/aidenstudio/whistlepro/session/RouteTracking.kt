package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.Phase

/**
 * GPS 경로를 지금 모을지: 기간(전·후반, 연장 전·후반)이 진행 중일 때만.
 * 하프타임·연장 대기·승부차기·종료 후에는 끈다 (2026-10-15 사용자 결정 — 분석은 어차피 기간 안 위치만 쓰고, 배터리만 쓴다)
 */
val MatchState.tracksRoute: Boolean get() = phase is Phase.Period
