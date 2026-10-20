package com.aidenstudio.whistlepro.session

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 워치가 폰의 "받았다" 확인을 놓쳐도 다시 받게 하는 보조 장치 (Swift DeliveryRetryTests와 같은 이름) */
class DeliveryRetryTests {
    /** 앱이 화면에 나타날 때마다 다시 보내되, 경기 중 손목을 들 때마다 보내지는 않는다 */
    @Test fun resendOnActivationIsThrottled() {
        val throttle = ResendThrottle(30.0)
        val results = listOf(100.0, 110.0, 129.0, 130.0).map { throttle.allows(it) }
        assertEquals(listOf(true, false, false, true), results)
    }

    // waitsUntilConditionHolds·waitGivesUpAfterTimeout: Swift waitUntil은 안드로이드에서 코루틴 withTimeoutOrNull이 대신하므로 옮기지 않는다
}

/** 경기 확인 누락 후속: 폰 앱이 꺼져 있는 동안 워치가 화면에 나타날 때마다 같은 경기를 다시 보냈다 */
class MatchSendThrottleTests {
    @Test fun sameMatchIsNotResentWithinInterval() {
        val throttle = MatchSendThrottle(600.0)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertTrue(throttle.allows(a, 0.0))
        assertFalse(throttle.allows(a, 30.0))
        assertTrue(throttle.allows(b, 30.0))
        assertTrue(throttle.allows(a, 601.0))
    }

    /** 전송이 실패하면 바로 다시 보낼 수 있다 */
    @Test fun failureClearsTheMatch() {
        val throttle = MatchSendThrottle(600.0)
        val a = UUID.randomUUID()
        assertTrue(throttle.allows(a, 0.0))
        throttle.failed(a)
        assertTrue(throttle.allows(a, 1.0))
    }
}

/** 경기 확인 누락 후속: 워치가 활성화·reachable 될 때 "이 경기들 받았니?"를 폰에 묻는다 */
class ReceiptCheckTests {
    @Test fun asksPendingMatches() {
        val check = ReceiptCheck(60.0)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertEquals(listOf(a, b), check.begin(listOf(a, b), 0.0))
        assertTrue(check.inFlight)
    }

    @Test fun doesNotAskWithoutPendingMatches() {
        val check = ReceiptCheck(60.0)
        assertNull(check.begin(emptyList(), 0.0))
        assertFalse(check.inFlight)
    }

    /** reachable이 깜빡여도 답을 기다리는 동안은 다시 묻지 않는다 */
    @Test fun doesNotOverlapWhileWaitingForReply() {
        val check = ReceiptCheck(60.0)
        val a = UUID.randomUUID()
        assertNotNull(check.begin(listOf(a), 0.0))
        assertNull(check.begin(listOf(a), 1.0))
    }

    /** 답에 든 경기만 확인으로 본다 (묻지 않은 ID는 무시) */
    @Test fun confirmsOnlyAskedMatchesInReply() {
        val check = ReceiptCheck(60.0)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        val asked = check.begin(listOf(a, b), 0.0)!!
        val confirmed = check.answered(asked, listOf(b, stranger), 1.0)
        assertEquals(listOf(b), confirmed)
        assertFalse(check.inFlight)
    }

    /** 답을 받은 뒤에는 간격 안에 다시 묻지 않는다 */
    @Test fun waitsIntervalAfterAnswer() {
        val check = ReceiptCheck(60.0)
        val a = UUID.randomUUID()
        val asked = check.begin(listOf(a), 0.0)!!
        check.answered(asked, emptyList(), 1.0)
        assertNull(check.begin(listOf(a), 30.0))
        assertEquals(listOf(a), check.begin(listOf(a), 61.0))
    }

    /** 실패하면 다음 기회에 바로 다시 묻는다 */
    @Test fun failureAllowsAskingAgain() {
        val check = ReceiptCheck(60.0)
        val a = UUID.randomUUID()
        assertNotNull(check.begin(listOf(a), 0.0))
        check.failed()
        assertEquals(listOf(a), check.begin(listOf(a), 1.0))
    }

    /** 메시지 한도 안에 들게 한 번에 묻는 수를 제한한다 (Swift는 plist 크기, 여기서는 Data Layer로 싣는 JSON 바이트 크기) */
    @Test fun limitsIDsPerQuery() {
        val check = ReceiptCheck(60.0)
        val many = List(ReceiptCheck.MAX_IDS + 10) { UUID.randomUUID() }
        val asked = assertNotNull(check.begin(many, 0.0))
        assertEquals(many.take(ReceiptCheck.MAX_IDS), asked)
        val size = SyncWire.encode(SyncPayload.receiptQuery(asked)).size
        assertTrue(size < 65_536, "크기 $size")
    }

    /** 디스크를 읽기 전에 물을 수 있는지부터 본다: 답 대기 중이거나 간격 안이면 false, 상태는 바꾸지 않는다 */
    @Test fun canAskWithoutTouchingState() {
        val check = ReceiptCheck(60.0)
        val a = UUID.randomUUID()
        assertTrue(check.canAsk(0.0))
        assertTrue(check.canAsk(0.0))
        assertFalse(check.inFlight)
        val asked = check.begin(listOf(a), 0.0)!!
        assertFalse(check.canAsk(1.0))
        check.answered(asked, emptyList(), 1.0)
        assertFalse(check.canAsk(30.0))
        assertTrue(check.canAsk(61.0))
        check.failed()
        assertTrue(check.canAsk(61.0))
    }

    /** 물은 경기 중 폰이 받았다고 한 것만 지운다: 묻고 난 뒤 보관된 경기는 폰이 받았다고 해도 남긴다 */
    @Test fun deletesOnlyAskedMatchesNotThoseArchivedAfterAsking() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val later = UUID.randomUUID()
        assertEquals(listOf(b), ReceiptCheck.toDelete(listOf(a, b), listOf(b, later)))
        assertEquals(emptyList(), ReceiptCheck.toDelete(listOf(a), emptyList()))
    }

    /**
     * 앱을 켤 때 다시 보내기: 폰에 닿고 물을 수 있거나 답을 기다리는 중이면 답(또는 실패) 뒤에 보낸다 — 이미 받은 경기를 다시 보내지 않게.
     * 닿지 않거나 간격 안이라 묻지 않으면 바로 보낸다
     */
    @Test fun resendWaitsForReceiptAnswerOnlyWhenAsking() {
        val check = ReceiptCheck(60.0)
        assertFalse(check.sendsAfterAnswer(false, 0.0))
        assertTrue(check.sendsAfterAnswer(true, 0.0))
        val asked = check.begin(listOf(UUID.randomUUID()), 0.0)!!
        assertTrue(check.sendsAfterAnswer(true, 1.0))
        assertFalse(check.sendsAfterAnswer(false, 1.0))
        check.answered(asked, emptyList(), 1.0)
        assertFalse(check.sendsAfterAnswer(true, 30.0))
        assertTrue(check.sendsAfterAnswer(true, 61.0))
    }
}
