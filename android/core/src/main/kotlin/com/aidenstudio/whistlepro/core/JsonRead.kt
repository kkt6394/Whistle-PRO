package com.aidenstudio.whistlepro.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor

// Swift Decodable과 같은 규칙으로 JSON 객체를 읽는 도우미. 형식이 틀리면 SerializationException.
// :session 등 다른 모듈도 같은 규칙으로 읽도록 공개한다.

/** 키가 없거나 null이면 null (decodeIfPresent) */
fun JsonObject.present(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }

private fun JsonElement.primitive(key: String): JsonPrimitive =
    this as? JsonPrimitive ?: throw SerializationException("$key: 값이 아님")

fun JsonObject.optionalString(key: String): String? = present(key)?.primitive(key)?.let {
    if (it.isString) it.content else throw SerializationException("$key: 문자열이 아님")
}

fun JsonObject.string(key: String): String = optionalString(key) ?: throw SerializationException("$key 없음")

/** JSON 표준(RFC 8259) 숫자 문법. NaN·Infinity·12d·0x1p3·+5·01 같은 kotlinx 비표준 리터럴은 Swift처럼 거부한다. */
private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

/** 표준 문법의 숫자 리터럴 텍스트 */
private fun JsonPrimitive.numberText(key: String): String {
    if (isString || !jsonNumber.matches(content)) throw SerializationException("$key: 숫자가 아님")
    return content
}

/** 유한한 실수. 1e400처럼 넘치는 값은 Swift처럼 거부한다. 배열 원소 등 키 없는 값에도 쓴다. */
fun JsonElement.finiteDouble(key: String): Double {
    val d = primitive(key).numberText(key).toDouble()
    if (!d.isFinite()) throw SerializationException("$key: 유한한 숫자가 아님")
    return d
}

fun JsonObject.optionalDouble(key: String): Double? = present(key)?.finiteDouble(key)

fun JsonObject.double(key: String): Double = optionalDouble(key) ?: throw SerializationException("$key 없음")

/**
 * 64비트 정수 (Swift Int와 같은 범위). 1500.0처럼 소수부가 없는 실수도 받는다 (Swift JSONDecoder와 같음).
 * 정수 문법은 Double을 거치지 않고 정확히 읽는다. 범위를 넘으면 실패.
 */
fun JsonObject.optionalLong(key: String): Long? {
    val text = present(key)?.primitive(key)?.numberText(key) ?: return null
    text.toLongOrNull()?.let { return it }
    val d = optionalDouble(key)!!
    if (d != floor(d) || d < -TWO_TO_63 || d >= TWO_TO_63) throw SerializationException("$key: 정수가 아님")
    return d.toLong()
}

private const val TWO_TO_63 = 9.223372036854775808e18

fun JsonObject.long(key: String): Long = optionalLong(key) ?: throw SerializationException("$key 없음")

// 32비트(Int)로 읽는 도우미는 두지 않는다: Swift Int는 64비트라, 잘라 읽으면 규칙(periodLength 등)·선수 번호·예고 시간에서
// 결과가 달라졌다 (예: 번호 3000000000과 3000000001이 같은 선수가 됨). 정수 필드는 모두 optionalLong/long으로 읽는다.

/** true/false 리터럴만 (kotlinx booleanOrNull은 TRUE·True도 받는다) */
fun JsonObject.optionalBoolean(key: String): Boolean? = present(key)?.primitive(key)?.let {
    if (it.isString) throw SerializationException("$key: 불리언이 아님")
    when (it.content) {
        "true" -> true
        "false" -> false
        else -> throw SerializationException("$key: 불리언이 아님")
    }
}

fun JsonObject.boolean(key: String): Boolean = optionalBoolean(key) ?: throw SerializationException("$key 없음")

fun JsonObject.team(key: String): Team =
    string(key).let { Team.fromCode(it) ?: throw SerializationException("$key: 알 수 없는 팀 $it") }

fun JsonObject.obj(key: String): JsonObject =
    present(key) as? JsonObject ?: throw SerializationException("$key: 객체가 아님")

fun JsonObject.array(key: String): JsonArray =
    present(key) as? JsonArray ?: throw SerializationException("$key: 배열이 아님")

/** 배열 원소를 객체로 읽는다. */
fun JsonArray.objects(key: String): List<JsonObject> =
    map { it as? JsonObject ?: throw SerializationException("$key: 원소가 객체가 아님") }
