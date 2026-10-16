package com.aidenstudio.whistlepro.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
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

fun JsonObject.optionalDouble(key: String): Double? = present(key)?.primitive(key)?.let {
    if (it.isString) throw SerializationException("$key: 숫자가 아님")
    it.doubleOrNull ?: throw SerializationException("$key: 숫자가 아님")
}

fun JsonObject.double(key: String): Double = optionalDouble(key) ?: throw SerializationException("$key 없음")

/**
 * 64비트 정수 (Swift Int와 같은 범위). 1500.0처럼 소수부가 없는 실수도 받는다 (Swift JSONDecoder와 같음).
 * 정수 문법은 Double을 거치지 않고 정확히 읽는다. 범위를 넘으면 실패.
 */
fun JsonObject.optionalLong(key: String): Long? {
    val text = present(key)?.primitive(key)?.let {
        if (it.isString) throw SerializationException("$key: 숫자가 아님")
        it.content
    } ?: return null
    text.toLongOrNull()?.let { return it }
    val d = optionalDouble(key)!!
    if (d != floor(d) || d < -TWO_TO_63 || d >= TWO_TO_63) throw SerializationException("$key: 정수가 아님")
    return d.toLong()
}

private const val TWO_TO_63 = 9.223372036854775808e18

fun JsonObject.long(key: String): Long = optionalLong(key) ?: throw SerializationException("$key 없음")

/**
 * 정수를 Int 범위로 고정해 읽는다. Swift는 32비트를 넘는 값(예: seconds 3000000000)도 읽고 엔진이 범위 검사로 무시하므로,
 * 여기서도 로그 전체를 실패시키지 않고 Int.MIN_VALUE~Int.MAX_VALUE로 고정한다 (엔진 범위 밖이라 똑같이 무시된다).
 */
fun JsonObject.optionalInt(key: String): Int? =
    optionalLong(key)?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()

fun JsonObject.int(key: String): Int = optionalInt(key) ?: throw SerializationException("$key 없음")

fun JsonObject.optionalBoolean(key: String): Boolean? = present(key)?.primitive(key)?.let {
    if (it.isString) throw SerializationException("$key: 불리언이 아님")
    it.booleanOrNull ?: throw SerializationException("$key: 불리언이 아님")
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
