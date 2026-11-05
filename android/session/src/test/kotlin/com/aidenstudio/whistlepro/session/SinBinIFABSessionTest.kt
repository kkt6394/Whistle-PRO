package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.AlertKind
import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 일시퇴장 IFAB 세션 값 (Swift SinBinIFABSessionTests와 같은 케이스) */
class SinBinIFABSessionTest {
    private val rules = RuleSet(sinBin = true, sinBinLength = 600)
    private val starters = (1L..11L).toList()

    private fun setup() =
        MatchSetup(homeRoster = (1L..18L).map { Player(it, "P$it", if (it in starters) true else null) })

    private fun sinBin(at: Double) = CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 7, CardKind.SIN_BIN)) to at

    /** 홈 7번 두 번째 일시퇴장 (100·800, 1400에 끝남) 뒤 추가 명령 */
    private fun state(vararg extra: Pair<CommandKind, Double>) =
        makeState(rules, listOf(CommandKind.StartPeriod to 0.0, sinBin(100.0), sinBin(800.0)) + extra)

    @Test fun onPitchExcludesBarred() {
        val s = state()
        val pitch = s.onPitchNumbers(Team.HOME, setup(), 1500.0)
        assertTrue(pitch?.contains(7) == false)
        assertEquals(10, pitch?.size)
        assertTrue(s.onPitchNumbers(Team.HOME, setup(), 1000.0)?.contains(7) == true)
    }

    @Test fun barredFilledSplit() {
        val s = state(CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 12)) to 1500.0)
        assertEquals(setOf(7L), s.barredNumbers(Team.HOME, 1600.0, true))
        assertTrue(s.barredNumbers(Team.HOME, 1600.0, false).isEmpty())
        assertEquals(setOf(7L), s.barredNumbers(Team.HOME, 1600.0, null))
        val before = state()
        assertEquals(setOf(7L), before.barredNumbers(Team.HOME, 1500.0, false))
        assertTrue(before.barredNumbers(Team.AWAY, 1500.0, null).isEmpty())
    }

    @Test fun sinBinnedNumbersDuringSinBin() {
        val s = state()
        assertEquals(setOf(7L), s.sinBinnedNumbers(Team.HOME, 900.0))
        assertTrue(s.sinBinnedNumbers(Team.HOME, 1500.0).isEmpty())
        assertTrue(s.sinBinnedNumbers(Team.AWAY, 900.0).isEmpty())
    }

    @Test fun secondBadgeFlag() {
        val first = MatchDisplay(makeState(rules, listOf(CommandKind.StartPeriod to 0.0, sinBin(100.0))), 200.0, ClockDisplay.ELAPSED)
        assertEquals(listOf(false), first.sinBins.map { it.second })
        val second = MatchDisplay(state(), 900.0, ClockDisplay.ELAPSED)
        assertEquals(listOf(true), second.sinBins.map { it.second })
    }

    @Test fun secondSinBinEndHaptic() {
        assertEquals(HapticPattern(HapticTone.RETRY, 3, 0.6), AlertKind.SECOND_SIN_BIN_END.hapticPattern)
        assertEquals(2, AlertKind.SIN_BIN_END.hapticPattern.repeats)
    }

    @Test fun reportLineMarksSecond() {
        val record = makeRecord(MatchSetup(rules = rules), listOf(CommandKind.StartPeriod to 0.0, sinBin(100.0), sinBin(800.0)))
        val report = MatchReport(MatchBundle(record, null))
        assertEquals(listOf(false, true), report.discipline.map { it.secondSinBin })
    }

    @Test fun reportLineBarredOnlyWhenSecondSinBinFinishedWithoutSendOff() {
        // 두 번째가 1400에 끝난 뒤 1500에 기간 종료 -> 출전 금지
        val served = MatchReport(MatchBundle(makeRecord(MatchSetup(rules = rules),
            listOf(CommandKind.StartPeriod to 0.0, sinBin(100.0), sinBin(800.0), CommandKind.EndPeriod to 1500.0)), null))
        assertEquals(listOf(false, true), served.discipline.map { it.secondSinBin })
        assertEquals(listOf(false, true), served.discipline.map { it.barred })
        // 두 번째 중(1000) 퇴장 -> "2번째"만, 출전 금지 아님
        val red = CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 7, CardKind.RED)) to 1000.0
        val sentOff = MatchReport(MatchBundle(makeRecord(MatchSetup(rules = rules),
            listOf(CommandKind.StartPeriod to 0.0, sinBin(100.0), sinBin(800.0), red, CommandKind.EndPeriod to 1500.0)), null))
        assertEquals(listOf(false), sentOff.discipline.filter { it.secondSinBin }.map { it.barred })
        assertTrue(sentOff.discipline.none { it.barred })
    }
}
