package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.AlertKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 동전 던지기: 결과는 앞면/뒷면 둘 뿐이고 어느 쪽으로도 치우치지 않는다. */
class CoinTossTests {
    @Test fun tossIsFair() {
        val random = Random(42)
        val heads = (0 until 1000).count { CoinSide.toss(random) == CoinSide.HEADS }
        assertTrue(heads in 400..600, "앞면 $heads/1000")
    }

    @Test fun sameSeedGivesSameResult() {
        val a = Random(7)
        val b = Random(7)
        assertEquals((0 until 20).map { CoinSide.toss(a) }, (0 until 20).map { CoinSide.toss(b) })
    }

    /** 결과 진동은 한 번, 경기 알림과 겹치지 않는 종류 */
    @Test fun resultHapticIsSingleAndDistinctFromAlerts() {
        val alerts = listOf(
            AlertKind.WARNING_BEFORE_END, AlertKind.PERIOD_END, AlertKind.BREAK_ENDING_SOON,
            AlertKind.BREAK_END, AlertKind.SIN_BIN_END,
        )
        assertEquals(1, CoinSide.RESULT_HAPTIC.repeats)
        assertFalse(alerts.map { it.hapticPattern }.contains(CoinSide.RESULT_HAPTIC))
    }
}
