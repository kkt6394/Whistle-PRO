package com.aidenstudio.whistlepro.session.design

import kotlin.test.Test
import kotlin.test.assertEquals

class MainHeaderTests {
    /** 상단 띠가 넘쳐 줄어들거나 잘려도 경고는 남아야 한다 */
    @Test fun warningComesFirst() {
        assertEquals(
            listOf(MainHeaderItem.Warning, MainHeaderItem.Period("연장 전반")),
            MainHeader.items(period = "연장 전반", warning = true),
        )
    }

    /** 중단 시간은 띠에 없다 — 큰 시계 아래 줄에 둔다 */
    @Test fun onlyWhatExists() {
        assertEquals(listOf(MainHeaderItem.Period("전반")), MainHeader.items(period = "전반", warning = false))
        assertEquals(listOf(MainHeaderItem.Warning), MainHeader.items(period = null, warning = true))
    }
}
