package com.aidenstudio.whistlepro.session

/** 리플레이 영상 점의 화면 위치와 크기 */
data class ReplayScreenPoint(val x: Double, val y: Double, val scale: Double)

/**
 * 리플레이 영상의 그림 경기장 원근. 경기장 좌표(`pitch.local`)에서 B 골라인이 위, 먼 쪽(위)이 작다.
 * 시안 replay-layout.html의 `project`와 같은 식이다.
 */
class ReplayPerspective(val pitch: Pitch, val width: Double, val height: Double) {
    // 여백을 포함한 경기장 폭이 화면 폭을 채운다
    private val zoom = width / (pitch.width + 2 * MARGIN)

    fun point(p: GeoPoint): ReplayScreenPoint {
        val local = pitch.local(p)
        val dx = (local.x - pitch.width / 2) * zoom
        // 음수 = 먼 쪽(위)
        val dy = (pitch.length / 2 - local.y) * zoom
        val depth = 1 - dy / (height * 1.6)
        val scale = 1 / depth.coerceIn(0.6, 2.2)
        return ReplayScreenPoint(width / 2 + dx * scale, height * CENTER_Y + dy * TILT * scale, scale)
    }

    private companion object {
        /** 경기장 둘레 여백 (m) */
        const val MARGIN = 14.0
        const val TILT = 0.62
        /** 위 로고·점수 띠(약 530px까지) 아래로 내렸다 (critique 11-08, 0.44 → 0.487 = 90px) */
        const val CENTER_Y = 0.487
    }
}
