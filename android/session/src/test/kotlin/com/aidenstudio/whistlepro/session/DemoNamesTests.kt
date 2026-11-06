package com.aidenstudio.whistlepro.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoNamesTests {
    @Test fun rostersCoverDemoEventNumbers() {
        for (names in listOf(DemoNames.ko, DemoNames.en)) {
            val home = names.homeRoster.associateBy { it.number }
            val away = names.awayRoster.associateBy { it.number }
            // 데모 이벤트가 쓰는 번호: 홈 선발 1·4·5·7·9·10, 후보 14·17 / 원정 선발 4·11
            assertTrue(listOf(1L, 4L, 5L, 7L, 9L, 10L).all { home[it]?.starter == true })
            assertTrue(listOf(14L, 17L).all { home[it] != null && home[it]?.starter == null })
            assertTrue(listOf(4L, 11L).all { away[it]?.starter == true })
            // 실제 경기처럼 선발 11명씩, 번호 겹침 없음
            assertEquals(11, names.homeRoster.count { it.starter == true })
            assertEquals(11, names.awayRoster.count { it.starter == true })
            assertEquals(16, home.size)
            assertEquals(16, away.size)
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
