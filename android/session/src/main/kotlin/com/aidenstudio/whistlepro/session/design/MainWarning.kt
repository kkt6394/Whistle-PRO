package com.aidenstudio.whistlepro.session.design

/** 워치 메인 상단 띠의 ⚠ (스펙 §4.1, Swift `MainWarning`). 자세한 내용은 메뉴 페이지에 있다. */
object MainWarning {
    /** 운동 세션이 멈췄거나, 심박·거리 수집이 실패했거나, 경기 저장이 실패했을 때 */
    fun shows(workoutRunning: Boolean, workoutError: String?, saveError: String?): Boolean =
        !workoutRunning || workoutError != null || saveError != null
}
