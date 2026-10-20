package com.aidenstudio.whistlepro.session

import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * 폰 앱이 워치와 주고받는 통로 (Wear Data Layer 어댑터가 구현한다). WatchConnectivity의 WCSession 자리.
 */
interface PhoneLink {
    /** 워치 앱(capability whistlepro_wear)이 설치된 노드가 있다 (WCSession.isWatchAppInstalled) */
    val watchInstalled: Boolean

    /** 지금 컨텍스트를 보낼 수 있다 (Swift: 활성화 + 페어링 + 워치 앱 설치) */
    val canDeliver: Boolean

    /** 컨텍스트 DataItem(SyncPaths.CONTEXT)을 올린다 (updateApplicationContext). 실패하면 예외 */
    suspend fun putContext(context: Map<String, Any?>)

    /**
     * 확인·지원 안 함을 워치로: 워치 노드에 닿으면 메시지(SyncPaths.ACK), 실패하면 DataItem(SyncPaths.ACK_PREFIX + id)으로 남긴다.
     * (Task 5 메모) 대기 DataItem은 내용이 같으면 변경으로 잡히지 않아(onDataChanged 없음) 같은 경기의 두 번째 확인이 가지 않는다
     * → `sentAt`(보낸 시각)을 함께 넣는다. 워치는 처리 뒤 폰 노드를 포함한 URI로 그 DataItem을 지운다.
     */
    suspend fun reply(message: Map<String, Any?>)
}

/** 워치가 보낸 경기를 받은 결과 */
sealed interface ReceiveResult {
    /** 저장하고 확인을 보냈다 (Health Connect 저장·목록 새로고침은 이때만) */
    data class Stored(val bundle: MatchBundle) : ReceiveResult

    /** 사용자가 지운 경기라 다시 저장하지 않고 확인만 보냈다 */
    data class AlreadyDeleted(val matchID: UUID) : ReceiveResult

    /** 읽을 수 없는 형식 (앱 버전 차이): 경기 id를 알면 지원 안 함을 답했다 */
    data object Unsupported : ReceiveResult

    /** 바이트를 가져오지 못했거나(Asset 실패·timeout) 저장하지 못했다 (디스크 등). 확인을 보내지 않아 워치가 다시 보낸다 */
    data object Failed : ReceiveResult
}

/**
 * 워치에서 끝난 경기를 받아 저장하고 확인을 보내고, 프리셋과 구독 상태를 워치로 보내는 규칙.
 * Swift PhoneSync를 링크 인터페이스 위로 옮긴 것이다. 메인 스레드(한 스레드)에서 부른다.
 */
class PhoneSyncLogic(
    private val library: MatchLibrary,
    private val link: PhoneLink,
    /**
     * 지금 보낼 프리셋·구독 상태. 워치가 요청하면(새로 설치돼 받은 게 없을 때) 이 값을 보낸다.
     * 리스너 서비스로 깨어나 화면이 아직 없어도 저장된 값으로 답한다.
     */
    private val currentContext: () -> Pair<List<MatchPreset>, Entitlement>?,
    /** Unix 초 (컨텍스트의 보낸 시각) */
    private val clock: () -> Double = { System.currentTimeMillis() / 1000.0 },
) {
    private val delivery = ContextDelivery<Pair<List<MatchPreset>, Entitlement>>()

    /** 워치 앱이 없거나 아직 연결 전이면 기억해 두었다가 연결되면 보낸다. */
    suspend fun sendContext(presets: List<MatchPreset>, entitlement: Entitlement) {
        delivery.send(presets to entitlement, link.canDeliver)?.let { deliver(it) }
    }

    /** 워치 앱이 새로 설치되면(재설치 포함) 마지막 내용을 다시 보낸다. 새 워치 앱은 이전 구독 상태를 모른다. */
    suspend fun watchStateChanged(installed: Boolean) {
        delivery.watchStateChanged(installed, link.canDeliver)?.let { deliver(it) }
    }

    /** 프리셋이 한도를 넘으면 SyncPayload.context가 들어가는 만큼만 넣는다 (구독 상태는 늘 간다). */
    private suspend fun deliver(payload: Pair<List<MatchPreset>, Entitlement>) {
        put(SyncPayload.context(payload.first, payload.second, clock()))
    }

    private suspend fun put(context: Map<String, Any?>) {
        try {
            link.putContext(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Swift도 updateApplicationContext 실패는 기록만 한다: 다음 변경·요청 때 다시 보낸다
        }
    }

    /** 워치의 컨텍스트 요청에 답한다: 컨텍스트를 새로 올리고(다음에 워치가 켜질 때도 남게) 같은 내용을 돌려준다. 보낼 게 없으면 빈 맵. */
    suspend fun contextRequested(): Map<String, Any?> {
        val (presets, entitlement) = currentContext() ?: return emptyMap()
        val context = SyncPayload.context(presets, entitlement, clock())
        put(context)
        return context
    }

    /** 워치가 보관함에 남은 경기를 받았는지 물으면 이미 받은(또는 지운) 경기를 답한다. 묻는 메시지가 아니면 null */
    fun receiptQuery(message: Map<String, Any?>): Map<String, Any?>? {
        val asked = SyncPayload.receiptQueryIDs(message) ?: return null
        return SyncPayload.receiptReply(library.received(asked))
    }

    /**
     * 워치가 보낸 경기(DataItem의 bundle 바이트, 메타의 경기 id)를 받는다. 저장에 성공했을 때만 확인을 보낸다.
     * 읽을 수 없는 형식이면 워치가 계속 다시 보내지 않게 지원 안 함을 답한다.
     */
    suspend fun receive(matchID: UUID?, bytes: ByteArray?): ReceiveResult {
        // 바이트가 없다 = Asset을 가져오지 못했다(실패·timeout): 형식 문제가 아니므로 답하지 않는다 (워치가 10분 뒤·다음 실행에 다시 보낸다)
        if (bytes == null) return ReceiveResult.Failed
        val bundle = runCatching { MatchBundle.fromJson(parseJsonObject(bytes)) }.getOrNull()
        if (bundle == null) {
            if (matchID != null) reply(SyncPayload.unsupported(matchID))
            return ReceiveResult.Unsupported
        }
        val stored = try {
            library.upsert(bundle)
        } catch (e: Exception) {
            return ReceiveResult.Failed
        }
        reply(SyncPayload.ack(bundle.id))
        return if (stored) ReceiveResult.Stored(bundle) else ReceiveResult.AlreadyDeleted(bundle.id)
    }

    private suspend fun reply(message: Map<String, Any?>) {
        try {
            link.reply(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 링크가 메시지 실패를 DataItem 대기로 받는다. 그마저 실패하면 워치가 받았는지 물어 거둔다
        }
    }
}
