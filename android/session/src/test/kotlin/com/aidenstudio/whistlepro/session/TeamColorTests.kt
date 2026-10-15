package com.aidenstudio.whistlepro.session

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TeamColorTests {
    @Test fun readsHexAndLegacyNames() {
        assertEquals("#C8102E", TeamColor.fromCode("c8102e").hex)
        assertEquals("#FFFFFF", TeamColor.fromCode("white").hex)
        assertEquals(TeamColor.RED, TeamColor.fromCode("#FF3B30"))
        assertFailsWith<SerializationException> { TeamColor.fromCode("teal") }
    }
}
