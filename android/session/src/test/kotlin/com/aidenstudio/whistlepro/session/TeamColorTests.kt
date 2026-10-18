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

class TeamColorMarkTests {
    @Test fun markOnDarkIsVisibleOnBlack() {
        val black = com.aidenstudio.whistlepro.session.design.Palette.background.dark
        for (hex in listOf("#000000", "#001F5B", "#FF3B30", "#FFFFFF", "#34C759", "#3A3A3C")) {
            val mark = TeamColor.fromCode(hex).markOnDark
            kotlin.test.assertTrue(com.aidenstudio.whistlepro.session.design.RGB.contrast(mark, black) >= 3, hex)
        }
        // 이미 잘 보이는 색은 그대로
        assertEquals(TeamColor.RED.rgb, TeamColor.RED.markOnDark)
        assertEquals(0xFF3B30, TeamColor.RED.rgb.hex)
    }
}
