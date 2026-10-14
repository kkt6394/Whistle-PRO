package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutStoreTests {
    @Test fun oldSetupWithoutRecordsRouteDecodes() {
        val json = JsonObject(MatchSetup().toJson() - "recordsRoute")
        val setup = MatchSetup.fromJson(json)
        assertTrue(setup.recordsRoute)
        assertEquals(MatchSetup(), setup)
    }

    @Test fun recordsRouteRoundTrips() {
        val setup = MatchSetup(recordsRoute = false)
        assertEquals(setup, MatchSetup.fromJson(Json.parseToJsonElement(setup.toJson().toString()).jsonObject))
    }

    @Test fun noActiveWorkoutInitially() {
        assertNull(FileMatchLogStore(tempDirectory()).loadActiveWorkout())
    }

    @Test fun workoutLogMovesToArchiveWithItsMatch() {
        val store = FileMatchLogStore(tempDirectory())
        val record = MatchRecord(UUID.randomUUID(), 0.0, MatchSetup(), emptyList())
        val log = WorkoutLog(
            record.id,
            heartRate = listOf(WorkoutSample(1.0, 120.0)),
            route = listOf(RoutePoint(1.0, 37.5, 127.0, 3.0, 5.0)),
        )
        store.saveActive(record)
        store.saveActiveWorkout(log)
        assertEquals(log, store.loadActiveWorkout())

        store.finishActive(record)
        assertNull(store.loadActiveWorkout())
        assertEquals(log, store.archivedWorkout(record.id))
        assertEquals(listOf(record), store.archived())
    }

    @Test fun workoutOfAnotherMatchIsNotArchived() {
        val store = FileMatchLogStore(tempDirectory())
        val record = MatchRecord(UUID.randomUUID(), 0.0, MatchSetup(), emptyList())
        store.saveActive(record)
        store.saveActiveWorkout(WorkoutLog(UUID.randomUUID()))
        store.finishActive(record)
        assertNull(store.archivedWorkout(record.id))
        assertNull(store.loadActiveWorkout())
    }

    /** Kotlin 추가: Swift가 저장한 기록·운동 파일을 읽고, 다시 쓴 JSON이 값으로 같다 (M6-3 폰과 형식 공유) */
    @Test fun readsSwiftRecord() {
        val recordJson = resource("swift-record.json")
        val record = MatchRecord.fromJson(recordJson)
        assertEquals(UUID.fromString("6F9619FF-8B86-D011-B42D-00C04FC964FF"), record.id)
        assertEquals("FC 서울", record.setup.homeName)
        assertFalse(record.unlocked)
        assertFalse(record.setup.recordsRoute)
        assertEquals(1500, record.setup.rules.periodLength)
        val state = MatchEngine.reduce(record.setup.rules, record.commands)
        assertTrue(state.ignoredCommandIds.isEmpty())
        assertEquals(3, state.events.size)
        assertTrue(sameJson(recordJson, record.toJson()), "다시 쓴 기록 JSON이 Swift와 다름")

        val workoutJson = resource("swift-workout.json")
        val log = WorkoutLog.fromJson(workoutJson)
        assertEquals(record.id, log.matchID)
        assertTrue(sameJson(workoutJson, log.toJson()), "다시 쓴 운동 JSON이 Swift와 다름")
    }

    private fun resource(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.classLoader.getResource(name)!!.readText()).jsonObject

    /** 키 집합 + 값 비교 (숫자는 값으로) */
    private fun sameJson(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.all { (k, v) -> sameJson(v, b.getValue(k)) }
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
        a is JsonPrimitive && b is JsonPrimitive ->
            if (a.isString || b.isString) a.isString == b.isString && a.content == b.content
            else a.doubleOrNull?.let { it == b.doubleOrNull } ?: (a.content == b.content)
        else -> a == b
    }
}
