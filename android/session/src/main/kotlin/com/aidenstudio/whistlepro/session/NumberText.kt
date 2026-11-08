package com.aidenstudio.whistlepro.session

import java.util.Locale

/**
 * 화면에 보이는 소수: 화면 언어의 소수점으로 (독일어 12,3 · 영어 12.3). 묶음 기호(1,000)는 넣지 않는다.
 * 예전엔 `Locale.ROOT`라 독일어·프랑스어 등 열 개 언어에서도 점으로 찍혔다 (2026-11-09 독일어 점검).
 * 리플레이 영상(영어 고정)은 이 함수를 쓰지 않는다. 아이폰 `NumberText`와 같은 규칙
 */
object NumberText {
    fun decimal(value: Double, digits: Int, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.${digits}f", value)
}
