package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val pkRules = RuleSet(penalties = true)

private fun kick(team: Team, scored: Boolean, at: Double, number: Long? = null, id: String) =
    cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(team, number, scored)), at, id)

/** first 팀부터 번갈아 찬다. results[i]는 i번째 킥 성공 여부, ID는 "k0", "k1", ... */
private fun kicks(first: Team, results: List<Boolean>): List<Command> = results.mapIndexed { i, scored ->
    kick(if (i % 2 == 0) first else first.opponent, scored, 7000.0 + i, id = "k$i")
}

private fun shootout(first: Team, results: List<Boolean>, rules: RuleSet = pkRules): MatchState =
    play(rules, twoHalves() + cmd(CommandKind.SetShootoutFirstTeam(first), 6900.0) + kicks(first, results))

class ShootoutTests {
    @Test fun kickBeforeChoosingFirstTeamIsIgnored() {
        val s = play(pkRules, twoHalves() + kick(Team.HOME, true, 7000.0, id = "k"))
        assertEquals(listOf("k"), s.ignoredCommandIds)
    }

    @Test fun firstTeamCannotChangeAfterFirstKick() {
        val s = play(pkRules, twoHalves() + listOf(cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0), kick(Team.HOME, true, 7000.0, id = "k0"),
            cmd(CommandKind.SetShootoutFirstTeam(Team.AWAY), 7001.0, id = "late")))
        assertEquals(listOf("late"), s.ignoredCommandIds)
    }

    @Test fun teamsMustAlternate() {
        val s = play(pkRules, twoHalves() + listOf(cmd(CommandKind.SetShootoutFirstTeam(Team.AWAY), 6900.0), kick(Team.HOME, true, 7000.0, id = "wrong")))
        assertEquals(listOf("wrong"), s.ignoredCommandIds)
        assertEquals(Team.AWAY, s.shootout.next)
    }

    @Test fun notDecidedWhileTrailingTeamCanStillCatchUp() {
        // home O O O, away X X → 3:0, away 3회 남음
        val s = shootout(Team.HOME, listOf(true, false, true, false, true))
        assertFalse(s.shootout.decided)
        assertEquals(Phase.Penalties, s.phase)
        assertEquals(Team.AWAY, s.shootout.next)
    }

    @Test fun endsAsSoonAsOneTeamCannotCatchUp() {
        val s = shootout(Team.HOME, listOf(true, false, true, false, true, false))
        assertEquals(TeamPair(3, 0), s.shootout.goals)
        assertTrue(s.shootout.decided)
        assertNull(s.shootout.next)
        assertEquals(completed, s.phase)
    }

    @Test fun suddenDeathAfterRegulationKicks() {
        val tiedFive = List(10) { true }
        val afterOne = shootout(Team.HOME, tiedFive + true)
        assertFalse(afterOne.shootout.decided)

        val done = shootout(Team.HOME, tiedFive + listOf(true, false))
        assertEquals(TeamPair(6, 5), done.shootout.goals)
        assertEquals(completed, done.phase)
    }

    @Test fun kicksPerTeamIsConfigurable() {
        // 3인 승부차기: home O O, away X X → 2:0, away 1회 남음 → 결정
        val s = shootout(Team.HOME, listOf(true, false, true, false), rules = RuleSet(penalties = true, penaltyKicksPerTeam = 3))
        assertEquals(completed, s.phase)
    }

    @Test fun shootoutGoalsDoNotChangeMatchScore() {
        val s = shootout(Team.HOME, listOf(true, false, true, false, true, false))
        assertEquals(TeamPair(0, 0), s.score)
    }

    @Test fun repeatKickerBeforeEveryoneHasKickedWarns() {
        val rules = RuleSet(penalties = true, playersPerTeam = 3, minimumPlayers = null)
        val s = play(rules, twoHalves() + listOf(
            cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0),
            kick(Team.HOME, true, 7000.0, number = 1, id = "h1"), kick(Team.AWAY, true, 7001.0, number = 7, id = "a1"),
            kick(Team.HOME, true, 7002.0, number = 2, id = "h2"), kick(Team.AWAY, true, 7003.0, number = 8, id = "a2"),
            kick(Team.HOME, true, 7004.0, number = 1, id = "repeat"),
        ))
        val repeated = assertNotNull(s.resolvedEvents.firstOrNull { it.event.id == "repeat" })
        assertEquals(listOf(EventWarning.REPEAT_PENALTY_KICKER), repeated.warnings)
        assertTrue(s.resolvedEvents.filter { it.event.id != "repeat" }.all { it.warnings.isEmpty() })
    }

    @Test fun repeatKickerAfterEveryoneHasKickedDoesNotWarn() {
        // 자격 있는 선수 3명이 모두 찬 뒤에는 처음 찬 선수가 다시 차도 된다 (경계: 찬 인원 == 자격 인원)
        val rules = RuleSet(penalties = true, playersPerTeam = 3, minimumPlayers = null)
        val s = play(rules, twoHalves() + listOf(
            cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0),
            kick(Team.HOME, true, 7000.0, number = 1, id = "h1"), kick(Team.AWAY, true, 7001.0, number = 7, id = "a1"),
            kick(Team.HOME, true, 7002.0, number = 2, id = "h2"), kick(Team.AWAY, true, 7003.0, number = 8, id = "a2"),
            kick(Team.HOME, true, 7004.0, number = 3, id = "h3"), kick(Team.AWAY, true, 7005.0, number = 9, id = "a3"),
            kick(Team.HOME, true, 7006.0, number = 1, id = "again"),
        ))
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(7, s.resolvedEvents.size)
        assertTrue(s.resolvedEvents.all { it.warnings.isEmpty() })
    }

    @Test fun onlyLastKickCanBeDeleted() {
        val base = twoHalves() + cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0) + kicks(Team.HOME, listOf(true, false))
        val middle = play(pkRules, base + cmd(CommandKind.DeleteEvent("k0"), 7100.0, id = "d"))
        assertEquals(listOf("d"), middle.ignoredCommandIds)

        val last = play(pkRules, base + cmd(CommandKind.DeleteEvent("k1"), 7100.0))
        assertEquals(TeamPair(1, 0), last.shootout.kicks)
        assertEquals(Team.AWAY, last.shootout.next)
    }

    @Test fun editingKickResultCanDecideShootout() {
        // home O X X, away X X X → 1:0 (미결정). k2·k4(home 2·3번째)를 성공으로 고치면 3:0 → 결정.
        val base = twoHalves() + cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0) +
            kicks(Team.HOME, listOf(true, false, false, false, false, false))
        assertEquals(Phase.Penalties, play(pkRules, base).phase)

        val edited = play(pkRules, base + listOf(
            cmd(CommandKind.EditEvent("k2", EventPayload.PenaltyKick(Team.HOME, null, true)), 7100.0),
            cmd(CommandKind.EditEvent("k4", EventPayload.PenaltyKick(Team.HOME, null, true)), 7101.0),
        ))
        assertEquals(TeamPair(3, 0), edited.shootout.goals)
        assertEquals(completed, edited.phase)
    }

    @Test fun editingKickTeamIsRejected() {
        val base = twoHalves() + cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6900.0) + kicks(Team.HOME, listOf(true))
        val s = play(pkRules, base + cmd(CommandKind.EditEvent("k0", EventPayload.PenaltyKick(Team.AWAY, null, true)), 7100.0, id = "e"))
        assertEquals(listOf("e"), s.ignoredCommandIds)
    }
}
