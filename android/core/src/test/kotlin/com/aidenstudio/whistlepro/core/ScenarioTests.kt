package com.aidenstudio.whistlepro.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object Scenarios {
    val directory = File(System.getProperty("scenarioDir") ?: error("scenarioDir 시스템 속성 없음 (Gradle test 태스크가 설정)"))
    val files: List<File> get() = directory.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
}

/** 생략하거나 null이면 300. Swift 러너처럼 정수로 읽어 300.0도 받는다. */
internal fun scenarioWarningBeforeEnd(raw: JsonObject): Int = raw.optionalInt("warningBeforeEnd") ?: 300

class ScenarioTests {
    @Test fun scenarioDirectoryIsFound() {
        assertTrue(Scenarios.files.size >= 10, "시나리오 디렉터리: ${Scenarios.directory}")
    }

    @TestFactory fun scenario(): List<DynamicTest> = Scenarios.files.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val raw = Json.parseToJsonElement(file.readText()).jsonObject
            val name = raw["name"]?.jsonPrimitive?.content
            val rules = RuleSet.fromJson(raw["config"]?.jsonObject ?: JsonObject(emptyMap()))
            val warningBeforeEnd = scenarioWarningBeforeEnd(raw)
            // id를 적지 않은 명령에만 "#<인덱스>"를 붙인다 (디코더는 빈 id 대신 UUID를 만든다)
            val commands = raw.getValue("commands").jsonArray.mapIndexed { i, element ->
                val json = element.jsonObject
                val command = Command.fromJson(json)
                if ("id" in json) command else command.copy(id = "#$i")
            }
            for (expected in raw.getValue("expect").jsonArray.map { it.jsonObject }) {
                val at = expected.getValue("at").jsonPrimitive.double
                val state = MatchEngine.reduce(rules, commands.filter { it.timestamp <= at })
                val actual = state.snapshot(at, warningBeforeEnd).toJson()
                for ((key, value) in expected) {
                    if (key == "at") continue
                    assertTrue(jsonMatches(actual[key], value), "$name @$at $key: 실제 ${actual[key] ?: JsonNull}, 기대 $value")
                }
            }
        }
    }

    @Test fun snapshotEncodesTeamPairsAsArraysAndOmitsNil() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 10.0)))
        val json = s.snapshot(20.0, 300).toJson()
        assertEquals(JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(0))), json["score"])
        assertEquals(JsonPrimitive("period"), json["phase"])
        assertFalse("finishReason" in json)
    }

    /** Kotlin 추가: Swift Encodable처럼 이벤트·일시 퇴장의 null 등번호 키를 생략한다 (중첩 객체는 키 집합까지 비교하므로) */
    @Test fun snapshotOmitsNilNumbersInNestedObjects() {
        val s = play(RuleSet(sinBin = true), listOf(cmd(start, 0.0), card(Team.HOME, null, CardKind.SIN_BIN, 10.0, id = "s")))
        val json = s.snapshot(20.0, 300).toJson()
        val event = json.getValue("events").jsonArray[0].jsonObject
        assertEquals(setOf("id", "type", "team", "matchSecond", "warnings"), event.keys)
        val sinBin = json.getValue("sinBins").jsonArray[0].jsonObject
        assertEquals(setOf("eventId", "team", "remaining"), sinBin.keys)
    }

    /** Swift 러너(Int?, 생략·null이면 300)와 같게: null·300.0도 읽는다 */
    @Test fun warningBeforeEndIsReadLikeSwift() {
        fun read(text: String) = scenarioWarningBeforeEnd(Json.parseToJsonElement(text).jsonObject)
        assertEquals(300, read("{}"))
        assertEquals(300, read("""{"warningBeforeEnd": null}"""))
        assertEquals(300, read("""{"warningBeforeEnd": 300.0}"""))
        assertEquals(120, read("""{"warningBeforeEnd": 120}"""))
    }

    /** Kotlin 추가: 러너의 숫자 비교 규칙 */
    @Test fun jsonNumbersCompareByValue() {
        fun parse(text: String) = Json.parseToJsonElement(text)
        assertTrue(jsonMatches(parse("2400.0"), parse("2400")))
        assertTrue(jsonMatches(parse("[1.0, 0]"), parse("[1, 0]")))
        assertFalse(jsonMatches(parse("\"1\""), parse("1")))
        assertFalse(jsonMatches(parse("1"), parse("true")))
        assertFalse(jsonMatches(parse("""{"a": 1, "b": 2}"""), parse("""{"a": 1}""")))
    }

    /** Kotlin 추가: 기대값 null은 키가 없거나 null일 때만 통과 */
    @Test fun nullExpectationMatchesMissingKey() {
        assertTrue(jsonMatches(null, JsonNull))
        assertTrue(jsonMatches(JsonNull, JsonNull))
        assertFalse(jsonMatches(JsonPrimitive("away"), JsonNull))
        assertFalse(jsonMatches(null, JsonPrimitive("away")))
    }
}
