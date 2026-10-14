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
