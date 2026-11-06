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

    /** Wear 위 띠: 기간 이름이 안 들어갈 때 쓸 짧은 이름을 함께 넘긴다 (pt-BR "1º tempo" → "1ºT", 2026-11-06) */
    @Test fun periodCarriesShortName() {
        assertEquals(
            listOf(MainHeaderItem.Period("1º tempo", short = "1ºT")),
            MainHeader.items(period = "1º tempo", warning = false, periodShort = "1ºT"),
        )
    }
}
