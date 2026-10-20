package com.aidenstudio.whistlepro.session

import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * 워치 앱이 폰과 주고받는 통로 (Wear Data Layer 어댑터가 구현한다). WatchConnectivity의 WCSession 자리.
 * 실패는 예외로 알린다 (ask만 null).
 */
interface WatchLink {
    /** 폰 앱 노드(capability whistlepro_mobile)가 지금 연결돼 있다 (WCSession.isReachable) */
    val reachable: Boolean

    /** 경기 DataItem(SyncPaths.match, Asset `bundle`, 메타 `matchID`)을 올린다. 실패하면 예외 */
    suspend fun putMatch(matchID: UUID, transfer: String, bundle: ByteArray)

    /** 아직 남아 있는 경기 DataItem의 경기 id = 전송 중 (outstandingFileTransfers) */
    suspend fun pendingTransfers(): Set<UUID>

    /** 그 경기의 DataItem을 모두 지운다 (확인을 받았다) */
    suspend fun removeMatch(matchID: UUID)

    /** 메시지로 묻고 답을 기다린다 (요청 ID로 짝짓고 10초 timeout). null = 실패·timeout */
    suspend fun ask(path: String, message: Map<String, Any?>): Map<String, Any?>?

    /** 폰 앱이 실행될 때 받도록 컨텍스트 요청을 남긴다. 이미 있으면 다시 만들지 않는다 (transferUserInfo 대기열에 하나만) */
    suspend fun queueContextRequest()

    /** 남겨 둔 컨텍스트 요청을 거둔다 (없으면 아무것도 안 함) */
    suspend fun cancelContextRequest()
}

/** 폰에서 받은 프리셋·구독 상태 (없는 쪽은 null). 앱이 저장·화면 반영을 한다. */
data class Received(val presets: List<MatchPreset>?, val entitlement: Entitlement?) {
    val isEmpty: Boolean get() = presets == null && entitlement == null
}

/**
 * 끝난 경기를 폰으로 보내고(폰이 저장했다는 확인을 받으면 워치에서 지운다), 폰에서 프리셋과 구독 상태를 받는 규칙.
 * Swift WatchSync를 링크 인터페이스 위로 옮긴 것이다. 워치 보관함에 남은 경기 = 아직 확인받지 못한 경기다.
 *
 * - 메인 스레드(한 스레드)에서만 부른다 (Swift @MainActor와 같다). 디스크 작업은 짧은 파일 읽기·지우기뿐이다.
 * - 타이머를 직접 두지 않는다: 전송이 실패하면 [onRetryNeeded]를 부르고, 앱 어댑터가 [RETRY_DELAY]초 뒤 [sendPending]을 부른다.
 * - 활성화 순서(Swift activationDidComplete): [onActivated] → (필요하면) [requestContext] → [askReceiptsThenSend].
 *   폰에 닿게 되면(reachable) [requestContext]와 [askReceipts]를 부른다.
 */
class WatchSyncLogic(
    private val store: FileMatchLogStore,
    private val link: WatchLink,
    /** Unix 초 */
    private val clock: () -> Double,
    private val encodeBundle: (MatchBundle) -> ByteArray = ::defaultEncode,
) {
    companion object {
        /** 전송이 실패하면 이 초 뒤에 다시 보낸다 */
        const val RETRY_DELAY = 60.0
        /** 화면에 나타날 때 다시 보내기 간격 */
        const val RESEND_INTERVAL = 30.0
        /** 같은 경기를 다시 보내지 않는 간격 (10분) */
        const val MATCH_RESEND_INTERVAL = 600.0
        /** 받았는지 다시 묻는 간격 */
        const val RECEIPT_INTERVAL = 60.0

        private fun defaultEncode(bundle: MatchBundle): ByteArray {
            val json = bundle.toJson()
            requireFinite(json, "bundle")
            return json.toString().toByteArray(Charsets.UTF_8)
        }
    }

    var pendingCount = 0
        private set

    /** 폰이 읽지 못한다고 알린 경기 (이번 실행 동안 보내지 않는다) */
    var held: Set<UUID> = emptySet()
        private set
    val heldCount: Int get() = held.size

    /** 새로 설치돼 폰에서 받은 컨텍스트가 없다 → 폰에 닿으면 요청한다 */
    var needsContext = false
        private set

    /** 전송이 실패했다: 앱 어댑터가 RETRY_DELAY초 뒤 sendPending을 부른다 (예약은 하나만 두고 새 실패가 앞 예약을 바꾼다) */
    var onRetryNeeded: (() -> Unit)? = null

    private var contextMessageInFlight = false
    private val resendThrottle = ResendThrottle(RESEND_INTERVAL)
    /** 같은 경기를 10분 안에 다시 보내지 않는다 (폰 앱이 꺼져 있으면 확인이 오지 않아 화면에 나타날 때마다 다시 보냈다) */
    private val matchThrottle = MatchSendThrottle(MATCH_RESEND_INTERVAL)
    /** 폰에 "이 경기들 받았니?"를 묻는 정리 경로 (확인을 놓쳐 보관함에 남은 경기를 거둔다) */
    private val receiptCheck = ReceiptCheck(RECEIPT_INTERVAL)
    /** 받았는지 묻는 답(또는 실패)이 오면 남은 경기를 보낸다 */
    private var sendAfterReceipts = false

    init {
        refreshPendingCount()
    }

    /**
     * 보관함의 경기를 모두 보낸다. 이미 전송 중(DataItem이 남음)이거나, 폰이 읽지 못한다고 했거나, 10분 안에 보낸 경기는 건너뛴다.
     * 보관함에 없는 경기의 DataItem(확인을 받았는데 지우지 못한 것)은 지운다 — Swift removeStaleOutboxFiles 자리.
     */
    suspend fun sendPending() {
        refreshPendingCount()
        val inFlight = attempt { link.pendingTransfers() } ?: return
        // 지울지는 파일 이름으로 본다: 기록을 읽지 못한 경기의 DataItem까지 지우지 않게
        val archivedIDs = runCatching { store.archivedIDs().toSet() }.getOrNull()
        if (archivedIDs != null) for (stale in inFlight - archivedIDs) attempt { link.removeMatch(stale) }
        val records = runCatching { store.archived() }.getOrDefault(emptyList())
        if (records.isEmpty()) return
        val now = clock()
        for (record in records) {
            val id = record.id
            if (id in inFlight || id in held || !matchThrottle.allows(id, now)) continue
            val bytes = try {
                encodeBundle(MatchBundle(record, runCatching { store.archivedWorkout(id) }.getOrNull()))
            } catch (e: Exception) {
                // 묶음을 만들지 못했다 (Swift: outbox 파일 쓰기 실패): 다음 기회에 바로 다시
                matchThrottle.failed(id)
                continue
            }
            try {
                link.putMatch(id, UUID.randomUUID().swiftString, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onTransferFailed(id)
            }
        }
    }

    /**
     * 앱이 화면에 나타날 때: 확인받지 못한 경기가 있으면 다시 보낸다 (30초에 한 번까지).
     * 폰이 받고도 확인이 워치에 처리되지 않은 경우를 메운다.
     */
    suspend fun resendOnActivation() {
        // 간격 제한을 먼저 본다: 보관함을 읽는 건 그 다음 (손목을 들 때마다 읽지 않게)
        if (!resendThrottle.allows(clock())) return
        refreshPendingCount()
        if (pendingCount == 0) return
        askReceiptsThenSend()
    }

    /**
     * 남은 경기를 다시 보내되, 폰에 닿으면 받았는지 먼저 묻고 답(또는 실패) 뒤에 보낸다 — 폰이 이미 받은 경기를 다시 보내지 않게.
     * 닿지 않거나 간격 안이라 묻지 않으면 바로 보낸다.
     */
    suspend fun askReceiptsThenSend() {
        if (!receiptCheck.sendsAfterAnswer(link.reachable, clock())) {
            sendPending()
            return
        }
        sendAfterReceipts = true
        askReceipts()
    }

    /**
     * 보관함에 남은 경기 중 폰이 이미 받은 게 있는지 묻는다 (폰에 닿을 때만, 답을 기다리는 동안은 겹치지 않게).
     * 받았다고 한 경기는 확인을 받은 것처럼 지우고, 나머지는 원래대로 다시 보낸다.
     * 물을 수 있는지(답 대기·60초 간격)를 디스크보다 먼저 보고, 경기 ID는 파일 이름에서 뽑는다.
     */
    suspend fun askReceipts() {
        if (!link.reachable || !receiptCheck.canAsk(clock())) return
        val pending = runCatching { store.archivedIDs() }.getOrDefault(emptyList())
        val asked = receiptCheck.begin(pending, clock())
        if (asked == null) {
            // 다른 물음이 답을 기다리는 중이면 그 답 뒤에 보낸다
            if (!receiptCheck.inFlight) receiptsSettled()
            return
        }
        val reply = attempt { link.ask(SyncPaths.RECEIPT_QUERY, SyncPayload.receiptQuery(asked)) }
        if (reply == null) {
            receiptCheck.failed()
        } else {
            val confirmed = receiptCheck.answered(asked, SyncPayload.receivedIDs(reply), clock())
            acknowledged(confirmed)
        }
        receiptsSettled()
    }

    /** 묻기가 끝났다 (답·실패·물을 경기 없음): 기다리던 다시 보내기를 한다 */
    private suspend fun receiptsSettled() {
        if (!sendAfterReceipts) return
        sendAfterReceipts = false
        sendPending()
    }

    /**
     * 폰에 프리셋·구독 상태를 요청한다 (받은 컨텍스트가 없을 때만). 닿으면 메시지로 바로 받고,
     * 닿지 않거나 실패하거나 빈 답이면 요청을 남겨 두어 폰 앱이 실행될 때 컨텍스트로 받는다.
     * 받은 내용을 돌려준다 (못 받았으면 null).
     */
    suspend fun requestContext(): Received? {
        if (!needsContext) return null
        if (!link.reachable) {
            attempt { link.queueContextRequest() }
            return null
        }
        // reachable이 깜빡이면 요청이 겹쳐 나갔다: 답을 기다리는 동안은 다시 보내지 않는다
        if (contextMessageInFlight) return null
        contextMessageInFlight = true
        val reply = try {
            attempt { link.ask(SyncPaths.CONTEXT_REQUEST, SyncPayload.contextRequest()) }
        } finally {
            contextMessageInFlight = false
        }
        val received = reply?.let { received(it) }
        if (received == null || received.isEmpty) {
            // 빈 응답이면 다음 reachable 변화까지 다시 묻지 않았다: 폰 앱이 실행될 때 받도록 남겨 둔다
            attempt { link.queueContextRequest() }
            return null
        }
        return received
    }

    /**
     * 앱이 켜질 때 이미 받아 둔 컨텍스트(DataItem /whistlepro/context)를 읽는다. 없으면(context가 null·빈 맵) 요청이 필요하다고 표시한다.
     * 받은 게 없으면 null. 그 다음 어댑터가 requestContext()·askReceiptsThenSend()를 부른다.
     */
    suspend fun onActivated(context: Map<String, Any?>?): Received? {
        val context = context ?: emptyMap()
        val received = received(context)
        if (SyncPayload.needsContext(context)) needsContext = true
        return received.takeUnless { it.isEmpty }
    }

    /** 폰이 새 컨텍스트를 올렸다 */
    suspend fun onContext(context: Map<String, Any?>): Received = received(context)

    /** 폰의 확인(경기 저장) 또는 지원 안 함. 메시지·대기 DataItem 어느 쪽으로 와도 같다 */
    suspend fun onPhoneReply(message: Map<String, Any?>) {
        val matchID = SyncPayload.matchID(message) ?: return
        if (SyncPayload.isUnsupported(message)) {
            held = held + matchID
        } else {
            acknowledged(listOf(matchID))
        }
    }

    /** 경기 전송이 실패했다: 10분을 기다리지 않고 다시 보내게 하고 재시도를 알린다 */
    suspend fun onTransferFailed(matchID: UUID) {
        matchThrottle.failed(matchID)
        onRetryNeeded?.invoke()
    }

    private suspend fun received(context: Map<String, Any?>): Received {
        val received = Received(SyncPayload.presets(context), SyncPayload.entitlement(context))
        if (!received.isEmpty) {
            needsContext = false
            // 받았으니 대기열에 남은 요청은 거둔다
            attempt { link.cancelContextRequest() }
        }
        return received
    }

    /** 확인받은 경기를 보관함과 DataItem에서 지운다. 모두 지운 뒤 남은 수를 한 번만 센다 */
    private suspend fun acknowledged(matchIDs: List<UUID>) {
        if (matchIDs.isEmpty()) return
        for (id in matchIDs) {
            runCatching { store.deleteArchived(id) }
            attempt { link.removeMatch(id) }
        }
        refreshPendingCount()
    }

    private fun refreshPendingCount() {
        pendingCount = runCatching { store.archived().size }.getOrDefault(0)
    }

    /** 링크 호출: 실패하면 null (취소는 그대로 던진다) */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
