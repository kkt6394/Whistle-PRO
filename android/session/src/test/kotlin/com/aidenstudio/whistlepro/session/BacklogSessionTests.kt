package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Swift BacklogSessionTests 중 distanceSurvivesWorkoutRestart·twoSampleSpikeIsNotASprint는 Task 4(운동 통계),
// preExtraTimeClockSurvivesHugeNow는 Task 3(MatchDisplay)에서 이 파일에 더한다.
// deletedMatchIsNotStoredAgain(MatchLibrary)·unsupportedPayloadRoundTrips(SyncPayload)·
// reportSurvivesHugeTimestamps(MatchReport)는 폰 쪽이라 M6-3에서 옮긴다.
class BacklogSessionTests {
    @Test fun subscriptionProductsAreShared() {
        assertEquals(listOf("com.aidenstudio.whistlepro.pro.monthly", "com.aidenstudio.whistlepro.pro.yearly"), SubscriptionProducts.ids)
        assertTrue(SubscriptionProducts.contains("com.aidenstudio.whistlepro.pro.yearly"))
        assertFalse(SubscriptionProducts.contains("com.aidenstudio.whistlepro.pro.lifetime"))
    }
}

/** 최종 리뷰: 화면 표시 경로도 손상된 큰 타임스탬프에서 죽지 않아야 한다 (reportSurvivesHugeTimestamps는 M6-3) */
class HugeTimestampSessionTests {
    @Test fun preExtraTimeClockSurvivesHugeNow() {
        val s = makeState(RuleSet(extraTime = true), listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
        ))
        assertEquals(MatchDisplay.Screen.PRE_EXTRA_TIME, MatchDisplay(s, 1e300, ClockDisplay.ELAPSED).screen)
        assertEquals("00:00", MatchDisplay(s, Double.POSITIVE_INFINITY, ClockDisplay.ELAPSED).intervalClock)
    }
}
