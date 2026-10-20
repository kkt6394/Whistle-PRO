package com.aidenstudio.whistlepro.session

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.util.UUID

class TeamColorTests {
    @Test fun readsHexAndLegacyNames() {
        assertEquals("#C8102E", TeamColor.fromCode("c8102e").hex)
        assertEquals("#FFFFFF", TeamColor.fromCode("white").hex)
        assertEquals(TeamColor.RED, TeamColor.fromCode("#FF3B30"))
        assertFailsWith<SerializationException> { TeamColor.fromCode("teal") }
    }

    @Test fun similarColorsAreHardToTell() {
        assertTrue(TeamColor.RED.isHardToTell(TeamColor.RED))
        assertTrue(TeamColor.WHITE.isHardToTell(TeamColor.fromCode("#F2F2F2")))
        assertFalse(TeamColor.RED.isHardToTell(TeamColor.BLUE))
        assertFalse(TeamColor.WHITE.isHardToTell(TeamColor.BLACK))
        // 어두운 색끼리는 더 멀어야 한다: 진보라와 남색(ΔE 33)은 경고, 견본끼리(검정·남색 ΔE 46, 자주·남색)는 경고 없음
        assertTrue(TeamColor.fromCode("#371A89").isHardToTell(TeamColor.NAVY))
        assertFalse(TeamColor.BLACK.isHardToTell(TeamColor.NAVY))
        assertFalse(TeamColor.MAROON.isHardToTell(TeamColor.NAVY))
        // 밝은 색끼리는 기준 그대로: 노랑·주황(ΔE 32)은 경고 없음
        assertFalse(TeamColor.YELLOW.isHardToTell(TeamColor.ORANGE))
    }

    @Test fun quickPicksStartWithLegacyColors() {
        assertEquals(
            listOf(TeamColor.RED, TeamColor.BLUE, TeamColor.WHITE, TeamColor.BLACK, TeamColor.YELLOW, TeamColor.GREEN,
                TeamColor.ORANGE, TeamColor.PURPLE),
            TeamColor.QUICK_PICKS.take(8),
        )
        assertEquals(13, TeamColor.QUICK_PICKS.size)
        assertEquals(listOf("#5AC8FA", "#1C2E6B", "#7B1E3A", "#FF6FAE", "#8E8E93"), TeamColor.QUICK_PICKS.drop(8).map { it.hex })
    }

    @Test fun secondaryEqualToPrimaryNormalizesToNil() {
        val setup = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLUE,
            homeColor2 = TeamColor.fromCode("#FF3B30"), awayColor2 = TeamColor.BLACK)
        val n = setup.normalizedColors()
        assertTrue(n.homeColor2 == null && n.awayColor2 == TeamColor.BLACK)
    }

    @Test fun pairwiseWarningUsesSecondaryColors() {
        val seoul = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLACK, homeColor2 = TeamColor.BLACK)
        assertTrue(seoul.teamColorsHardToTell)                                       // 빨강·검정 대 검정
        assertFalse(seoul.copy(awayColor = TeamColor.WHITE).teamColorsHardToTell)    // 빨강·검정 대 흰색
        val plain = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLUE, homeColor2 = TeamColor.MAROON)
        assertFalse(plain.teamColorsHardToTell)                                      // 같은 팀 안의 비슷한 색은 경고 없음
    }

    @Test fun summaryAndListItemCarrySecondaryColor() {
        val setup = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLUE, homeColor2 = TeamColor.BLACK)
        val record = MatchRecord(UUID.randomUUID(), 0.0, setup, emptyList())
        val summary = MatchSummary.of(record)
        assertTrue(summary.homeColor2 == TeamColor.BLACK && summary.awayColor2 == null)
        val item = MatchListItem.of(summary)
        assertNotEquals(item, MatchListItem.of(summary.copy(homeColor2 = TeamColor.WHITE)))
        assertEquals(item, MatchListItem.of(summary))
        // 요약 JSON도 보조 색을 싣고, 없으면 키를 생략한다
        assertEquals(summary, MatchSummary.fromJson(summary.toJson()))
        assertFalse(summary.toJson().containsKey("awayColor2"))
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
