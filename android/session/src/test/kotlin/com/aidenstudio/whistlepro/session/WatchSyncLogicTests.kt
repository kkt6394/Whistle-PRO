package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 워치 쪽 동기화 규칙 (Swift WatchSync를 가짜 링크 위에서) */
class WatchSyncLogicTests {
    private val store = FileMatchLogStore(tempDirectory())
    private val link = FakeWatchLink()
    private val clock = FakeClock()
    private var retries = 0
    private val sync = WatchSyncLogic(store, link, { clock.t }).also { it.onRetryNeeded = { retries++ } }

    /** 끝난 경기를 워치 보관함에 넣는다 */
    private fun finish(createdAt: Double = 0.0): MatchRecord =
        makeRecord(listOf(CommandKind.StartPeriod to createdAt), createdAt).also { store.finishActive(it) }

    private fun at(t: Double) {
        clock.t = t
    }

    @Test fun sendsEveryArchivedMatchOnce() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        sync.sendPending()
        assertEquals(setOf(a.id, b.id), link.sentIDs().toSet())
        assertEquals(2, link.puts.size)
        assertEquals(2, sync.pendingCount)
        // 실은 바이트는 폰이 읽는 경기 묶음 그대로
        val bundle = MatchBundle.fromJson(parseJsonObject(link.items.getValue(a.id)))
        assertEquals(a, bundle.record)
        // 전송 이름은 전송마다 다르다
        assertEquals(2, link.puts.map { it.second }.toSet().size)
    }

    /** 이번 실행에서 방금 올린 경기는 DataItem이 남아 있는 동안(10분 안) 지우지도 다시 올리지도 않는다 */
    @Test fun skipsMatchesAlreadyInFlight() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        at(0.0)
        sync.sendPending()
        at(5.0)
        sync.sendPending()
        assertEquals(setOf(a.id, b.id), link.sentIDs().toSet())
        assertEquals(2, link.puts.size)
        assertTrue(link.removed.isEmpty())
        assertEquals(setOf(a.id, b.id), link.items.keys)
    }

    /**
     * 확인이 오지 않으면 DataItem이 남아 있어도 10분 뒤 지우고 새 전송 이름으로 다시 올린다
     * (Data Layer DataItem은 ack 전까지 남는다 — 남았다고 계속 기다리면 폰이 저장에 실패한 경기가 영영 다시 가지 않는다)
     */
    @Test fun sameMatchNotResentWithinTenMinutes() = runTest {
        val a = finish()
        at(0.0)
        sync.sendPending()
        val first = link.puts.single().second
        at(30.0)
        sync.sendPending()
        assertEquals(listOf(a.id), link.sentIDs())
        assertTrue(link.removed.isEmpty())
        at(601.0)
        sync.sendPending()
        assertEquals(listOf(a.id, a.id), link.sentIDs())
        assertEquals(listOf(a.id), link.removed)
        assertTrue(link.puts[1].second != first)
        assertEquals(setOf(a.id), link.items.keys)
    }

    /** 남은 DataItem을 지우지 못하면 새 전송을 올리지 않고(한 경기에 DataItem 둘 방지) 재시도를 알린다. 지워지면 바로 다시 보낸다 */
    @Test fun failedRemoveSkipsPut() = runTest {
        val a = finish()
        at(0.0)
        sync.sendPending()
        link.failRemoves = true
        at(601.0)
        sync.sendPending()
        assertEquals(1, link.puts.size)
        assertEquals(1, retries)
        link.failRemoves = false
        at(602.0)
        sync.sendPending()
        assertEquals(listOf(a.id, a.id), link.sentIDs())
        assertEquals(setOf(a.id), link.items.keys)
    }

    /** 받았는지 묻기 경로도 보관함 목록 IO 오류를 견딘다: 묻지 않고(물을 경기 없음) 예외 없이 끝난다 */
    @Test fun unreadableArchiveSkipsReceiptQuery() = runTest {
        val dir = tempDirectory()
        dir.mkdirs()
        java.io.File(dir, "finished").writeText("폴더가 아닌 파일")
        link.reachable = true
        link.answer = { _, _ -> SyncPayload.receiptReply(emptyList()) }
        val broken = WatchSyncLogic(FileMatchLogStore(dir), link, { clock.t })
        broken.askReceiptsThenSend()
        broken.askReceipts()
        assertTrue(link.asked.isEmpty())
        assertTrue(link.puts.isEmpty())
    }

    /** 앱을 다시 실행하면(새 로직) 지난 실행의 DataItem이 남은 경기를 바로 새로 보낸다 */
    @Test fun leftoverDataItemResentAfterRestart() = runTest {
        val a = finish()
        at(0.0)
        sync.sendPending()
        val restarted = WatchSyncLogic(store, link, { clock.t })
        at(5.0)
        restarted.sendPending()
        assertEquals(listOf(a.id, a.id), link.sentIDs())
        assertEquals(listOf(a.id), link.removed)
        // 새 실행에서도 방금 올린 건 10분 안에 다시 올리지 않는다
        at(10.0)
        restarted.sendPending()
        assertEquals(2, link.puts.size)
    }

    /** 전송이 실패하면 10분을 기다리지 않고 다시 보내고, 앱 어댑터에 재시도(60초 뒤)를 알린다 */
    @Test fun failedTransferResendsImmediately() = runTest {
        val a = finish()
        link.failPuts = true
        at(0.0)
        sync.sendPending()
        assertEquals(1, retries)
        assertTrue(link.puts.isEmpty())
        link.failPuts = false
        at(1.0)
        sync.sendPending()
        assertEquals(listOf(a.id), link.sentIDs())
        // 어댑터가 알려 온 실패도 같다: 남은 DataItem을 지우고 바로 새로 올린다
        sync.onTransferFailed(a.id)
        assertEquals(2, retries)
        at(2.0)
        sync.sendPending()
        assertEquals(listOf(a.id, a.id), link.sentIDs())
        assertEquals(listOf(a.id), link.removed)
        assertEquals(60.0, WatchSyncLogic.RETRY_DELAY)
    }

    /** 폰의 확인: 워치 보관함과 경기 DataItem을 지운다 */
    @Test fun ackDeletesArchivedAndDataItem() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        sync.sendPending()
        sync.onPhoneReply(overWire(SyncPayload.ack(a.id)))
        assertEquals(listOf(b), store.archived())
        assertFalse(a.id in link.items)
        assertEquals(listOf(a.id), link.removed)
        assertEquals(1, sync.pendingCount)
    }

    /** 폰이 읽지 못한다고 한 경기는 이번 실행 동안 보내지 않는다 (보관함에는 남는다) */
    @Test fun unsupportedHoldsForThisRun() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        sync.onPhoneReply(overWire(SyncPayload.unsupported(a.id)))
        assertEquals(setOf(a.id), sync.held)
        sync.sendPending()
        assertEquals(listOf(b.id), link.sentIDs())
        assertEquals(2, store.archived().size)
        at(700.0)
        sync.sendPending()
        assertFalse(a.id in link.sentIDs())
    }

    /** 지원 안 함을 받으면 DataItem을 지우고, 다음 실행(폰 앱이 업데이트됐을 수 있다)에는 다시 보낸다 */
    @Test fun unsupportedResentAfterRestart() = runTest {
        val a = finish()
        at(0.0)
        sync.sendPending()
        sync.onPhoneReply(overWire(SyncPayload.unsupported(a.id)))
        assertEquals(listOf(a.id), link.removed)
        assertTrue(link.items.isEmpty())
        val restarted = WatchSyncLogic(store, link, { clock.t })
        at(1.0)
        restarted.sendPending()
        assertEquals(listOf(a.id, a.id), link.sentIDs())
    }

    /** 보관함 목록을 읽지 못하면(IO 오류) 남은 DataItem 정리를 건너뛴다 (빈 목록으로 보고 모두 지우지 않게) */
    @Test fun unreadableArchiveSkipsCleanup() = runTest {
        val dir = tempDirectory()
        dir.mkdirs()
        java.io.File(dir, "finished").writeText("폴더가 아닌 파일")
        val other = UUID.randomUUID()
        link.items[other] = ByteArray(0)
        WatchSyncLogic(FileMatchLogStore(dir), link, { clock.t }).sendPending()
        assertTrue(link.removed.isEmpty())
        assertEquals(setOf(other), link.items.keys)
    }

    /** activated: 받아 둔 컨텍스트 반영 → 컨텍스트 요청 → 받았는지 묻고 보내기 (Swift activationDidComplete 순서) */
    @Test fun activatedRunsSwiftOrder() = runTest {
        val a = finish()
        link.reachable = true
        val entitlement = Entitlement(2_000_000_000.0, 1.0)
        link.answer = { path, _ ->
            when (path) {
                SyncPaths.CONTEXT_REQUEST -> SyncPayload.context(emptyList(), entitlement, 1.0)
                SyncPaths.RECEIPT_QUERY -> SyncPayload.receiptReply(emptyList())
                else -> null
            }
        }
        val received = assertNotNull(sync.activated(null))
        assertEquals(entitlement, received.entitlement)
        assertEquals(listOf(SyncPaths.CONTEXT_REQUEST, SyncPaths.RECEIPT_QUERY), link.asked.map { it.first })
        assertEquals(listOf(a.id), link.sentIDs())
        // 받아 둔 컨텍스트가 있으면 묻지 않고 그 내용을 돌려준다
        val again = WatchSyncLogic(store, link, { clock.t })
        val stored = assertNotNull(again.activated(overWire(SyncPayload.context(emptyList(), entitlement, 2.0))))
        assertEquals(entitlement, stored.entitlement)
        assertEquals(1, link.asked.count { it.first == SyncPaths.CONTEXT_REQUEST })
    }

    /**
     * Review Focus 2: 확인을 놓친 경기. 다시 열릴 때 폰에 닿으면 받았는지 먼저 묻고,
     * 폰이 받았다고 한 경기는 지우고 나머지만 보낸다 (같은 경기를 폰에 두 번 보내지 않는다)
     */
    @Test fun askReceiptsBeforeResend() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        // 폰은 A를 받아 저장했지만 확인이 오지 않았다 (DataItem도 이미 정리됨 → 묻지 않으면 A를 다시 보낸다)
        link.reachable = true
        link.answer = { path, message ->
            assertEquals(SyncPaths.RECEIPT_QUERY, path)
            SyncPayload.receiptReply(SyncPayload.receiptQueryIDs(message)!!.filter { it == a.id })
        }
        at(100.0)
        sync.resendOnActivation()
        assertEquals(1, link.asked.size)
        assertEquals(setOf(a.id, b.id), SyncPayload.receiptQueryIDs(link.asked[0].second)!!.toSet())
        assertEquals(listOf(b), store.archived())
        assertEquals(listOf(b.id), link.sentIDs())
        assertEquals(1, sync.pendingCount)
    }

    /** 확인을 받아 보관함에서 지운 경기의 DataItem이 남아 있으면 다음 보내기 때 지운다 */
    @Test fun staleDataItemIsRemoved() = runTest {
        val gone = UUID.randomUUID()
        link.items[gone] = ByteArray(0)
        val a = finish()
        sync.sendPending()
        assertEquals(listOf(gone), link.removed)
        assertEquals(listOf(a.id), link.items.keys.toList())
    }

    /** 폰이 "안 받음"이라 답한 경기도 같은 규칙: 10분 안에는 그대로, 지나면 남은 DataItem을 지우고 다시 올린다 */
    @Test fun notReceivedReplyFollowsTenMinuteRule() = runTest {
        val a = finish()
        at(0.0)
        sync.sendPending()
        link.reachable = true
        link.answer = { _, _ -> SyncPayload.receiptReply(emptyList()) }
        at(30.0)
        sync.askReceiptsThenSend()
        assertEquals(1, link.asked.size)
        assertEquals(1, link.puts.size)
        at(601.0)
        sync.askReceiptsThenSend()
        assertEquals(2, link.asked.size)
        assertEquals(listOf(a.id, a.id), link.sentIDs())
        assertEquals(listOf(a.id), link.removed)
    }

    /** 받았는지 묻기가 실패해도 남은 경기는 보내고, 다음 기회에 바로 다시 묻는다 */
    @Test fun receiptQueryFailureStillSends() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        link.reachable = true
        at(0.0)
        sync.askReceiptsThenSend()
        assertEquals(1, link.asked.size)
        assertEquals(setOf(a.id, b.id), link.sentIDs().toSet())
        at(1.0)
        sync.askReceipts()
        assertEquals(2, link.asked.size)
    }

    @Test fun unreachableSendsWithoutAsking() = runTest {
        val a = finish(1.0)
        val b = finish(2.0)
        sync.askReceiptsThenSend()
        assertTrue(link.asked.isEmpty())
        assertEquals(setOf(a.id, b.id), link.sentIDs().toSet())
    }

    /** 화면에 나타날 때 다시 보내기는 30초에 한 번 */
    @Test fun resendOnActivationThrottled30s() = runTest {
        val a = finish(1.0)
        at(100.0)
        sync.resendOnActivation()
        assertEquals(listOf(a.id), link.sentIDs())
        val c = finish(2.0)
        at(110.0)
        sync.resendOnActivation()
        assertEquals(listOf(a.id), link.sentIDs())
        at(130.0)
        sync.resendOnActivation()
        assertEquals(listOf(a.id, c.id), link.sentIDs())
    }

    /**
     * Review Focus 3: 새로 설치돼 받은 컨텍스트가 없다 → 닿으면 묻고, 실패하면 대기 요청을 하나만 남기고,
     * 받으면 대기 요청을 거둔다. 받은 뒤에는 다시 묻지 않는다.
     */
    @Test fun newInstallRequestsContextOnce() = runTest {
        assertNull(sync.onActivated(emptyMap()))
        assertTrue(sync.needsContext)
        // 닿지 않으면 묻지 않고 대기 요청을 남긴다
        sync.requestContext()
        assertTrue(link.asked.isEmpty())
        assertEquals(1, link.contextRequestsCreated)
        // 닿았지만 실패: 대기 요청은 여전히 하나
        link.reachable = true
        assertNull(sync.requestContext())
        assertEquals(listOf(SyncPaths.CONTEXT_REQUEST), link.asked.map { it.first })
        assertTrue(SyncPayload.isContextRequest(link.asked[0].second))
        assertEquals(1, link.contextRequestsCreated)
        assertTrue(link.contextRequestQueued)
        // 폰이 답한다
        val presets = listOf(MatchPreset.FIFA_STANDARD, MatchPreset(UUID.randomUUID(), "조기축구", MatchSetup()))
        val entitlement = Entitlement(2_000_000_000.0, 1_000.0)
        link.answer = { _, _ -> SyncPayload.context(presets, entitlement, 5.0) }
        val received = assertNotNull(sync.requestContext())
        assertEquals(presets, received.presets)
        assertEquals(entitlement, received.entitlement)
        assertFalse(sync.needsContext)
        assertFalse(link.contextRequestQueued)
        assertEquals(1, link.contextRequestsCancelled)
        // 받은 뒤에는 다시 묻지 않는다
        assertNull(sync.requestContext())
        assertEquals(2, link.asked.size)
    }

    /** 폰이 빈 답을 주면(이전 앱·내용 없음) 대기 요청을 남겨 폰 앱이 실행될 때 받는다 */
    @Test fun emptyContextReplyQueuesRequest() = runTest {
        sync.onActivated(emptyMap())
        link.reachable = true
        link.answer = { _, _ -> emptyMap() }
        assertNull(sync.requestContext())
        assertTrue(link.contextRequestQueued)
        assertTrue(sync.needsContext)
    }

    /** 컨텍스트가 오면 내용을 돌려주고 대기 요청을 거둔다. 이미 받은 컨텍스트로 켜지면 묻지 않는다 */
    @Test fun receivedContextIsReturned() = runTest {
        link.contextRequestQueued = true
        val entitlement = Entitlement(null, 3.0, revoked = true)
        val received = sync.onContext(overWire(SyncPayload.context(emptyList(), entitlement, 1.0)))
        assertEquals(emptyList(), received.presets)
        assertEquals(entitlement, received.entitlement)
        assertFalse(link.contextRequestQueued)
        val onLaunch = assertNotNull(sync.onActivated(overWire(SyncPayload.context(emptyList(), entitlement, 1.0))))
        assertEquals(entitlement, onLaunch.entitlement)
        assertFalse(sync.needsContext)
        link.reachable = true
        sync.requestContext()
        assertTrue(link.asked.isEmpty())
    }

    /** 워치 물리 입력 상태: 바뀔 때만 올리고, 올리기에 실패하면 다음 기회([flushInputStatus])에 다시 (2026-11-06 Task 11) */
    @Test fun sendsInputStatusOnlyWhenChanged() = runTest {
        val first = WatchInputStatus("SM-R965N", listOf(InputChannel.BACK_KEY), emptyList(), emptyList(), rotaryIsBezel = false)
        sync.sendInputStatus(first)
        assertEquals(listOf(first), link.inputStatuses)
        sync.sendInputStatus(first)
        assertEquals(1, link.inputStatuses.size, "같은 상태를 또 올렸다")
        val confirmed = first.copy(confirmed = listOf(InputChannel.BACK_KEY))
        link.failInputStatus = true
        sync.sendInputStatus(confirmed)
        assertEquals(1, link.inputStatuses.size)
        link.failInputStatus = false
        sync.flushInputStatus()
        assertEquals(listOf(first, confirmed), link.inputStatuses)
        sync.flushInputStatus()
        assertEquals(2, link.inputStatuses.size)
    }
}
