package com.aidenstudio.whistlepro.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// Swift Decodable과 같은 규칙으로 JSON 객체를 읽는 도우미. 형식이 틀리면 SerializationException.

/** 키가 없거나 null이면 null (decodeIfPresent) */
internal fun JsonObject.present(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }

private fun JsonElement.primitive(key: String): JsonPrimitive =
    this as? JsonPrimitive ?: throw SerializationException("$key: 값이 아님")

internal fun JsonObject.optionalString(key: String): String? = present(key)?.primitive(key)?.let {
    if (it.isString) it.content else throw SerializationException("$key: 문자열이 아님")
}

internal fun JsonObject.string(key: String): String = optionalString(key) ?: throw SerializationException("$key 없음")

internal fun JsonObject.optionalDouble(key: String): Double? = present(key)?.primitive(key)?.let {
    if (it.isString) throw SerializationException("$key: 숫자가 아님")
    it.doubleOrNull ?: throw SerializationException("$key: 숫자가 아님")
}

internal fun JsonObject.double(key: String): Double = optionalDouble(key) ?: throw SerializationException("$key 없음")

/** 정수. 1500.0처럼 소수부가 없는 실수도 받는다 (Swift JSONDecoder와 같음). */
internal fun JsonObject.optionalInt(key: String): Int? = optionalDouble(key)?.let {
    val i = it.toInt()
    if (i.toDouble() == it) i else throw SerializationException("$key: 정수가 아님")
}

internal fun JsonObject.int(key: String): Int = optionalInt(key) ?: throw SerializationException("$key 없음")

internal fun JsonObject.optionalBoolean(key: String): Boolean? = present(key)?.primitive(key)?.let {
    if (it.isString) throw SerializationException("$key: 불리언이 아님")
    it.booleanOrNull ?: throw SerializationException("$key: 불리언이 아님")
}

internal fun JsonObject.boolean(key: String): Boolean = optionalBoolean(key) ?: throw SerializationException("$key 없음")

internal fun JsonObject.team(key: String): Team =
    string(key).let { Team.fromCode(it) ?: throw SerializationException("$key: 알 수 없는 팀 $it") }

internal fun JsonObject.obj(key: String): JsonObject =
    present(key) as? JsonObject ?: throw SerializationException("$key: 객체가 아님")
