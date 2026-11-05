package com.aidenstudio.whistlepro.core

data class ActiveSinBin(
    val eventId: String,
    val team: Team,
    val number: Long?,
    /** 남은 일시 퇴장 시간(초). 경기 시계 기준이라 휴식 중에는 줄지 않는다. */
    val remainingSeconds: Double,
    /** 두 번째 일시 퇴장 (끝나면 출전 금지) */
    val isSecond: Boolean = false,
) {
    /** 화면 표시용 (올림) */
    val remaining: Long get() = wholeSeconds(remainingSeconds, roundUp = true)
}

/** 두 번째 일시 퇴장 시간을 마쳐 남은 경기에 못 나오는 선수 (IFAB 일시 퇴장 지침, Swift BarredPlayer와 같음) */
data class BarredPlayer(
    /** 두 번째 일시 퇴장 이벤트 */
    val eventId: String,
    val team: Team,
    val number: Long,
    /** 그 선수를 나가는 선수로 고른 교체가 기록됨 (팀 인원이 다시 참) */
    val filled: Boolean,
)

/** start~now 사이에 실제로 기간이 진행된 시간(휴식 제외) */
internal fun MatchState.playedSeconds(start: Double, now: Double): Double = periods.fold(0.0) { total, p ->
    val from = swiftMax(p.startedAt, start)
    val to = swiftMin(p.endedAt ?: now, now)
    total + swiftMax(0.0, to - from)
}

private val notCounted = setOf(EventWarning.OVERLAPPING_SIN_BIN, EventWarning.PLAYER_OFF_PITCH, EventWarning.PLAYER_ALREADY_SENT_OFF)

fun MatchState.activeSinBins(now: Double): List<ActiveSinBin> {
    // 승부차기·종료 후에는 일시 퇴장이 이어지지 않는다 (Law 10.3: 일시 퇴장 중인 선수도 키커 자격)
    if (phase == Phase.Penalties || phase is Phase.Finished) return emptyList()
    val resolved = resolvedEvents
    return resolved.mapIndexedNotNull { index, r ->
        val card = r.event.payload as? EventPayload.Card
        // 이미 퇴장·출전 금지·교체로 나간 선수의 일시 퇴장은 인원에 다시 반영하지 않는다 (한 사람은 한 번만 빠진다)
        if (r.kind != ResolvedEventKind.SIN_BIN || card == null || r.warnings.any { it in notCounted }) {
            return@mapIndexedNotNull null
        }
        // 일시 퇴장 중 퇴장당하면 일시 퇴장은 끝나고 퇴장 인원으로만 센다
        if (card.number != null && resolved.drop(index + 1).any {
                it.isSendingOff && it.event.payload.team == card.team && it.event.payload.shirtNumbers.firstOrNull() == card.number
            }
        ) return@mapIndexedNotNull null
        val left = rules.sinBinLength - playedSeconds(r.event.timestamp, now)
        if (!(left > 0)) return@mapIndexedNotNull null // NaN이면 Swift guard처럼 제외
        ActiveSinBin(r.event.id, card.team, card.number, left, r.isSecondSinBin)
    }
}

/**
 * 두 번째 일시 퇴장 시간이 끝났고 그 뒤 퇴장당하지 않은 선수 (기록 순서).
 * 승부차기·종료 상태에서는 시간과 상관없이 끝난 것으로 본다. Swift barredPlayers(at:)와 같음.
 */
fun MatchState.barredPlayers(now: Double): List<BarredPlayer> {
    val allEnded = phase == Phase.Penalties || phase is Phase.Finished
    val resolved = resolvedEvents
    val seen = mutableSetOf<PlayerKey>()
    val result = mutableListOf<BarredPlayer>()
    resolved.forEachIndexed { index, r ->
        val card = r.event.payload as? EventPayload.Card ?: return@forEachIndexed
        val number = card.number ?: return@forEachIndexed
        if (!r.isSecondSinBin || !seen.add(PlayerKey(card.team, number))) return@forEachIndexed
        val later = resolved.drop(index + 1)
        // 그 뒤 퇴장당하면 퇴장으로만 센다
        if (later.any { it.isSendingOff && it.event.payload.team == card.team && it.event.payload.shirtNumbers.firstOrNull() == number }) {
            return@forEachIndexed
        }
        if (!allEnded && !(playedSeconds(r.event.timestamp, now) >= rules.sinBinLength)) return@forEachIndexed
        val filled = later.any {
            val sub = it.event.payload as? EventPayload.Substitution
            sub != null && sub.team == card.team && sub.numberOut == number
        }
        result += BarredPlayer(r.event.id, card.team, number, filled)
    }
    return result
}
