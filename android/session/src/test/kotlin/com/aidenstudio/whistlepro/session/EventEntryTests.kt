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

    /** Swift private(set)처럼 숫자 문자열로는 만들 수 없다 (3자리·비숫자·아랍 숫자를 막는다). 공개 생성자는 Int?만 받는다. */
    @Test fun numberEntryCannotBeBuiltFromDigitString() {
        val public = NumberEntry::class.java.constructors.toList()
        assertTrue(public.isNotEmpty())
        assertTrue(public.none { c -> c.parameterTypes.any { it == String::class.java } }, public.toString())
    }

    /** Swift Int(String)처럼 ASCII 숫자만 값이 된다 (Kotlin toIntOrNull은 아랍 숫자 '٣'도 3으로 읽는다) */
    @Test fun numberEntryValueAcceptsOnlyAsciiDigits() {
        val private = NumberEntry::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
        assertNull(private.newInstance("\u0663").value)
        assertNull(private.newInstance("\uFF13").value)
        assertEquals(12, private.newInstance("12").value)
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
