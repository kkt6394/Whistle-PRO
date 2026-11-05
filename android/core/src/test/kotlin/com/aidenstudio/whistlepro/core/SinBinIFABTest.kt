package com.aidenstudio.whistlepro.core

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 일시퇴장 IFAB 기본 지침: 겹친 일시퇴장 무시, 두 번째 뒤 출전 금지, 교체로 나간 선수 레드 (Swift SinBinIFABTests와 같은 케이스) */
private val sinBinRules = RuleSet(sinBin = true, sinBinLength = 600)

private fun subst(team: Team, out: Long?, into: Long?, at: Double, id: String = UUID.randomUUID().toString()) =
    cmd(CommandKind.RecordEvent(EventPayload.Substitution(team, out, into)), at, id)

private fun kick(team: Team, number: Long, at: Double, id: String) =
    cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(team, number, true)), at, id)

/** 홈 7번 두 번째 일시퇴장 (100·800, 1400에 끝남) */
private fun secondSinBinHome7(): List<Command> =
    listOf(cmd(start, 0.0), card(Team.HOME, 7, CardKind.SIN_BIN, 100.0), card(Team.HOME, 7, CardKind.SIN_BIN, 800.0))

class SinBinIFABTest {
    @Test fun unnumberedSinBinsAreAlwaysFirst() {
        val s = play(sinBinRules, listOf(cmd(start, 0.0), card(Team.AWAY, null, CardKind.SIN_BIN, 100.0), card(Team.AWAY, null, CardKind.SIN_BIN, 200.0)))
        assertEquals(listOf(false, false), s.resolvedEvents.filter { it.kind == ResolvedEventKind.SIN_BIN }.map { it.isSecondSinBin })
        assertTrue(s.resolvedEvents.all { it.warnings.isEmpty() })
        assertEquals(TeamPair(11L, 9L), s.playersOnField(300.0))
    }

    @Test fun returnSubstitutionBringsPlayerBackOnPitch() {
        val rules = RuleSet(returnSubstitutions = true)
        val s = play(rules, listOf(cmd(start, 0.0), subst(Team.HOME, 5, 12, 100.0), subst(Team.HOME, 12, 5, 200.0),
            card(Team.HOME, 5, CardKind.RED, 300.0, id = "r")))
        val red = s.resolvedEvents.first { it.event.id == "r" }
        assertEquals(true, red.reducesTeam)
        assertEquals(emptyList(), red.warnings)
        assertEquals(TeamPair(10L, 11L), s.playersOnField(400.0))
    }

    @Test fun barredPlayerRedAfterFilledDoesNotReduce() {
        val s = play(sinBinRules, secondSinBinHome7() + listOf(subst(Team.HOME, 7, 12, 1500.0), card(Team.HOME, 7, CardKind.RED, 1600.0, id = "r")))
        val red = s.resolvedEvents.first { it.event.id == "r" }
        assertEquals(listOf(EventWarning.PLAYER_OFF_PITCH), red.warnings)
        assertEquals(false, red.reducesTeam)
        assertEquals(TeamPair(11L, 11L), s.playersOnField(1700.0))
        assertTrue(s.barredPlayers(1700.0).isEmpty())
    }

    @Test fun barredUnfilledRedCountsOnce() {
        val before = play(sinBinRules, secondSinBinHome7())
        assertEquals(listOf(7L), before.barredPlayers(1500.0).map { it.number })
        assertEquals(false, before.barredPlayers(1500.0).first().filled)
        assertEquals(TeamPair(10L, 11L), before.playersOnField(1500.0))

        val s = play(sinBinRules, secondSinBinHome7() + card(Team.HOME, 7, CardKind.RED, 1500.0))
        assertEquals(TeamPair(10L, 11L), s.playersOnField(1600.0))
        assertTrue(s.barredPlayers(1600.0).isEmpty())
    }

    @Test fun barredPlayerReturnedWarns() {
        val rules = RuleSet(returnSubstitutions = true, sinBin = true, sinBinLength = 600)
        val s = play(rules, secondSinBinHome7() + listOf(subst(Team.HOME, 7, 12, 1500.0, id = "out"), subst(Team.HOME, 12, 7, 1600.0, id = "back")))
        assertEquals(emptyList(), s.resolvedEvents.first { it.event.id == "out" }.warnings)
        assertEquals(listOf(EventWarning.BARRED_PLAYER_RETURNED), s.resolvedEvents.first { it.event.id == "back" }.warnings)
    }

    @Test fun penaltyEligibilityExcludesUnfilledBarred() {
        val rules = RuleSet(penalties = true, playersPerTeam = 7, minimumPlayers = null, sinBin = true, sinBinLength = 600)
        // 홈 1~6이 차고, 7번째 홈 킥은 1번이 다시 (홈 7번은 출전 금지·미채움이라 자격 6명)
        val homeNumbers = listOf(1L, 2L, 3L, 4L, 5L, 6L, 1L)
        val kicks = mutableListOf<Command>()
        homeNumbers.forEachIndexed { i, number ->
            kicks += kick(Team.HOME, number, 7000.0 + i * 2, id = "h$i")
            if (i < homeNumbers.size - 1) kicks += kick(Team.AWAY, 21L + i, 7001.0 + i * 2, id = "a$i")
        }
        val s = play(rules, secondSinBinHome7() + listOf(cmd(end, 2700.0), cmd(start, 3600.0), cmd(end, 6300.0),
            cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0)) + kicks)
        assertEquals(Phase.Penalties, s.phase)
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(emptyList(), s.resolvedEvents.first { it.event.id == "h6" }.warnings)
    }
}
