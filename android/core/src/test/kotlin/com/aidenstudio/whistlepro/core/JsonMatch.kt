package com.aidenstudio.whistlepro.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * 시나리오 기대값 비교 (shared/engine-scenarios/README.md).
 * 숫자는 값으로(2400 == 2400.0), 기대가 null이면 실제가 없거나 null, 배열·객체는 전체가 같아야 한다.
 */
fun jsonMatches(actual: JsonElement?, expected: JsonElement): Boolean = when {
    expected is JsonNull -> actual == null || actual is JsonNull
    actual == null || actual is JsonNull -> false
    expected is JsonPrimitive && actual is JsonPrimitive -> when {
        expected.isString || actual.isString -> expected.isString && actual.isString && expected.content == actual.content
        expected.booleanOrNull != null || actual.booleanOrNull != null -> expected.booleanOrNull == actual.booleanOrNull
        else -> expected.doubleOrNull != null && expected.doubleOrNull == actual.doubleOrNull
    }
    expected is JsonArray && actual is JsonArray ->
        expected.size == actual.size && expected.indices.all { jsonMatches(actual[it], expected[it]) }
    expected is JsonObject && actual is JsonObject ->
        expected.keys == actual.keys && expected.all { (key, value) -> jsonMatches(actual[key], value) }
    else -> false
}
