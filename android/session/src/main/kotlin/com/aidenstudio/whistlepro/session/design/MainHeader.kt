package com.aidenstudio.whistlepro.session.design

/** 워치 메인 상단 띠의 한 조각 (B1 스펙 §3.1) */
sealed interface MainHeaderItem {
    data object Warning : MainHeaderItem
    /**
     * [short]: 띠에 [name]이 안 들어갈 때 대신 쓸 짧은 이름 (휴식 화면 "하프타임" → "휴식", 2026-11-04)
     * [isRest]: 휴식 이름(하프타임·연장 대기)이면 true — 경기 중 기간 이름만 라임, 휴식 이름은 회색 (애플 nameIsPeriod와 맞춤, 2026-11-04)
     */
    data class Period(val name: String, val short: String? = null, val isRest: Boolean = false) : MainHeaderItem
}

/**
 * 상단 띠 순서. 띠가 좁아 긴 문구는 줄어들거나 잘리므로 놓치면 안 되는 ⚠를 맨 앞에 둔다.
 * 중단 시간은 띠에 두지 않고 큰 시계 아래 줄에 둔다.
 * [periodShort]: 기간 이름이 띠에 안 들어갈 때 대신 쓸 짧은 이름 (Wear 위 띠 전용, 2026-11-06 — 애플 띠는 글자를 줄여 넣어 이 값이 없다)
 */
object MainHeader {
    fun items(period: String?, warning: Boolean, periodShort: String? = null): List<MainHeaderItem> = buildList {
        if (warning) add(MainHeaderItem.Warning)
        if (period != null) add(MainHeaderItem.Period(period, short = periodShort))
    }
}
