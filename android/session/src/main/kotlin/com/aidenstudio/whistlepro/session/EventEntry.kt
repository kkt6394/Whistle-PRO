package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team

/** 워치 이벤트 입력에서 고르는 종류 */
enum class EventKindChoice {
    GOAL, OWN_GOAL, YELLOW, RED, SIN_BIN, SUBSTITUTION;

    /** 교체는 나가는 번호와 들어오는 번호를 차례로 받는다. */
    val needsSecondNumber: Boolean get() = this == SUBSTITUTION

    /** 자책골의 team은 자책한 선수의 팀이다 (득점은 상대 팀). */
    fun payload(team: Team, number: Int?, secondNumber: Int?): EventPayload = when (this) {
        GOAL -> EventPayload.Goal(team, number, false)
        OWN_GOAL -> EventPayload.Goal(team, number, true)
        YELLOW -> EventPayload.Card(team, number, CardKind.YELLOW)
        RED -> EventPayload.Card(team, number, CardKind.RED)
        SIN_BIN -> EventPayload.Card(team, number, CardKind.SIN_BIN)
        SUBSTITUTION -> EventPayload.Substitution(team, number, secondNumber)
    }

    companion object {
        /** 일시 퇴장은 규칙에서 켰을 때만 보인다. */
        fun available(rules: RuleSet): List<EventKindChoice> = entries.filter { it != SIN_BIN || rules.sinBin }
    }
}

/**
 * 숫자 패드 입력 (최대 두 자리). 불변 값이라 append·deleteLast는 새 값을 돌려준다 (Compose 상태로 쓰기 쉽게).
 * Swift private(set)처럼 숫자 문자열로는 만들 수 없다 (3자리·비숫자 문자열을 막는다).
 */
@ConsistentCopyVisibility
data class NumberEntry private constructor(
    val digits: String,
    /** 번호 수정으로 미리 채운 번호: 처음 누른 숫자가 바꾼다 (뒤에 이어 붙으면 4 → 5가 45가 된다) */
    private val prefilled: Boolean,
) {
    constructor(value: Int? = null) : this(value?.toString() ?: "", value != null)

    fun append(digit: Int): NumberEntry {
        if (digit !in 0..9) return this
        val current = if (prefilled) "" else digits
        if (current.length >= 2) return this
        val base = if (current == "0") "" else current
        return NumberEntry(base + digit, false)
    }

    fun deleteLast(): NumberEntry = NumberEntry(digits.dropLast(1), false)

    /** Swift Int(String)처럼 ASCII 숫자만 (toIntOrNull은 아랍 숫자 '٣' 같은 유니코드 숫자도 읽는다) */
    val value: Int? get() = digits.takeIf { asciiInteger.matches(it) }?.toIntOrNull()

    private companion object {
        val asciiInteger = Regex("[+-]?[0-9]+")
    }
}

/** 이벤트의 등번호 자리. 교체는 PRIMARY=나가는 번호, INCOMING=들어오는 번호. */
enum class NumberSlot { PRIMARY, INCOMING }

fun EventPayload.number(slot: NumberSlot): Int? = when {
    this is EventPayload.Substitution -> if (slot == NumberSlot.PRIMARY) numberOut else numberIn
    slot != NumberSlot.PRIMARY -> null
    this is EventPayload.Goal -> number
    this is EventPayload.Card -> number
    this is EventPayload.PenaltyKick -> number
    else -> null
}

fun EventPayload.withNumber(number: Int?, slot: NumberSlot): EventPayload = when (this) {
    is EventPayload.Substitution -> if (slot == NumberSlot.PRIMARY) copy(numberOut = number) else copy(numberIn = number)
    is EventPayload.Goal -> if (slot == NumberSlot.PRIMARY) copy(number = number) else this
    is EventPayload.Card -> if (slot == NumberSlot.PRIMARY) copy(number = number) else this
    is EventPayload.PenaltyKick -> if (slot == NumberSlot.PRIMARY) copy(number = number) else this
}
