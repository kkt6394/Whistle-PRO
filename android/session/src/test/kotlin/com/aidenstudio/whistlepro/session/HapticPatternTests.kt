package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.AlertKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticPatternTests {
    private val kinds = AlertKind.entries

    @Test fun everyAlertHasDistinctPattern() {
        assertEquals(kinds.size, kinds.map { it.hapticPattern }.toSet().size)
    }

    @Test fun periodEndIsTheLongestPattern() {
        val longest = kinds.maxOf { it.hapticPattern.repeats }
        assertEquals(longest, AlertKind.PERIOD_END.hapticPattern.repeats)
    }

    /** 실기기(워치 SE)에서 directionUp 한 번은 손목으로 느끼지 못했다. 예고도 강한 진동이어야 한다. */
    @Test fun warningsUseStrongTones() {
        val strong = setOf(HapticTone.NOTIFICATION, HapticTone.RETRY)
        assertTrue(AlertKind.WARNING_BEFORE_END.hapticPattern.tone in strong)
        assertTrue(AlertKind.BREAK_ENDING_SOON.hapticPattern.tone in strong)
    }

    /** 실기기(워치 SE)에서 0.4초 간격 notification 두 번은 한 번으로 느껴졌다 (앞 진동이 끝나기 전이라 무시됨). */
    @Test fun repeatedHapticsLeaveRoomForEachTap() {
        for (kind in kinds) {
            if (kind.hapticPattern.repeats > 1) assertTrue(kind.hapticPattern.interval >= 0.6, "$kind")
        }
    }
}
