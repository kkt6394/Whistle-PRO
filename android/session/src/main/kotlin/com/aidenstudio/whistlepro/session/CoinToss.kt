package com.aidenstudio.whistlepro.session

import kotlin.random.Random

/**
 * 동전 던지기 (경기 시작·연장 전·승부차기 전). 결과는 화면에만 보여 주고 경기 기록에는 남기지 않는다.
 * 이긴 팀이 무엇을 고를지(선축·진영, 먼저/나중에 차기)는 심판이 직접 누른다.
 */
enum class CoinSide {
    HEADS, TAILS;

    companion object {
        fun toss(random: Random = Random.Default): CoinSide = if (random.nextBoolean()) HEADS else TAILS

        /** 결과가 나올 때 한 번. 경기 알림 진동과 헷갈리지 않는 종류 (이벤트 기록과 같지만 동전 시트가 떠 있으면 기록할 수 없다) */
        val RESULT_HAPTIC = HapticPattern(HapticTone.SUCCESS, 1, 0.0)
    }
}
