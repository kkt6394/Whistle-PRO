package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnPitchTests {
    private fun setup(starters: List<Long> = listOf(1, 4, 5, 7, 9, 10)) =
        MatchSetup(homeRoster = (1L..18L).map { Player(it, "P$it", if (it in starters) true else null) })

    private fun state(vararg events: EventPayload): MatchState =
        makeState(listOf<Pair<CommandKind, Double>>(CommandKind.StartPeriod to 0.0) +
            events.mapIndexed { i, e -> CommandKind.RecordEvent(e) to (100.0 + i * 10) })

    private fun sub(out: Long, into: Long) = EventPayload.Substitution(Team.HOME, out, into)

    @Test fun onPitchIsNilWithoutStarters() {
        assertNull(state().onPitchNumbers(Team.HOME, setup(emptyList()), 200.0))
    }

    @Test fun onPitchStartsWithStartersAndFollowsSubstitutions() {
        assertEquals(setOf(1L, 4, 5, 9, 10, 17), state(sub(7, 17)).onPitchNumbers(Team.HOME, setup(), 200.0))
    }

    @Test fun sendOffWinsOverSubstitutionBack() {
        val s = state(EventPayload.Card(Team.HOME, 5, CardKind.RED), sub(17, 5))
        assertTrue(s.onPitchNumbers(Team.HOME, setup(), 200.0)?.contains(5) == false)
    }

    @Test fun sinBinStaysOnPitch() {
        val s = state(EventPayload.Card(Team.HOME, 4, CardKind.SIN_BIN))
        assertTrue(s.onPitchNumbers(Team.HOME, setup(), 200.0)?.contains(4) == true)
    }

    @Test fun numberOutsideRosterMovesInAndOut() {
        val pitch = state(sub(7, 23)).onPitchNumbers(Team.HOME, setup(), 200.0)
        assertTrue(pitch?.contains(23) == true && pitch.contains(7) == false)
    }

    @Test fun excludingTheEditedSubstitution() {
        val s = state(sub(7, 17))
        val pitch = s.onPitchNumbers(Team.HOME, setup(), 200.0, s.events[0].id)
        assertTrue(pitch?.contains(7) == true && pitch.contains(17) == false)
    }

    @Test fun threeStartersStillWork() {
        val s = state(sub(1, 12))
        assertEquals(setOf(2L, 3, 12), s.onPitchNumbers(Team.HOME, setup(listOf(1, 2, 3)), 200.0))
    }

    /** 경고 1장 선수 (명단 칩의 노란 카드, 2026-11-04 critique): 두 번째 경고·레드로 퇴장하면 빠진다 */
    @Test fun cautionedNumbersAreSingleYellowsStillPlaying() {
        fun card(team: Team, n: Long?, kind: CardKind) = EventPayload.Card(team, n, kind)
        val s = state(
            card(Team.HOME, 4, CardKind.YELLOW),
            card(Team.HOME, 9, CardKind.YELLOW), card(Team.HOME, 9, CardKind.YELLOW),
            card(Team.HOME, 10, CardKind.YELLOW), card(Team.HOME, 10, CardKind.RED),
            card(Team.AWAY, 7, CardKind.YELLOW),
            card(Team.HOME, 5, CardKind.SIN_BIN),
            card(Team.HOME, null, CardKind.YELLOW),
        )
        assertEquals(setOf(4L), s.cautionedNumbers(Team.HOME))
        assertEquals(setOf(7L), s.cautionedNumbers(Team.AWAY))
    }

    @Test fun cautionedNumbersSkipTheEditedCard() {
        val s = state(EventPayload.Card(Team.HOME, 4, CardKind.YELLOW))
        assertTrue(s.cautionedNumbers(Team.HOME, s.events[0].id).isEmpty())
    }
}
