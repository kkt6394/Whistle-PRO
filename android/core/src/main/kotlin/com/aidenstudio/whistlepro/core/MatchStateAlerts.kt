package com.aidenstudio.whistlepro.core

enum class AlertKind(val code: String) {
    WARNING_BEFORE_END("warningBeforeEnd"), PERIOD_END("periodEnd"),
    BREAK_ENDING_SOON("breakEndingSoon"), BREAK_END("breakEnd"), SIN_BIN_END("sinBinEnd"),
}

data class MatchAlert(val kind: AlertKind, val at: Double)

/**
 * now 이후에 울려야 할 알림(시각순). 진동은 앱이 실행한다.
 * warningBeforeEnd: 종료 N초 전 예고 (0이면 끔, 기간 길이 이상이면 생략)
 */
fun MatchState.alerts(now: Double, warningBeforeEnd: Long): List<MatchAlert> {
    val result = mutableListOf<MatchAlert>()
    when (val phase = phase) {
        is Phase.Period -> periods.lastOrNull()?.let { p ->
            val end = p.startedAt + p.nominalLength
            if (warningBeforeEnd > 0 && warningBeforeEnd < p.nominalLength) {
                result += MatchAlert(AlertKind.WARNING_BEFORE_END, end - warningBeforeEnd)
            }
            result += MatchAlert(AlertKind.PERIOD_END, end)
            result += activeSinBins(now).map { MatchAlert(AlertKind.SIN_BIN_END, now + it.remainingSeconds) }
        }
        is Phase.Interval -> periods.lastOrNull()?.endedAt?.let { end ->
            val length = intervalLength(phase.after)
            if (length > 0) {
                // 휴식이 2분 미만이면 "1분 전" 알림은 생략
                if (length >= 120) result += MatchAlert(AlertKind.BREAK_ENDING_SOON, end + (length - 60))
                result += MatchAlert(AlertKind.BREAK_END, end + length)
            }
        }
        Phase.NotStarted, Phase.PreExtraTime, Phase.Penalties, is Phase.Finished -> Unit
    }
    return result.filter { it.at > now }.sortedBy { it.at }
}

fun MatchState.nextAlert(now: Double, warningBeforeEnd: Long): MatchAlert? = alerts(now, warningBeforeEnd).firstOrNull()
