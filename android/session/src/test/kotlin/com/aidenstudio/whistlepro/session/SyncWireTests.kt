package com.aidenstudio.whistlepro.session

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Data Layer로 싣는 메시지 형식: SyncPayload의 모든 메시지가 JSON 바이트로 오가도 같게 읽힌다 */
class SyncWireTests {
    private fun roundTrip(message: Map<String, Any?>) = SyncWire.decode(SyncWire.encode(message))

    @Test fun everySyncPayloadMessageRoundTrips() {
        val id = UUID.randomUUID()
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())

        val ack = roundTrip(SyncPayload.ack(id))
        assertEquals(id, SyncPayload.matchID(ack))
        assertFalse(SyncPayload.isUnsupported(ack))

        val unsupported = roundTrip(SyncPayload.unsupported(id))
        assertEquals(id, SyncPayload.matchID(unsupported))
        assertTrue(SyncPayload.isUnsupported(unsupported))

        assertEquals(ids, SyncPayload.receiptQueryIDs(roundTrip(SyncPayload.receiptQuery(ids))))
        assertEquals(ids, SyncPayload.receivedIDs(roundTrip(SyncPayload.receiptReply(ids))))
        assertEquals(emptyList(), SyncPayload.receiptQueryIDs(roundTrip(SyncPayload.receiptQuery(emptyList()))))
        assertTrue(SyncPayload.isContextRequest(roundTrip(SyncPayload.contextRequest())))

        val presets = listOf(
            MatchPreset.FIFA_STANDARD,
            MatchPreset(UUID.randomUUID(), "조기축구", MatchSetup(homeRoster = listOf(Player(7L, "김민준")))),
        )
        val entitlement = Entitlement(expiresAt = 1_900_000_000.5, checkedAt = 1_800_000_000.25, revoked = false)
        val original = SyncPayload.context(presets, entitlement, sentAt = 1_234.5)
        val context = roundTrip(original)
        assertEquals(presets, SyncPayload.presets(context))
        assertEquals(entitlement, SyncPayload.entitlement(context))
        assertEquals(1_234.5, context[SyncPayload.SENT_AT_KEY])
        assertContentEquals(original[SyncPayload.PRESETS_KEY] as ByteArray, context[SyncPayload.PRESETS_KEY] as ByteArray)
        assertFalse(SyncPayload.needsContext(context))
        assertTrue(SyncPayload.needsContext(roundTrip(emptyMap())))
    }

    /** 답 메시지를 물음과 짝짓는 요청 ID */
    @Test fun requestIdRoundTrips() {
        val requestId = UUID.randomUUID().toString()
        val ids = listOf(UUID.randomUUID())
        val bytes = SyncWire.request(requestId, SyncPayload.receiptQuery(ids))
        assertEquals(requestId, SyncWire.requestId(bytes))
        assertEquals(ids, SyncPayload.receiptQueryIDs(SyncWire.decode(bytes)))
        assertNull(SyncWire.requestId(SyncWire.encode(SyncPayload.receiptQuery(ids))))
        assertNull(SyncWire.requestId("쓰레기".toByteArray()))
    }

    /** 워치 버전 capability `whistlepro_wear_v<versionCode>`만 버전으로 읽는다 (설치 capability·폰 capability는 아님) */
    @Test fun wearVersionCapabilityParses() {
        assertEquals(226103002L, SyncPaths.wearVersion("whistlepro_wear_v226103002"))
        assertNull(SyncPaths.wearVersion(SyncPaths.CAPABILITY_WEAR))
        assertNull(SyncPaths.wearVersion(SyncPaths.CAPABILITY_MOBILE))
        assertNull(SyncPaths.wearVersion("whistlepro_wear_vabc"))
    }

    @Test fun matchPathCarriesId() {
        val id = UUID.randomUUID()
        val path = SyncPaths.match(id, "t1")
        assertEquals("/whistlepro/match/${id.swiftString}/t1", path)
        assertEquals(id, SyncPaths.matchID(path))
        assertNull(SyncPaths.matchID("/whistlepro/match/abc/t1"))
        assertNull(SyncPaths.matchID(SyncPaths.CONTEXT))
        assertNull(SyncPaths.matchID("/whistlepro/ack/${id.swiftString}"))
        assertEquals("/whistlepro/context", SyncPaths.CONTEXT)
        assertEquals("/whistlepro/ack", SyncPaths.ACK)
        assertEquals("/whistlepro/ack/", SyncPaths.ACK_PREFIX)
        assertEquals("/whistlepro/receipt-query", SyncPaths.RECEIPT_QUERY)
        assertEquals("/whistlepro/receipt-reply", SyncPaths.RECEIPT_REPLY)
        assertEquals("/whistlepro/context-request", SyncPaths.CONTEXT_REQUEST)
        assertEquals("whistlepro_wear", SyncPaths.CAPABILITY_WEAR)
        assertEquals("whistlepro_mobile", SyncPaths.CAPABILITY_MOBILE)
    }

    /** 다른 앱·이전 형식의 바이트는 빈 메시지로 읽는다 (예외를 던지지 않는다) */
    @Test fun foreignBytesDecodeEmpty() {
        assertEquals(emptyMap(), SyncWire.decode("not json".toByteArray()))
        assertEquals(emptyMap(), SyncWire.decode("[1,2]".toByteArray()))
        assertEquals(emptyMap(), SyncWire.decode(byteArrayOf(0xC3.toByte(), 0x28)))
        assertEquals(emptyMap(), SyncWire.decode(ByteArray(0)))
    }

    // ---- 워치 물리 입력 상태 (2026-11-06 Task 11: 워치 → 폰 결제 화면)

    private val galaxy = WatchInputStatus(
        model = "SM-R965N",
        detectable = listOf(InputChannel.BACK_KEY, InputChannel.ROTARY, InputChannel.STEM),
        confirmed = listOf(InputChannel.BACK_KEY, InputChannel.ROTARY),
        unavailable = listOf(InputChannel.BACK_KEY),
        rotaryIsBezel = true,
    )

    @Test fun inputStatusRoundTrip() {
        assertEquals(galaxy, WatchInputStatus.fromMap(roundTrip(galaxy.toMap())))
        val none = WatchInputStatus("Pixel Watch", emptyList(), emptyList(), emptyList(), rotaryIsBezel = false)
        assertEquals(none, WatchInputStatus.fromMap(roundTrip(none.toMap())))
        assertEquals("/whistlepro/input-status", SyncPaths.INPUT_STATUS)
    }

    /** 다음 버전의 모르는 통로 이름은 버리고 나머지는 읽는다. 기종 이름이 없으면 상태가 아니다 */
    @Test fun inputStatusIgnoresUnknownChannel() {
        val map = galaxy.toMap() + mapOf(
            "detectable" to listOf("ROTARY", "LASER"),
            "confirmed" to listOf("TOUCHPAD"),
            "future" to "무시",
        )
        val read = WatchInputStatus.fromMap(roundTrip(map))
        assertEquals(listOf(InputChannel.ROTARY), read?.detectable)
        assertEquals(emptyList(), read?.confirmed)
        assertEquals(listOf(InputChannel.BACK_KEY), read?.unavailable)
        assertNull(WatchInputStatus.fromMap(roundTrip(galaxy.toMap() - "model")))
        assertNull(WatchInputStatus.fromMap(emptyMap()))
    }

    /** 폰 결제 화면·워치 PRO 안내 카드: 지금 쓸 수 있는 확인 통로만, 모두 막혔으면 확인 전 문구, 잡히는 통로가 없으면 지원 안 함 */
    @Test fun inputCardFromStatus() {
        assertEquals(WatchInputCard.Usable("SM-R965N", listOf(InputChannel.ROTARY), rotaryIsBezel = true), galaxy.card)
        assertEquals(WatchInputCard.CheckFirst, galaxy.copy(confirmed = emptyList(), unavailable = emptyList()).card)
        assertEquals(WatchInputCard.CheckFirst, galaxy.copy(confirmed = listOf(InputChannel.BACK_KEY)).card)
        assertEquals(WatchInputCard.Unsupported, WatchInputStatus("X", emptyList(), emptyList(), emptyList(), false).card)
        assertEquals(
            WatchInputCard.Usable("SM-R965N", listOf(InputChannel.BACK_KEY, InputChannel.STEM), rotaryIsBezel = true),
            galaxy.copy(confirmed = listOf(InputChannel.STEM, InputChannel.BACK_KEY), unavailable = emptyList()).card,
        )
    }
}
