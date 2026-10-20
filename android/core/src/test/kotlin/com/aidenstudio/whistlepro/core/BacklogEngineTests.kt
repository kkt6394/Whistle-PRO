package com.aidenstudio.whistlepro.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val pkRules = RuleSet(penalties = true, sinBin = true)

private fun kick(team: Team, scored: Boolean, at: Double, id: String) =
    cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(team, null, scored)), at, id)

/** home O O O, away X X X → 3:0 결정 (마지막 킥 id "last") */
private fun decidedShootout(): List<Command> = twoHalves() + listOf(
    cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6400.0),
    kick(Team.HOME, true, 6410.0, "h1"), kick(Team.AWAY, false, 6420.0, "a1"),
    kick(Team.HOME, true, 6430.0, "h2"), kick(Team.AWAY, false, 6440.0, "a2"),
    kick(Team.HOME, true, 6450.0, "h3"), kick(Team.AWAY, false, 6460.0, "last"),
)

class BacklogEngineTests {
    @Test fun sinBinsEndWhenShootoutStarts() {
        val s = play(pkRules, listOf(cmd(start, 0.0), cmd(end, 2700.0), cmd(start, 3600.0),
            card(Team.HOME, 5, CardKind.SIN_BIN, 6200.0), cmd(end, 6300.0)))
        assertEquals(Phase.Penalties, s.phase)
        assertTrue(s.activeSinBins(6300.0).isEmpty())
        assertEquals(TeamPair(11L, 11L), s.playersOnField(6300.0))
    }

    @Test fun sinBinsEndWhenMatchFinishes() {
        val s = play(RuleSet(sinBin = true), listOf(cmd(start, 0.0), card(Team.HOME, 5, CardKind.SIN_BIN, 2600.0), cmd(end, 2700.0),
            cmd(start, 3600.0), cmd(CommandKind.AbandonMatch("x"), 3610.0)))
        assertTrue(s.activeSinBins(3610.0).isEmpty())
    }

    @Test fun shootoutLabelIsPK() {
        assertEquals("PK", play(pkRules, twoHalves()).currentPeriodLabel)
        assertEquals("PK", play(pkRules, decidedShootout()).currentPeriodLabel)
        assertEquals("2H", play(twoHalves()).currentPeriodLabel)
    }

    /** Swift와 같게: 승부차기 중(킥 없이) 중단해도 PK */
    @Test fun abandonedDuringShootoutIsPK() {
        assertEquals("PK", play(pkRules, twoHalves() + cmd(CommandKind.AbandonMatch("x"), 6400.0)).currentPeriodLabel)
        assertEquals("2H", play(twoHalves() + cmd(CommandKind.AbandonMatch("x"), 6400.0)).currentPeriodLabel)
    }

    @Test fun hugeTimestampsDoNotTrap() {
        val s = play(listOf(cmd(start, 0.0), cmd(toggle, 1e300)))
        s.snapshot(Double.POSITIVE_INFINITY, 300, 300)
        s.snapshot(1e300, 300, 300)
        s.snapshot(Double.NaN, 300, 300)
        assertEquals(0L, wholeSeconds(Double.POSITIVE_INFINITY))
        assertEquals(1_000_000_000_000L, wholeSeconds(1e300))
        assertEquals(-6L, wholeSeconds(-5.5))
    }

    /** validate() 범위 밖 규칙에서도 Swift(64비트 Int)와 같은 값: 기간 offset 합이 Int를 넘는다 */
    @Test fun hugePeriodLengthOffsetsDoNotOverflow() {
        val rules = RuleSet(periodCount = 4, periodLength = 2_000_000_000, quarterBreakLength = 0)
        val s = play(rules, listOf(cmd(start, 0.0), cmd(end, 10.0), cmd(start, 20.0), cmd(end, 30.0), cmd(start, 40.0),
            goal(Team.HOME, 50.0, id = "g")))
        assertEquals(4_000_000_000L, s.periods.last().offset)
        assertEquals(4_000_000_010L, s.events.single().time.matchSecond)
        assertEquals(4_000_000_010L, s.clock(50.0)?.matchSecond)
    }

    /** 32비트를 넘는 규칙·예고 값도 Swift(64비트 Int)처럼 그대로 읽고 계산한다 (Int로 고정하면 기간 끝·예고 시각이 달라졌다) */
    @Test fun rulesAndWarningBeyond32BitsKeepExactValues() {
        val json = Json.parseToJsonElement("""{"periodLength": 4000000000, "substitutionLimit": -3000000000}""").jsonObject
        val rules = RuleSet.fromJson(json)
        assertEquals("4000000000", rules.value(RuleSet.Field.periodLength))
        assertEquals(-3_000_000_000L, rules.substitutionLimit)
        val s = play(rules, listOf(cmd(start, 0.0)))
        assertEquals(listOf(
            MatchAlert(AlertKind.WARNING_BEFORE_END, 1e9),
            MatchAlert(AlertKind.PERIOD_END, 4e9),
        ), s.alerts(0.0, 3_000_000_000L, 300))
    }

    /** 교체 한도 + 연장 추가 교체가 Int를 넘어도 경고가 붙지 않는다 (Swift와 같음) */
    @Test fun hugeSubstitutionLimitDoesNotOverflow() {
        val rules = RuleSet(extraTime = true, substitutionLimit = Int.MAX_VALUE.toLong(), extraTimeExtraSubs = 1)
        val s = play(rules, twoHalves() + listOf(cmd(start, 6400.0), sub(Team.HOME, 6500.0)))
        assertTrue(s.resolvedEvents.single().warnings.isEmpty())
    }

    @Test fun deletingDecisiveKickReopensShootout() {
        val reopened = play(pkRules, decidedShootout() + cmd(CommandKind.DeleteEvent("last"), 6500.0))
        assertEquals(Phase.Penalties, reopened.phase)
        assertEquals(Team.AWAY, reopened.shootout.next)
    }

    @Test fun editingDecisiveKickAfterFinishReopensShootout() {
        // h3를 실패로 고치면 2:0, away 2회 남음 → 미정
        val edited = play(pkRules, decidedShootout() +
            cmd(CommandKind.EditEvent("h3", EventPayload.PenaltyKick(Team.HOME, null, false)), 6500.0))
        assertEquals(Phase.Penalties, edited.phase)
        assertEquals(TeamPair(2, 0), edited.shootout.goals)
    }

    @Test fun kickNumberCanBeFixedAfterFinish() {
        val s = play(pkRules, decidedShootout() +
            cmd(CommandKind.EditEvent("h1", EventPayload.PenaltyKick(Team.HOME, 9, true)), 6500.0, id = "e"))
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(completed, s.phase)
    }
}
