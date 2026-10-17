package com.aidenstudio.whistlepro.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MatchSetupTests {
    @Test fun secondaryColorRoundTripsAndOmitsWhenNil() {
        val setup = MatchSetup(homeColor2 = TeamColor.fromCode("#000000"))
        val json = setup.toJson()
        assertEquals(JsonPrimitive("#000000"), json["homeColor2"])
        assertFalse(json.containsKey("awayColor2"))
        val back = MatchSetup.fromJson(json)
        assertEquals(TeamColor.fromCode("#000000"), back.homeColor2)
        assertNull(back.awayColor2)
    }

    @Test fun oldJsonWithoutSecondaryDecodes() {
        val json = JsonObject(MatchSetup().toJson().filterKeys { it != "homeColor2" && it != "awayColor2" })
        val back = MatchSetup.fromJson(json)
        assertNull(back.homeColor2)
        assertNull(back.awayColor2)
    }
}
