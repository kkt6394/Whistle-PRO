package com.aidenstudio.whistlepro.session.design

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 메인 상단 ⚠: 메뉴에 오류 문구가 있으면 메인에서도 알 수 있어야 한다 (Swift MainWarningTests) */
class MainWarningTests {
    @Test fun noWarningWhenAllIsWell() {
        assertFalse(MainWarning.shows(workoutRunning = true, workoutError = null, saveError = null))
    }

    @Test fun warnsWhenWorkoutSessionStopped() {
        assertTrue(MainWarning.shows(workoutRunning = false, workoutError = null, saveError = null))
    }

    @Test fun warnsWhenDataCollectionFailed() {
        assertTrue(MainWarning.shows(workoutRunning = true, workoutError = "권한 없음", saveError = null))
    }

    @Test fun warnsWhenSavingFailed() {
        assertTrue(MainWarning.shows(workoutRunning = true, workoutError = null, saveError = "디스크 가득 참"))
    }
}
