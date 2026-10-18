package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.PeriodRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 기간 코드(1H·Q3·ET1)를 화면 이름으로 바꾸기 위한 구분. 한국어는 전반·후반·3쿼터·연장 전반으로 보인다. (Swift PeriodNameTests와 같음) */
class PeriodNameTests {
    @Test fun parsesCodesFromPeriodRef() {
        assertEquals(PeriodName.Half(1), PeriodName.of(PeriodRef(PeriodRef.Kind.REGULAR, 1).label(2)))
        assertEquals(PeriodName.Half(2), PeriodName.of(PeriodRef(PeriodRef.Kind.REGULAR, 2).label(2)))
        assertEquals(PeriodName.Quarter(3), PeriodName.of(PeriodRef(PeriodRef.Kind.REGULAR, 3).label(4)))
        assertEquals(PeriodName.Extra(1), PeriodName.of(PeriodRef(PeriodRef.Kind.EXTRA, 1).label(2)))
        assertEquals(PeriodName.Extra(2), PeriodName.of(PeriodRef(PeriodRef.Kind.EXTRA, 2).label(4)))
    }

    @Test fun unknownLabelIsNil() {
        assertNull(PeriodName.of(""))
        assertNull(PeriodName.of("H"))
        assertNull(PeriodName.of("ETx"))
        // 기간 이름 리뷰: 빠져 있던 잘못된 코드
        assertNull(PeriodName.of("1Q"))
        assertNull(PeriodName.of("Q"))
        assertNull(PeriodName.of("ET"))
    }

    @Test fun fourthQuarter() {
        assertEquals(PeriodName.Quarter(4), PeriodName.of(PeriodRef(PeriodRef.Kind.REGULAR, 4).label(4)))
    }

    /** Swift Int(String)처럼 ASCII 숫자만 읽는다 (Kotlin toIntOrNull은 아랍 숫자 '٣'도 3으로 읽는다) */
    @Test fun readsOnlyAsciiDigits() {
        assertNull(PeriodName.of("Q٣"))
        assertNull(PeriodName.of("ET１"))
        assertNull(PeriodName.of("1 H"))
    }

    /** 앱 periodDisplayName(apple/AppShared/PeriodDisplay.swift)의 한국어 결과 */
    @Test fun koreanDisplayNames() {
        assertEquals("전반", periodDisplayName("1H"))
        assertEquals("후반", periodDisplayName("2H"))
        assertEquals("1쿼터", periodDisplayName("Q1"))
        assertEquals("4쿼터", periodDisplayName("Q4"))
        assertEquals("연장 전반", periodDisplayName("ET1"))
        assertEquals("연장 후반", periodDisplayName("ET2"))
    }

    /** 이름이 없는 코드는 코드 그대로 보인다 (Swift default: label) */
    @Test fun unnamedCodesShowLabel() {
        assertEquals("3H", periodDisplayName("3H"))
        assertEquals("ET3", periodDisplayName("ET3"))
        assertEquals("ETx", periodDisplayName("ETx"))
        assertEquals("", periodDisplayName(""))
    }

    /** 다른 언어는 앱이 문자열 리소스로 이름표를 만들어 넘긴다 (Swift 영어 "Q%lld" → "Q3") */
    @Test fun otherLanguageNames() {
        val english = PeriodNameStrings(
            firstHalf = "1st Half", secondHalf = "2nd Half", quarter = { "Q$it" },
            extraFirstHalf = "ET 1st Half", extraSecondHalf = "ET 2nd Half",
        )
        assertEquals("Q3", periodDisplayName("Q3", english))
        assertEquals("2nd Half", periodDisplayName("2H", english))
        assertEquals("ET 1st Half", periodDisplayName("ET1", english))
        assertEquals("ET3", periodDisplayName("ET3", english))
    }
}
