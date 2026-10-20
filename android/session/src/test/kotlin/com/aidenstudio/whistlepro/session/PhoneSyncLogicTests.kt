package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 폰 쪽 동기화 규칙 (Swift PhoneSync를 가짜 링크 위에서) */
class PhoneSyncLogicTests {
    private val library = MatchLibrary(tempDirectory())
    private val link = FakePhoneLink()
    private var current: Pair<List<MatchPreset>, Entitlement>? = null
    private val sync = PhoneSyncLogic(library, link, { current }, clock = { 42.0 })

    private val presets = listOf(MatchPreset.FIFA_STANDARD, MatchPreset(UUID.randomUUID(), "조기축구", MatchSetup()))
    private val entitlement = Entitlement(2_000_000_000.0, 1_000.0)

    private fun bundle() = MatchBundle(makeRecord(listOf(CommandKind.StartPeriod to 0.0), 10.0), null)

    private fun bytes(bundle: MatchBundle) = bundle.toJson().toString().toByteArray(Charsets.UTF_8)

    /** 워치 앱이 없거나 연결 전이면 기억해 두었다가 설치되면 보낸다 */
    @Test fun contextQueuedUntilWatchInstalled() = runTest {
        sync.sendContext(presets, entitlement)
        assertTrue(link.contexts.isEmpty())
        link.watchInstalled = true
        link.canDeliver = true
        sync.watchStateChanged(installed = true)
        assertEquals(1, link.contexts.size)
        assertEquals(presets, SyncPayload.presets(link.contexts[0]))
        assertEquals(entitlement, SyncPayload.entitlement(link.contexts[0]))
        assertEquals(42.0, link.contexts[0][SyncPayload.SENT_AT_KEY])
    }

    /** 워치 앱을 다시 설치하면 마지막 내용을 다시 보낸다 */
    @Test fun reinstallResendsLastContext() = runTest {
        link.watchInstalled = true
        link.canDeliver = true
        sync.watchStateChanged(installed = true)
        sync.sendContext(presets, entitlement)
        assertEquals(1, link.contexts.size)
        link.watchInstalled = false
        link.canDeliver = false
        sync.watchStateChanged(installed = false)
        assertEquals(1, link.contexts.size)
        link.watchInstalled = true
        link.canDeliver = true
        sync.watchStateChanged(installed = true)
        assertEquals(2, link.contexts.size)
        assertEquals(presets, SyncPayload.presets(link.contexts[1]))
    }

    /** Review Focus 3: 워치의 컨텍스트 요청 → 컨텍스트를 다시 올리고 같은 내용을 답으로 돌려준다 (없으면 빈 답) */
    @Test fun contextRequestedRepublishesAndReturns() = runTest {
        assertEquals(emptyMap(), sync.contextRequested())
        assertTrue(link.contexts.isEmpty())
        current = presets to entitlement
        val reply = overWire(sync.contextRequested())
        assertEquals(presets, SyncPayload.presets(reply))
        assertEquals(entitlement, SyncPayload.entitlement(reply))
        assertEquals(1, link.contexts.size)
        assertEquals(entitlement, SyncPayload.entitlement(link.contexts[0]))
    }

    /** Review Focus 2: 받았는지 물으면 저장된 경기와 사용자가 지운 경기를 답한다. 묻는 메시지가 아니면 null */
    @Test fun receiptQueryAnswersStoredAndDeleted() {
        val stored = bundle()
        val deleted = bundle()
        library.upsert(stored)
        library.upsert(deleted)
        library.delete(deleted.id)
        val unknown = UUID.randomUUID()
        val reply = assertNotNull(sync.receiptQuery(overWire(SyncPayload.receiptQuery(listOf(stored.id, unknown, deleted.id)))))
        assertEquals(listOf(stored.id, deleted.id), SyncPayload.receivedIDs(overWire(reply)))
        assertNull(sync.receiptQuery(overWire(SyncPayload.contextRequest())))
    }

    /** 읽을 수 없는 경기(앱 버전 차이) → 지원 안 함을 답해 워치가 다시 보내지 않게 한다 */
    @Test fun unreadableBundleRepliesUnsupported() = runTest {
        val id = UUID.randomUUID()
        assertEquals(ReceiveResult.Unsupported, sync.receive(id, "{\"record\":1}".toByteArray()))
        assertEquals(1, link.replies.size)
        assertEquals(id, SyncPayload.matchID(link.replies[0]))
        assertTrue(SyncPayload.isUnsupported(link.replies[0]))
        // 경기 id도 모르면 답할 곳이 없다
        assertEquals(ReceiveResult.Unsupported, sync.receive(null, "쓰레기".toByteArray()))
        assertEquals(1, link.replies.size)
    }

    /** Asset을 가져오지 못했으면(바이트 없음·timeout) 형식 문제가 아니라 실패: 답하지 않아 워치가 다시 보낸다 */
    @Test fun missingAssetIsFailedNotUnsupported() = runTest {
        val id = UUID.randomUUID()
        assertEquals(ReceiveResult.Failed, sync.receive(id, null))
        assertEquals(ReceiveResult.Failed, sync.receive(null, null))
        assertTrue(link.replies.isEmpty())
    }

    /** 저장한 뒤 확인을 보낸다 */
    @Test fun storedBundleAcked() = runTest {
        val b = bundle()
        val result = sync.receive(b.id, bytes(b))
        assertEquals(ReceiveResult.Stored(b), result)
        assertEquals(b, library.bundle(b.id))
        assertEquals(listOf(b.id), link.replies.map { SyncPayload.matchID(it) })
        assertTrue(link.replies.none { SyncPayload.isUnsupported(it) })
        // 같은 경기가 다시 와도 하나로 저장하고 확인을 다시 보낸다
        sync.receive(b.id, bytes(b))
        assertEquals(1, library.all().size)
        assertEquals(2, link.replies.size)
    }

    /** 사용자가 지운 경기가 다시 오면 저장하지 않고 확인만 보낸다 (워치가 계속 보내지 않게) */
    @Test fun deletedMatchStillAcked() = runTest {
        val b = bundle()
        library.upsert(b)
        library.delete(b.id)
        val result = sync.receive(b.id, bytes(b))
        assertEquals(ReceiveResult.AlreadyDeleted(b.id), result)
        assertNull(library.bundle(b.id))
        assertEquals(listOf(b.id), link.replies.map { SyncPayload.matchID(it) })
    }

    /** 저장이 실패하면 확인을 보내지 않는다 (워치가 다시 보낸다) */
    @Test fun failedStoreSendsNoAck() = runTest {
        val dir = tempDirectory()
        dir.parentFile.mkdirs()
        dir.writeText("폴더가 아닌 파일")
        val broken = PhoneSyncLogic(MatchLibrary(dir), link, { null })
        val b = bundle()
        assertIs<ReceiveResult.Failed>(broken.receive(b.id, bytes(b)))
        assertTrue(link.replies.isEmpty())
    }

    /** Review Focus 4: 프리셋이 한도를 넘으면 앞쪽만 가고 구독 상태는 늘 간다 */
    @Test fun oversizedPresetsKeepEntitlement() = runTest {
        val roster = (1L..40L).map { Player(it, "가".repeat(20)) }
        val many = (0 until 20).map { MatchPreset(UUID.randomUUID(), "P$it", MatchSetup(homeRoster = roster, awayRoster = roster)) }
        val fitting = SyncPayload.presetsFittingContext(many)
        assertTrue(fitting.isNotEmpty() && fitting.size < many.size)
        link.watchInstalled = true
        link.canDeliver = true
        sync.sendContext(many, entitlement)
        assertEquals(fitting, SyncPayload.presets(link.contexts.single()))
        assertEquals(entitlement, SyncPayload.entitlement(link.contexts.single()))
        // Data Layer DataItem 한도(100KB) 안에 든다 (base64로 커져도)
        assertTrue(link.contextSizes.single() < 100 * 1024, "크기 ${link.contextSizes.single()}")
    }
}
