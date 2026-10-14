package com.aidenstudio.whistlepro.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleSetTests {
    private fun decode(json: String) = RuleSet.fromJson(Json.parseToJsonElement(json).jsonObject)

    @Test fun fifaStandardDefaults() {
        val r = RuleSet.FIFA_STANDARD
        assertEquals(2, r.periodCount)
        assertEquals(2700, r.periodLength)
        assertEquals(900, r.halfTimeLength)
        assertEquals(false, r.extraTime)
        assertEquals(900, r.extraTimePeriodLength)
        assertEquals(60, r.extraTimeBreakLength)
        assertEquals(false, r.penalties)
        assertEquals(5, r.penaltyKicksPerTeam)
        assertEquals(11, r.playersPerTeam)
        assertEquals(7, r.minimumPlayers)
        assertEquals(5, r.substitutionLimit)
        assertEquals(false, r.returnSubstitutions)
        assertEquals(0, r.extraTimeExtraSubs)
        assertTrue(r.secondYellowSendOff)
        assertEquals(false, r.sinBin)
        assertEquals(600, r.sinBinLength)
        assertTrue(r.alternateKickoff)
    }

    @Test fun decodesPartialJSONOverFifaDefaults() {
        val r = decode("""{"periodCount": 4, "periodLength": 1500}""")
        assertEquals(4, r.periodCount)
        assertEquals(1500, r.periodLength)
        assertEquals(900, r.halfTimeLength)
        assertEquals(7, r.minimumPlayers)
    }

    @Test fun explicitNullTurnsOptionalRulesOff() {
        val r = decode("""{"minimumPlayers": null, "substitutionLimit": null}""")
        assertNull(r.minimumPlayers)
        assertNull(r.substitutionLimit)
    }

    @Test fun roundTripKeepsDisabledOptionals() {
        val r = RuleSet.FIFA_STANDARD.copy(substitutionLimit = null, minimumPlayers = null)
        val back = RuleSet.fromJson(Json.parseToJsonElement(r.toJson().toString()).jsonObject)
        assertEquals(r, back)
    }

    @Test fun validateAcceptsFifaStandard() {
        RuleSet.FIFA_STANDARD.validate()
    }

    @Test fun validateRejectsOutOfRange() {
        val cases = listOf(
            RuleSet(periodCount = 3) to RuleSet.Field.periodCount,
            RuleSet(periodLength = 30) to RuleSet.Field.periodLength,
            RuleSet(halfTimeLength = 3601) to RuleSet.Field.halfTimeLength,
            RuleSet(quarterBreakLength = 1801) to RuleSet.Field.quarterBreakLength,
            RuleSet(extraTimePeriodLength = 1801) to RuleSet.Field.extraTimePeriodLength,
            RuleSet(extraTimeBreakLength = 901) to RuleSet.Field.extraTimeBreakLength,
            RuleSet(penaltyKicksPerTeam = 0) to RuleSet.Field.penaltyKicksPerTeam,
            RuleSet(playersPerTeam = 2) to RuleSet.Field.playersPerTeam,
            RuleSet(playersPerTeam = 7, minimumPlayers = 8) to RuleSet.Field.minimumPlayers,
            RuleSet(substitutionLimit = 21) to RuleSet.Field.substitutionLimit,
            RuleSet(extraTimeExtraSubs = 4) to RuleSet.Field.extraTimeExtraSubs,
            RuleSet(sinBinLength = 30) to RuleSet.Field.sinBinLength,
        )
        for ((rules, field) in cases) {
            val e = assertFailsWith<RuleSetException> { rules.validate() }
            assertEquals(field, e.field)
        }
    }

    @Test fun deviationsListOnlyChangedFields() {
        val r = RuleSet(periodCount = 4, periodLength = 1500, substitutionLimit = null)
        assertEquals(listOf(RuleSet.Field.periodCount, RuleSet.Field.periodLength, RuleSet.Field.substitutionLimit), r.deviations())
        assertTrue(RuleSet.FIFA_STANDARD.deviations().isEmpty())
    }
}
