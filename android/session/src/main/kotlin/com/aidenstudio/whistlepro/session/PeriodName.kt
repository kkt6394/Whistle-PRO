package com.aidenstudio.whistlepro.session

/**
 * 기간 코드(`PeriodRef.label`: 1H·2H·Q1~Q4·ET1·ET2)의 구분. 화면에서는 앱이 현지화한 이름으로 바꾼다
 * (한국어: 전반·후반·1쿼터·연장 전반). 코드는 기록·보고서 데이터에 그대로 남는다. (Swift PeriodName과 같음)
 */
sealed interface PeriodName {
    data class Half(val index: Int) : PeriodName
    data class Quarter(val index: Int) : PeriodName
    data class Extra(val index: Int) : PeriodName

    companion object {
        /** 알 수 없는 코드는 null */
        fun of(label: String): PeriodName? {
            if (label.startsWith("ET")) swiftInt(label.drop(2))?.let { return Extra(it) }
            if (label.startsWith("Q")) swiftInt(label.drop(1))?.let { return Quarter(it) }
            if (label.endsWith("H")) swiftInt(label.dropLast(1))?.let { return Half(it) }
            return null
        }

        /** Swift Int(String)처럼 ASCII 숫자만 (toIntOrNull은 아랍 숫자 '٣' 같은 유니코드 숫자도 읽는다) */
        private fun swiftInt(text: String): Int? = text.takeIf { asciiInteger.matches(it) }?.toIntOrNull()

        private val asciiInteger = Regex("[+-]?[0-9]+")
    }
}

/**
 * 기간 이름표. 기본값은 앱(워치 Components.swift·폰 RuleDisplay.swift)의 한국어 이름과 같다.
 * Wear 앱은 문자열 리소스로 만들어 넘긴다.
 */
data class PeriodNameStrings(
    val firstHalf: String,
    val secondHalf: String,
    val quarter: (Int) -> String,
    val extraFirstHalf: String,
    val extraSecondHalf: String,
) {
    companion object {
        val KOREAN = PeriodNameStrings(
            firstHalf = "전반",
            secondHalf = "후반",
            quarter = { "${it}쿼터" },
            extraFirstHalf = "연장 전반",
            extraSecondHalf = "연장 후반",
        )
    }
}

/** 기간 코드를 화면 이름으로. 이름이 없는 코드(3H·ET3·알 수 없는 코드)는 코드 그대로 (Swift periodDisplayName과 같음) */
fun periodDisplayName(label: String, strings: PeriodNameStrings = PeriodNameStrings.KOREAN): String =
    when (val name = PeriodName.of(label)) {
        PeriodName.Half(1) -> strings.firstHalf
        PeriodName.Half(2) -> strings.secondHalf
        is PeriodName.Quarter -> strings.quarter(name.index)
        PeriodName.Extra(1) -> strings.extraFirstHalf
        PeriodName.Extra(2) -> strings.extraSecondHalf
        else -> label
    }
