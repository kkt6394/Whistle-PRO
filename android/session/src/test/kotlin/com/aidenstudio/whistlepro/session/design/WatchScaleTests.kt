package com.aidenstudio.whistlepro.session.design

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchScaleTests {
    /** 가장 작은 Wear 원형(지름 192dp)을 1로 본다 */
    @Test fun fortyMillimetreIsOne() {
        assertEquals(1.0, WatchScale(192.0).factor)
        assertEquals(1.0, WatchScale(190.0).factor)
    }

    /** 갤럭시 워치 44mm급(240dp)은 1.25 */
    @Test fun ultraIsAboutOnePointTwoFive() {
        assertTrue(abs(WatchScale(240.0).factor - 1.25) < 0.0001)
        assertTrue(WatchScale(241.0).factor > 1.25)
    }

    @Test fun scaledMultiplies() {
        assertEquals(20.0, WatchScale(384.0).scaled(10.0))
        assertEquals(10.0, WatchScale(0.0).scaled(10.0))
    }

    @Test fun neverBelowOne() {
        assertEquals(1.0, WatchScale(180.0).factor)
    }
}
