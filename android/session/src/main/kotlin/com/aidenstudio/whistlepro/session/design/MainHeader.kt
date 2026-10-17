package com.aidenstudio.whistlepro.session.design

/** 워치 메인 상단 띠의 한 조각 (B1 스펙 §3.1) */
sealed interface MainHeaderItem {
    data object Warning : MainHeaderItem
    data class Period(val name: String) : MainHeaderItem
}

/**
 * 상단 띠 순서. 띠가 좁아 긴 문구는 줄어들거나 잘리므로 놓치면 안 되는 ⚠를 맨 앞에 둔다.
 * 중단 시간은 띠에 두지 않고 큰 시계 아래 줄에 둔다.
 */
object MainHeader {
    fun items(period: String?, warning: Boolean): List<MainHeaderItem> = buildList {
        if (warning) add(MainHeaderItem.Warning)
        if (period != null) add(MainHeaderItem.Period(period))
    }
}
