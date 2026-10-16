package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventEntryTests {
    @Test fun sinBinOnlyWhenRuleEnabled() {
        assertEquals(
            listOf(EventKindChoice.GOAL, EventKindChoice.OWN_GOAL, EventKindChoice.YELLOW, EventKindChoice.RED, EventKindChoice.SUBSTITUTION),
            EventKindChoice.available(RuleSet.FIFA_STANDARD),
        )
        assertTrue(EventKindChoice.SIN_BIN in EventKindChoice.available(RuleSet(sinBin = true)))
    }

    @Test fun choicesMapToPayloads() {
        assertEquals(EventPayload.Goal(Team.AWAY, 3, true), EventKindChoice.OWN_GOAL.payload(Team.AWAY, 3, null))
        assertEquals(EventPayload.Substitution(Team.HOME, 7, 14), EventKindChoice.SUBSTITUTION.payload(Team.HOME, 7, 14))
        assertEquals(EventPayload.Card(Team.HOME, null, CardKind.SIN_BIN), EventKindChoice.SIN_BIN.payload(Team.HOME, null, null))
        assertTrue(EventKindChoice.SUBSTITUTION.needsSecondNumber)
        assertFalse(EventKindChoice.GOAL.needsSecondNumber)
    }

    @Test fun numberEntryKeepsTwoDigits() {
        var e = NumberEntry()
        assertNull(e.value)
        e = e.append(1).append(2).append(3)
        assertEquals(12, e.value)
        e = e.deleteLast()
        assertEquals(1, e.value)

        var zero = NumberEntry().append(0)
        assertEquals(0, zero.value)
        zero = zero.append(7)
        assertEquals("7", zero.digits)
        assertEquals("9", NumberEntry(9).digits)
    }

    /** 번호 수정: 미리 채운 번호 뒤에 이어 붙지 않고, 처음 누른 숫자가 바꾼다 (실기기: 4 → 5를 눌러 45가 됨) */
    @Test fun firstDigitReplacesPrefilledNumber() {
        var e = NumberEntry(4)
        e = e.append(5)
        assertEquals("5", e.digits)
        e = e.append(1)
        assertEquals("51", e.digits)
    }

    @Test fun firstDigitReplacesPrefilledTwoDigitNumber() {
        val e = NumberEntry(10).append(7)
        assertEquals("7", e.digits)
    }

    /** 지우기로 고치기 시작하면 그 뒤로는 이어서 입력한다 */
    @Test fun deletingPrefilledNumberContinuesEditing() {
        val e = NumberEntry(45).deleteLast().append(7)
        assertEquals("47", e.digits)
    }

    /** Swift private(set)처럼 숫자 문자열로는 만들 수 없다 (3자리·비숫자·아랍 숫자를 막는다). 공개 생성자는 Int?만 받는다. */
    @Test fun numberEntryCannotBeBuiltFromDigitString() {
        val public = NumberEntry::class.java.constructors.toList()
        assertTrue(public.isNotEmpty())
        assertTrue(public.none { c -> c.parameterTypes.any { it == String::class.java } }, public.toString())
    }

    /** Swift Int(String)처럼 ASCII 숫자만 값이 된다 (Kotlin toIntOrNull은 아랍 숫자 '٣'도 3으로 읽는다) */
    @Test fun numberEntryValueAcceptsOnlyAsciiDigits() {
        val private = NumberEntry::class.java.getDeclaredConstructor(String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        assertNull(private.newInstance("\u0663", false).value)
        assertNull(private.newInstance("\uFF13", false).value)
        assertEquals(12, private.newInstance("12", false).value)
        assertEquals(-5, NumberEntry(-5).value)
    }

    @Test fun numberSlotsReadAndReplace() {
        val sub = EventPayload.Substitution(Team.HOME, 7, 14)
        assertEquals(7, sub.number(NumberSlot.PRIMARY))
        assertEquals(14, sub.number(NumberSlot.INCOMING))
        assertEquals(EventPayload.Substitution(Team.HOME, 7, 15), sub.withNumber(15, NumberSlot.INCOMING))

        val card = EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)
        assertEquals(EventPayload.Card(Team.AWAY, 5, CardKind.YELLOW), card.withNumber(5, NumberSlot.PRIMARY))
        assertEquals(EventPayload.Card(Team.AWAY, null, CardKind.YELLOW), card.withNumber(null, NumberSlot.PRIMARY))
        assertNull(card.number(NumberSlot.INCOMING))
    }
}
