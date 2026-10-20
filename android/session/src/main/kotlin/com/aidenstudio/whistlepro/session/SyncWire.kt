package com.aidenstudio.whistlepro.session

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.UUID
import kotlin.io.encoding.Base64

/**
 * Wear Data Layer 경로와 capability 이름 (두 앱이 같은 문자열을 써야 닿는다).
 * WatchConnectivity의 applicationContext·transferFile·sendMessage·transferUserInfo를 이 경로들로 옮긴다.
 */
object SyncPaths {
    /** 폰 → 워치 컨텍스트(프리셋·구독 상태) DataItem */
    const val CONTEXT = "/whistlepro/context"
    /** 워치 → 폰 경기 DataItem: /whistlepro/match/<경기 id>/<전송 이름> */
    const val MATCH_PREFIX = "/whistlepro/match/"
    /** 폰 → 워치 확인·지원 안 함 메시지 */
    const val ACK = "/whistlepro/ack"
    /** 메시지가 실패했을 때 남기는 확인 DataItem: /whistlepro/ack/<경기 id> */
    const val ACK_PREFIX = "/whistlepro/ack/"
    /** 워치 → 폰 받았는지 묻기, 답은 RECEIPT_REPLY */
    const val RECEIPT_QUERY = "/whistlepro/receipt-query"
    const val RECEIPT_REPLY = "/whistlepro/receipt-reply"
    /** 워치 → 폰 컨텍스트 요청 메시지 (닿지 않으면 같은 경로의 DataItem으로 남긴다) */
    const val CONTEXT_REQUEST = "/whistlepro/context-request"
    const val CAPABILITY_WEAR = "whistlepro_wear"
    const val CAPABILITY_MOBILE = "whistlepro_mobile"

    /** 경기 DataItem 경로. transfer는 전송마다 다른 이름 (같은 경로면 끝난 이전 전송의 정리가 새 전송을 지울 수 있다) */
    fun match(matchID: UUID, transfer: String): String = "$MATCH_PREFIX${matchID.swiftString}/$transfer"

    /** 경기 DataItem 경로의 경기 id. 경기 경로가 아니거나 id 형식이 틀리면 null */
    fun matchID(path: String): UUID? {
        if (!path.startsWith(MATCH_PREFIX)) return null
        return parseUuid(path.removePrefix(MATCH_PREFIX).substringBefore('/'))
    }
}

/**
 * SyncPayload 메시지(키와 값의 맵)를 Data Layer에 싣는 JSON 바이트로 바꾼다.
 * 값은 String·Boolean·Double·List<String>·ByteArray·null만 쓴다. ByteArray는 `{"$b64":"…"}` 객체로 싣는다.
 */
object SyncWire {
    private const val BYTES_KEY = "\$b64"
    /** 물음과 답을 짝짓는 요청 ID 키 (MessageClient는 답 콜백이 없어 답 메시지에 같은 ID를 싣는다) */
    private const val REQUEST_ID_KEY = "requestID"

    fun encode(message: Map<String, Any?>): ByteArray =
        JsonObject(message.mapValues { (key, value) -> element(key, value) }).toString().toByteArray(Charsets.UTF_8)

    /** 읽을 수 없는 바이트(다른 형식·깨진 UTF-8·객체가 아닌 JSON)는 빈 맵. 모르는 형식의 값은 빼고 읽는다. */
    fun decode(bytes: ByteArray): Map<String, Any?> {
        val json = runCatching { parseJson(bytes) as? JsonObject }.getOrNull() ?: return emptyMap()
        val message = LinkedHashMap<String, Any?>()
        for ((key, element) in json) {
            val value = value(element) ?: continue
            message[key] = value.value
        }
        return message
    }

    /** 답을 기다리는 물음: 메시지에 요청 ID를 더해 싣는다 */
    fun request(id: String, message: Map<String, Any?>): ByteArray = encode(message + (REQUEST_ID_KEY to id))

    /** 물음·답의 요청 ID (없거나 읽을 수 없으면 null) */
    fun requestId(bytes: ByteArray): String? = decode(bytes)[REQUEST_ID_KEY] as? String

    private fun element(key: String, value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Double -> {
            require(value.isFinite()) { "$key: 유한하지 않은 숫자 $value" }
            JsonPrimitive(value)
        }
        is ByteArray -> JsonObject(mapOf(BYTES_KEY to JsonPrimitive(Base64.encode(value))))
        is List<*> -> JsonArray(value.map { item ->
            require(item is String) { "$key: 문자열이 아닌 목록 항목" }
            JsonPrimitive(item)
        })
        else -> throw IllegalArgumentException("$key: 싣지 않는 값 형식 ${value::class.simpleName}")
    }

    /** 읽은 값을 감싼다 (null 값과 "읽을 수 없음"을 가르려고). 모르는 형식이면 null */
    private class Value(val value: Any?)

    private fun value(element: JsonElement): Value? = when (element) {
        JsonNull -> Value(null)
        is JsonPrimitive -> when {
            element.isString -> Value(element.content)
            element.booleanOrNull != null -> Value(element.booleanOrNull)
            else -> element.doubleOrNull?.let(::Value)
        }
        is JsonArray -> element
            .takeIf { array -> array.all { it is JsonPrimitive && it.isString } }
            ?.let { array -> Value(array.map { (it as JsonPrimitive).content }) }
        is JsonObject -> (element[BYTES_KEY] as? JsonPrimitive)
            ?.takeIf { element.size == 1 && it.isString }
            ?.let { runCatching { Base64.decode(it.content) }.getOrNull() }
            ?.let(::Value)
    }
}
