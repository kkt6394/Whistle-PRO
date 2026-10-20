package com.aidenstudio.whistlepro.session

import java.io.IOException
import java.util.UUID

/** 메시지를 Data Layer 형식(SyncWire)으로 오가게 해서 받은 쪽이 읽는 그대로 돌려준다 */
fun overWire(message: Map<String, Any?>): Map<String, Any?> = SyncWire.decode(SyncWire.encode(message))

/**
 * 가짜 워치 링크. 경기 DataItem은 items(경기 id → 마지막 바이트)로, 컨텍스트 요청 대기 DataItem은 하나의 경로로 흉내 낸다
 * (같은 경로라 둘이 될 수 없다 — 실제 어댑터도 있으면 다시 만들지 않는다).
 */
class FakeWatchLink : WatchLink {
    override var reachable = false
    /** 보낸 경기 (경기 id, 전송 이름) 순서대로 */
    val puts = mutableListOf<Pair<UUID, String>>()
    val items = linkedMapOf<UUID, ByteArray>()
    val removed = mutableListOf<UUID>()
    var failPuts = false
    var failRemoves = false
    /** 물은 메시지 (경로, 받은 쪽이 읽은 내용) */
    val asked = mutableListOf<Pair<String, Map<String, Any?>>>()
    /** 폰의 답. null이면 실패·timeout */
    var answer: (path: String, message: Map<String, Any?>) -> Map<String, Any?>? = { _, _ -> null }
    var contextRequestQueued = false
    /** 대기 요청 DataItem을 새로 만든 횟수 */
    var contextRequestsCreated = 0
    var contextRequestsCancelled = 0

    override suspend fun putMatch(matchID: UUID, transfer: String, bundle: ByteArray) {
        if (failPuts) throw IOException("전송 실패")
        puts += matchID to transfer
        items[matchID] = bundle
    }

    override suspend fun pendingTransfers(): Set<UUID> = items.keys.toSet()

    override suspend fun removeMatch(matchID: UUID) {
        if (failRemoves) throw IOException("지우기 실패")
        items.remove(matchID)
        removed += matchID
    }

    override suspend fun ask(path: String, message: Map<String, Any?>): Map<String, Any?>? {
        val received = overWire(message)
        asked += path to received
        return answer(path, received)?.let(::overWire)
    }

    override suspend fun queueContextRequest() {
        if (contextRequestQueued) return
        contextRequestQueued = true
        contextRequestsCreated++
    }

    override suspend fun cancelContextRequest() {
        if (!contextRequestQueued) return
        contextRequestQueued = false
        contextRequestsCancelled++
    }

    fun sentIDs(): List<UUID> = puts.map { it.first }
}

/** 가짜 폰 링크. 올린 컨텍스트와 보낸 답을 워치가 읽는 그대로 모은다 */
class FakePhoneLink : PhoneLink {
    override var watchInstalled = false
    override var canDeliver = false
    val contexts = mutableListOf<Map<String, Any?>>()
    /** 올린 컨텍스트의 Data Layer 바이트 크기 */
    val contextSizes = mutableListOf<Int>()
    val replies = mutableListOf<Map<String, Any?>>()

    override suspend fun putContext(context: Map<String, Any?>) {
        val bytes = SyncWire.encode(context)
        contextSizes += bytes.size
        contexts += SyncWire.decode(bytes)
    }

    override suspend fun reply(message: Map<String, Any?>) {
        replies += overWire(message)
    }
}
