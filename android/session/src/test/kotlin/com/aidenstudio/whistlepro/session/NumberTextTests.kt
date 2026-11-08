package com.aidenstudio.whistlepro.session

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** 화면 소수점: 언어에 맞게 (독일어 쉼표, 영어 점), 반올림은 아이폰과 같다 (아이폰 NumberTextTests와 같은 값) */
class NumberTextTests {
    @Test fun decimalFollowsLocale() {
        assertEquals("12,3", NumberText.decimal(12.34, 1, Locale.GERMANY))
        assertEquals("12.3", NumberText.decimal(12.34, 1, Locale.US))
        assertEquals("3,0", NumberText.decimal(3.0, 1, Locale.FRANCE))
        assertEquals("0.3", NumberText.decimal(0.25, 1, Locale.US))
        assertEquals("1234,50", NumberText.decimal(1234.5, 2, Locale.GERMANY))
    }

    @Test fun monthDistanceUsesLocale() {
        assertEquals("9,8", MonthSummary.distanceText(9_840.0, Locale.GERMANY))
        assertEquals("9.8", MonthSummary.distanceText(9_840.0, Locale.KOREA))
        assertEquals("12", MonthSummary.distanceText(12_300.0, Locale.GERMANY))
    }
}
