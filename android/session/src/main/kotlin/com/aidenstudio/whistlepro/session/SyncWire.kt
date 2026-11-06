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
    /** 워치 → 폰 물리 입력 상태 DataItem (덮어쓰기, [WatchInputStatus]). 폰 결제 화면 카드가 본다 (2026-11-06) */
    const val INPUT_STATUS = "/whistlepro/input-status"
    const val CAPABILITY_WEAR = "whistlepro_wear"
    const val CAPABILITY_MOBILE = "whistlepro_mobile"
    /** 워치 앱 버전 capability 앞부분: `whistlepro_wear_v<versionCode>` (워치 res/values/wear.xml) */
    const val CAPABILITY_WEAR_VERSION_PREFIX = "whistlepro_wear_v"

    /** 버전 capability 이름의 versionCode. 버전 capability가 아니면 null */
    fun wearVersion(capability: String): Long? =
        capability.takeIf { it.startsWith(CAPABILITY_WEAR_VERSION_PREFIX) }?.removePrefix(CAPABILITY_WEAR_VERSION_PREFIX)?.toLongOrNull()

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

/**
 * 워치의 물리 입력 상태 (워치 → 폰, [SyncPaths.INPUT_STATUS]). 결제는 폰에서만 하므로 사기 전에 "내 워치에서 버튼·회전이 되는지"를
 * 폰 결제 화면이 보여 주려고 보낸다 (2026-11-06 Task 11).
 * [model]은 워치 표시 이름(설정의 기기 이름, 없으면 `Build.MODEL` — 워치 `DeviceInputs.displayName`, 빌 수 있음),
 * [detectable]은 감지되는 통로, [confirmed]는 세 동작 확인을 마친 통로(감지되는 것만), [unavailable]은 지금 막힌 통로(설정이 바뀜),
 * [rotaryIsBezel]은 회전 통로를 "베젤"로 부를지(아니면 "크라운") — 폰도 같은 이름을 쓰게.
 * 실을 때 목록 순서는 상관없다(카드는 [DISPLAY_ORDER]로 다시 고른다). 모르는 통로 이름은 읽을 때 버린다 (다음 버전 호환)
 */
data class WatchInputStatus(
    val model: String,
    val detectable: List<InputChannel>,
    val confirmed: List<InputChannel>,
    val unavailable: List<InputChannel>,
    val rotaryIsBezel: Boolean,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        MODEL_KEY to model,
        DETECTABLE_KEY to detectable.map { it.name },
        CONFIRMED_KEY to confirmed.map { it.name },
        UNAVAILABLE_KEY to unavailable.map { it.name },
        ROTARY_IS_BEZEL_KEY to rotaryIsBezel,
    )

    /** 결제 화면·PRO 안내 카드 */
    val card: WatchInputCard
        get() {
            val usable = DISPLAY_ORDER.filter { it in confirmed && it !in unavailable }
            return when {
                usable.isNotEmpty() -> WatchInputCard.Usable(model, usable, rotaryIsBezel)
                detectable.isEmpty() -> WatchInputCard.Unsupported
                // 확인한 통로가 모두 막혔어도 따로 "지금 안 돼요"를 만들지 않는다: 워치에서 확인 화면을 열면 이유가 보인다
                else -> WatchInputCard.CheckFirst
            }
        }

    companion object {
        const val MODEL_KEY = "model"
        const val DETECTABLE_KEY = "detectable"
        const val CONFIRMED_KEY = "confirmed"
        const val UNAVAILABLE_KEY = "unavailable"
        const val ROTARY_IS_BEZEL_KEY = "rotaryIsBezel"

        /** 카드에 나열하는 순서: 회전 → 뒤로가기 버튼 → 옆 버튼 (시안 2차 ⑤, Ruling 14) */
        val DISPLAY_ORDER = listOf(InputChannel.ROTARY, InputChannel.BACK_KEY, InputChannel.STEM)

        /** 기종 이름이 없으면 null (상태가 아님). 모르는 통로 이름·키는 버린다 */
        fun fromMap(map: Map<String, Any?>): WatchInputStatus? {
            val model = map[MODEL_KEY] as? String ?: return null
            fun channels(key: String): List<InputChannel> {
                val names = (map[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
                return InputChannel.entries.filter { it.name in names }
            }
            return WatchInputStatus(
                model = model,
                detectable = channels(DETECTABLE_KEY),
                confirmed = channels(CONFIRMED_KEY),
                unavailable = channels(UNAVAILABLE_KEY),
                rotaryIsBezel = map[ROTARY_IS_BEZEL_KEY] as? Boolean ?: false,
            )
        }
    }
}

/** 폰 결제 화면 카드·워치 PRO 안내 줄이 보일 것 */
sealed interface WatchInputCard {
    /** 지금 쓸 수 있는 확인된 통로가 있다 ([WatchInputStatus.DISPLAY_ORDER] 순서): 폰 "내 워치(model)" + "통로들으로 워치를 조작할 수 있어요" */
    data class Usable(val model: String, val channels: List<InputChannel>, val rotaryIsBezel: Boolean) : WatchInputCard
    /** 잡히는 통로는 있는데 쓸 수 있게 확인된 것이 없다: "워치에서 물리 입력 확인을 먼저 해 보세요" */
    data object CheckFirst : WatchInputCard
    /** 잡히는 통로가 없다: "내 워치는 버튼·회전 입력을 지원하지 않아요" */
    data object Unsupported : WatchInputCard
}
