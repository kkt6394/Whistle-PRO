package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.RuleSet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Swift BacklogSessionTests 중 deletedMatchIsNotStoredAgain(MatchLibrary)·unsupportedPayloadRoundTrips(SyncPayload)·
// reportSurvivesHugeTimestamps(MatchReport)는 폰 쪽이라 M6-3에서 옮긴다.
private fun point(t: Double, kmh: Double) = RoutePoint(t, 0.0, 0.0, kmh / 3.6, 5.0)

class BacklogSessionTests {
    /** restarts 원소도 표준 숫자만 (Swift [Double] 디코딩과 같게 NaN·Infinity·1e400 거부) */
    @Test fun workoutRestartsRejectNonStandardNumbers() {
        val id = UUID.randomUUID()
        fun decode(restarts: String) = WorkoutLog.fromJson(kotlinx.serialization.json.Json.parseToJsonElement(
            """{"matchID": "${id.toString().uppercase()}", "heartRate": [], "distance": [], "energy": [], "route": [], "restarts": $restarts}""",
        ) as JsonObject)
        assertEquals(listOf(1.5, 1000.0), decode("[1.5, 1e3]").restarts)
        assertEquals(null, decode("null").restarts)
        for (bad in listOf("[NaN]", "[Infinity]", "[1e400]", "[12d]", "[\"1\"]", "1")) {
            kotlin.test.assertFailsWith<kotlinx.serialization.SerializationException>(bad) { decode(bad) }
        }
    }

    @Test fun distanceSurvivesWorkoutRestart() {
        // 1000초에 2000m, 운동 세션 재시작으로 1500초에 100m부터 다시, 2700초에 1500m
        val log = WorkoutLog(UUID.randomUUID(), distance = listOf(
            WorkoutSample(0.0, 0.0), WorkoutSample(1000.0, 2000.0), WorkoutSample(1500.0, 100.0), WorkoutSample(2700.0, 1500.0),
        ), restarts = listOf(1200.0))
        val stats = WorkoutAnalysis.perPeriod(log, makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0)), 3000.0)
        assertEquals(listOf(3500.0), stats.map { it.distance })
    }

    /** Swift와 같게: 재시작 뒤 첫 값이 더 커도 0부터 센다, 재시작 표시 없이 줄면 늘어난 양 없음 */
    @Test fun restartWithLargerFirstValueCountsFully() {
        val log = WorkoutLog(UUID.randomUUID(), distance = listOf(
            WorkoutSample(0.0, 0.0), WorkoutSample(500.0, 300.0), WorkoutSample(1500.0, 400.0),
        ), restarts = listOf(600.0))
        val stats = WorkoutAnalysis.perPeriod(log, makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0)), 3000.0)
        assertEquals(listOf(700.0), stats.map { it.distance })
    }

    @Test fun dipWithoutRestartIsNotCountedAgain() {
        val log = WorkoutLog(UUID.randomUUID(), distance = listOf(
            WorkoutSample(0.0, 0.0), WorkoutSample(100.0, 500.0), WorkoutSample(110.0, 480.0), WorkoutSample(200.0, 700.0),
        ))
        val stats = WorkoutAnalysis.perPeriod(log, makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0)), 3000.0)
        assertEquals(listOf(700.0), stats.map { it.distance })
    }

    /** Swift와 같게: 새 기록은 빈 배열도 쓴다, 키가 없는 예전 기록은 값이 줄면 재시작으로 본다 */
    @Test fun newLogsAlwaysWriteRestarts() {
        val log = WorkoutLog(UUID.randomUUID())
        assertEquals(JsonArray(emptyList()), log.toJson()["restarts"])
        val restarted = log.copy(restarts = listOf(10.0))
        assertEquals(listOf(10.0), WorkoutLog.fromJson(restarted.toJson()).restarts)
    }

    @Test fun legacyLogKeepsDecreaseHeuristic() {
        val json = WorkoutLog(UUID.randomUUID(), distance = listOf(
            WorkoutSample(0.0, 0.0), WorkoutSample(1000.0, 2000.0), WorkoutSample(1500.0, 100.0), WorkoutSample(2700.0, 1500.0),
        )).toJson().let { JsonObject(it - "restarts") }
        val log = WorkoutLog.fromJson(json)
        assertEquals(null, log.restarts)
        val stats = WorkoutAnalysis.perPeriod(log, makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0)), 3000.0)
        assertEquals(listOf(3500.0), stats.map { it.distance })
    }

    @Test fun twoSampleSpikeIsNotASprint() {
        assertEquals(0, WorkoutAnalysis.sprintCount(listOf(point(0.0, 10.0), point(1.0, 30.0), point(2.0, 30.0), point(3.0, 10.0))))
        assertEquals(1, WorkoutAnalysis.sprintCount(listOf(point(0.0, 10.0), point(1.0, 30.0), point(2.0, 30.0), point(3.0, 30.0))))
    }

    @Test fun subscriptionProductsAreShared() {
        assertEquals(listOf("com.aidenstudio.whistlepro.pro.yearly", "com.aidenstudio.whistlepro.pro.monthly"), SubscriptionProducts.ids)
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
