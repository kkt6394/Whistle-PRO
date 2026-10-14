package com.aidenstudio.whistlepro.session

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
