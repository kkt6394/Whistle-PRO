package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamColorsTests {
    @Test fun appliedOverridesAllFourAndDropsSameSecond() {
        val preset = MatchSetup(homeName = "FC 서울", awayName = "수원 FC", homeColor = TeamColor.RED, awayColor = TeamColor.BLUE)
        val colors = TeamColors(home = TeamColor.WHITE, away = TeamColor.BLACK, home2 = TeamColor.WHITE, away2 = null)
        val result = colors.applied(preset)
        assertEquals(TeamColor.WHITE, result.homeColor)
        assertEquals(TeamColor.BLACK, result.awayColor)
        assertNull(result.homeColor2)   // 주색과 같아 정리
        assertNull(result.awayColor2)
        assertEquals("FC 서울", result.homeName)
        assertEquals(preset.rules, result.rules)
    }

    @Test fun initFromSetupAndSettingSecond() {
        val setup = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLUE, homeColor2 = TeamColor.YELLOW)
        val colors = TeamColors.from(setup)
        assertEquals(TeamColors(TeamColor.RED, TeamColor.BLUE, TeamColor.YELLOW, null), colors)
        assertEquals(TeamColor.GREEN, colors.setting(Team.AWAY, primary = TeamColor.GREEN).away)
        assertEquals(TeamColor.WHITE, colors.setting(Team.AWAY, second = TeamColor.WHITE).away2)
    }

    @Test fun settingSecondNoneClears() {
        val colors = TeamColors(TeamColor.RED, TeamColor.BLUE, TeamColor.YELLOW, TeamColor.WHITE)
        assertEquals(TeamColors(TeamColor.RED, TeamColor.BLUE, null, TeamColor.WHITE), colors.setting(Team.HOME, second = null))
    }

    @Test fun wheelColors() {
        assertEquals("#FF0000", TeamColor.wheel(0, 2).hex)
        assertEquals("#00FF00", TeamColor.wheel(12, 2).hex)
        assertEquals("#0000FF", TeamColor.wheel(24, 2).hex)
        assertEquals(TeamColor.wheel(0, 2), TeamColor.wheel(36, 2))
        assertEquals("#FFFFFF", TeamColor.wheel(5, 0).hex)
        assertEquals("#000000", TeamColor.wheel(5, 4).hex)
        assertEquals("#FF8080", TeamColor.wheel(0, 1).hex)
        assertEquals("#8C0000", TeamColor.wheel(0, 3).hex)
        assertEquals(36, TeamColor.WHEEL_STEPS)
        assertEquals(5, TeamColor.WHEEL_LEVELS)
    }

    /** 색상환 시작 칸: 지금 색에 가장 가까운 색상 (무채색은 0 = 빨강). Swift `wheelStep`과 같다 */
    @Test fun wheelStepNearestHue() {
        assertEquals(0, TeamColor("#FF0000").wheelStep)
        assertEquals(12, TeamColor("#00FF00").wheelStep)
        assertEquals(24, TeamColor("#0000FF").wheelStep)
        assertEquals(0, TeamColor.WHITE.wheelStep)
        assertEquals(0, TeamColor.BLACK.wheelStep)
        // 355° 근처는 36이 아니라 0으로 감긴다
        assertEquals(0, TeamColor("#FF0015").wheelStep)
        for (s in listOf(0, 1, 5, 11, 17, 23, 29, 35)) {
            for (level in 1..3) assertEquals(s, TeamColor.wheel(s, level).wheelStep, "step $s level $level")
        }
    }

    @Test fun jsonOmitsMissingSecond() {
        val json = TeamColors(TeamColor.RED, TeamColor.BLUE, TeamColor.WHITE, null).toJson()
        assertEquals("\"#FF3B30\"", json["home"].toString())
        assertEquals("\"#FFFFFF\"", json["home2"].toString())
        assertNull(json["away2"])
    }
}

class MatchNoteColorsTests {
    private val colors = TeamColors(TeamColor.WHITE, TeamColor.BLACK, null, TeamColor.YELLOW)

    @Test fun noteColorsRoundTrip() {
        val note = MatchNote(competition = "Cup", colors = colors)
        assertEquals(note, MatchNote.fromJson(note.toJson()))
        val inner = note.toJson()["colors"] as kotlinx.serialization.json.JsonObject
        assertNull(inner["home2"])
        assertEquals("\"#FFCC00\"", inner["away2"].toString())
    }

    @Test fun oldNoteWithoutColorsDecodes() {
        val note = MatchNote.fromJson(parseJson("""{"competition":"Cup"}""".toByteArray()) as kotlinx.serialization.json.JsonObject)
        assertNull(note.colors)
        assertFalse(note.isEmpty)
        assertTrue(MatchNote().isEmpty)
        assertTrue(MatchNote(colors = null).isEmpty)
        assertFalse(MatchNote(colors = colors).isEmpty)
    }

    @Test fun reportAndSummaryUseNoteColors() {
        val setup = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLUE, homeColor2 = TeamColor.YELLOW)
        val bundle = MatchBundle(makeRecord(setup, listOf(CommandKind.StartPeriod to 0.0)), null)
        val note = MatchNote(colors = colors)
        val report = MatchReport(bundle, note)
        assertEquals(TeamColor.WHITE, report.homeColor)
        assertEquals(TeamColor.BLACK, report.awayColor)
        assertNull(report.homeColor2)
        assertEquals(TeamColor.YELLOW, report.awayColor2)
        val summary = MatchSummary.of(bundle.record).applying(note)
        assertEquals(TeamColor.WHITE, summary.homeColor)
        assertNull(summary.homeColor2)
        assertEquals(TeamColor.YELLOW, summary.awayColor2)
        val plain = MatchReport(bundle, MatchNote(competition = "Cup"))
        assertEquals(TeamColor.RED, plain.homeColor)
        assertEquals(TeamColor.YELLOW, plain.homeColor2)
        assertEquals(TeamColor.RED, MatchSummary.of(bundle.record).applying(null).homeColor)
    }

    @Test fun noteColorsClearSecondColor() {
        val setup = MatchSetup(homeColor = TeamColor.RED, awayColor = TeamColor.BLUE, homeColor2 = TeamColor.YELLOW)
        val bundle = MatchBundle(makeRecord(setup, listOf(CommandKind.StartPeriod to 0.0)), null)
        val note = MatchNote(colors = TeamColors(TeamColor.RED, TeamColor.BLUE, null, null))
        assertNull(MatchReport(bundle, note).homeColor2)
        assertNull(MatchSummary.of(bundle.record).applying(note).homeColor2)
    }
}
