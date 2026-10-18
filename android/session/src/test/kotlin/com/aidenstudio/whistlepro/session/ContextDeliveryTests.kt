package com.aidenstudio.whistlepro.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 폰 → 워치 구독·프리셋 전달: 워치 앱이 새로 설치되면 마지막 내용을 다시 보낸다. (Swift ContextDeliveryTests와 같음)
 * (실기기: 워치 앱을 다시 설치하면 폰 앱을 다시 열 때까지 워치가 잠겨 있었다)
 */
class ContextDeliveryTests {
    @Test fun sendsImmediatelyWhenWatchIsReachable() {
        val delivery = ContextDelivery<String>()
        delivery.watchStateChanged(installed = true, canDeliver = true)
        assertEquals("pro", delivery.send("pro", canDeliver = true))
    }

    @Test fun holdsUntilWatchAppIsInstalled() {
        val delivery = ContextDelivery<String>()
        assertNull(delivery.send("pro", canDeliver = false))
        assertEquals("pro", delivery.watchStateChanged(installed = true, canDeliver = true))
    }

    @Test fun resendsLastContextWhenWatchAppIsReinstalled() {
        val delivery = ContextDelivery<String>()
        delivery.watchStateChanged(installed = true, canDeliver = true)
        assertEquals("pro", delivery.send("pro", canDeliver = true))
        // 워치 앱 삭제 → 다시 설치
        assertNull(delivery.watchStateChanged(installed = false, canDeliver = false))
        assertEquals("pro", delivery.watchStateChanged(installed = true, canDeliver = true))
    }

    @Test fun noResendWhenNothingChanged() {
        val delivery = ContextDelivery<String>()
        delivery.watchStateChanged(installed = true, canDeliver = true)
        delivery.send("pro", canDeliver = true)
        assertNull(delivery.watchStateChanged(installed = true, canDeliver = true))
    }

    @Test fun nothingToResendBeforeFirstSend() {
        val delivery = ContextDelivery<String>()
        assertNull(delivery.watchStateChanged(installed = true, canDeliver = true))
    }
}
