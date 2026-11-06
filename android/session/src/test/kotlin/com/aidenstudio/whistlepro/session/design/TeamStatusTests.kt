package com.aidenstudio.whistlepro.session.design

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.session.ClockDisplay
import com.aidenstudio.whistlepro.session.MatchDisplay
import com.aidenstudio.whistlepro.session.makeState
import com.aidenstudio.whistlepro.session.SinBinBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamStatusTests {
    private fun bin(team: Team, number: Long?, seconds: Long) =
        SinBinBadge(team, number, "${seconds / 60}:${"%02d".format(seconds % 60)}", seconds)

    @Test fun splitsByTeamShortestFirst() {
        val a = bin(Team.AWAY, 8, 300)
        val b = bin(Team.HOME, 7, 90)
        val c = bin(Team.AWAY, 4, 45)
        val status = TeamStatus.split(shortHanded = listOf(Team.AWAY), sinBins = listOf(a, b, c))
        assertEquals(TeamStatus(shortHanded = false, sinBins = listOf(b)), status.home)
        assertEquals(TeamStatus(shortHanded = true, sinBins = listOf(c, a)), status.away)
    }

    @Test fun equalTimesKeepListedOrder() {
        val a = bin(Team.HOME, 2, 120)
        val b = bin(Team.HOME, 9, 120)
        assertEquals(listOf(a, b), TeamStatus.split(emptyList(), listOf(a, b)).home.sinBins)
    }

    @Test fun noLinesWhenNothing() {
        assertEquals(emptyList(), TeamStatus(false, emptyList()).lines(3))
    }

    @Test fun allFitWithoutCount() {
        val s = TeamStatus(false, listOf(bin(Team.HOME, 7, 60), bin(Team.HOME, 9, 200)))
        assertEquals(listOf(TeamLine.SinBin(s.sinBins[0], null), TeamLine.SinBin(s.sinBins[1], null)), s.lines(2))
    }

    /** 넘치면 마지막 줄에 그 팀 전체 인원 수 (B1 스펙 §3.3 "#4 0:45 ×3") */
    @Test fun overflowCollapsesLastLine() {
        val s = TeamStatus(false, listOf(bin(Team.AWAY, 4, 45), bin(Team.AWAY, 8, 300), bin(Team.AWAY, 9, 500)))
        assertEquals(listOf(TeamLine.SinBin(s.sinBins[0], 3)), s.lines(1))
        assertEquals(listOf(TeamLine.SinBin(s.sinBins[0], null), TeamLine.SinBin(s.sinBins[1], 3)), s.lines(2))
    }

    @Test fun atLeastOneLine() {
        val s = TeamStatus(false, listOf(bin(Team.HOME, 7, 60), bin(Team.HOME, 9, 200)))
        assertEquals(s.lines(1), s.lines(0))
        assertEquals(s.lines(1), s.lines(-2))
    }

    @Test fun shortHandedLabelFirst() {
        val s = TeamStatus(true, listOf(bin(Team.AWAY, 4, 45), bin(Team.AWAY, 8, 300)))
        assertEquals(
            listOf(TeamLine.ShortHanded, TeamLine.SinBin(s.sinBins[0], null), TeamLine.SinBin(s.sinBins[1], null)),
            s.lines(3),
        )
        assertEquals(listOf(TeamLine.ShortHanded, TeamLine.SinBin(s.sinBins[0], 2)), s.lines(2))
    }

    @Test fun shortHandedWithoutSinBinsShowsLabel() {
        assertEquals(listOf(TeamLine.ShortHanded), TeamStatus(true, emptyList()).lines(1))
    }

    /** 한 줄뿐이면 일시 퇴장이 먼저 (2026-10-15 결정: 인원 부족은 빨간 배경·음성 안내로) */
    @Test fun singleLinePrefersSinBinOverLabel() {
        val s = TeamStatus(true, listOf(bin(Team.AWAY, 4, 45), bin(Team.AWAY, 8, 300)))
        assertEquals(listOf(TeamLine.SinBin(s.sinBins[0], 2)), s.lines(1))
    }

    /** 출전 금지 줄 (Ruling 8): 교체로 채우기 전까지 일시 퇴장 줄 뒤에 "#7 출전 금지", 채우면 사라진다 (Swift와 같은 케이스) */
    @Test fun teamStatusShowsUnfilledBarredLine() {
        val rules = RuleSet(sinBin = true, sinBinLength = 600)
        fun card(n: Long) = CommandKind.RecordEvent(EventPayload.Card(Team.HOME, n, CardKind.SIN_BIN))
        fun state(vararg extra: Pair<CommandKind, Double>): MatchState =
            makeState(rules, listOf(CommandKind.StartPeriod to 0.0, card(7) to 100.0, card(7) to 800.0) + extra)
        fun home(s: MatchState, now: Double): TeamStatus {
            val d = MatchDisplay(s, now, ClockDisplay.ELAPSED)
            return TeamStatus.split(d.shortHandedTeams, d.sinBins, d.barred).home
        }
        assertTrue(home(state(), 1000.0).barred.isEmpty())
        val before = home(state(), 1500.0)
        assertEquals(listOf(7L), before.barred)
        assertEquals(listOf<TeamLine>(TeamLine.Barred(7, null)), before.lines(2))
        val both = home(state(card(9) to 1450.0), 1500.0)
        assertEquals(listOf("s", "b"), both.lines(2).map { if (it is TeamLine.Barred) "b" else "s" })
        assertTrue(both.lines(1).single() is TeamLine.SinBin)
        val filled = home(state(CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 12)) to 1500.0), 1600.0)
        assertTrue(filled.barred.isEmpty())
        assertTrue(filled.lines(2).isEmpty())
    }
}
