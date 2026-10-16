package com.aidenstudio.whistlepro.core

data class ActiveSinBin(
    val eventId: String,
    val team: Team,
    val number: Long?,
    /** 남은 일시 퇴장 시간(초). 경기 시계 기준이라 휴식 중에는 줄지 않는다. */
    val remainingSeconds: Double,
) {
    /** 화면 표시용 (올림) */
    val remaining: Long get() = wholeSeconds(remainingSeconds, roundUp = true)
}

/** start~now 사이에 실제로 기간이 진행된 시간(휴식 제외) */
internal fun MatchState.playedSeconds(start: Double, now: Double): Double = periods.fold(0.0) { total, p ->
    val from = swiftMax(p.startedAt, start)
    val to = swiftMin(p.endedAt ?: now, now)
    total + swiftMax(0.0, to - from)
}

fun MatchState.activeSinBins(now: Double): List<ActiveSinBin> {
    // 승부차기·종료 후에는 일시 퇴장이 이어지지 않는다 (Law 10.3: 일시 퇴장 중인 선수도 키커 자격)
    if (phase == Phase.Penalties || phase is Phase.Finished) return emptyList()
    val resolved = resolvedEvents
    return resolved.mapIndexedNotNull { index, r ->
        val card = r.event.payload as? EventPayload.Card
        if (r.kind != ResolvedEventKind.SIN_BIN || card == null) return@mapIndexedNotNull null
        // 일시 퇴장 중 퇴장당하면 일시 퇴장은 끝나고 퇴장 인원으로만 센다
        if (card.number != null && resolved.drop(index + 1).any {
                it.isSendingOff && it.event.payload.team == card.team && it.event.payload.shirtNumbers.firstOrNull() == card.number
            }
        ) return@mapIndexedNotNull null
        val left = rules.sinBinLength - playedSeconds(r.event.timestamp, now)
        if (!(left > 0)) return@mapIndexedNotNull null // NaN이면 Swift guard처럼 제외
        ActiveSinBin(r.event.id, card.team, card.number, left)
    }
}
