package com.aidenstudio.whistlepro.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoNamesTests {
    @Test fun rostersCoverDemoEventNumbers() {
        for (names in listOf(DemoNames.ko, DemoNames.en)) {
            assertEquals(listOf(1L, 4L, 5L, 7L, 9L, 10L, 14L, 17L), names.homeRoster.map { it.number })
            assertEquals(listOf(4L, 11L), names.awayRoster.map { it.number })
            assertTrue(names.homeRoster.take(6).all { it.starter == true })
            assertTrue(names.homeRoster.takeLast(2).all { it.starter == null })
        }
    }

    @Test fun fromExtra() {
        assertEquals("Riverside FC", DemoNames.fromExtra("en")?.homeName)
        assertEquals("FC 서울숲", DemoNames.fromExtra("ko")?.homeName)
        assertNull(DemoNames.fromExtra("detail"))
        assertNull(DemoNames.fromExtra(null))
    }

    @Test fun enHasNoHangul() {
        val en = DemoNames.en
        val all = listOf(en.homeName, en.awayName, en.presetName, en.competition, en.venue, en.referee) +
            (en.homeRoster + en.awayRoster).map { it.name }
        assertTrue(all.all { s -> s.none { it.code in 0xAC00..0xD7A3 } })
    }
}
