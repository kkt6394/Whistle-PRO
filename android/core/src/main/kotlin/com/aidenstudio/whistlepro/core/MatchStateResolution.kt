package com.aidenstudio.whistlepro.core

enum class ResolvedEventKind(val code: String) {
    GOAL("goal"), OWN_GOAL("ownGoal"), YELLOW("yellow"), SECOND_YELLOW("secondYellow"),
    RED("red"), SIN_BIN("sinBin"), SUBSTITUTION("substitution"), PENALTY_KICK("penaltyKick"),
}

enum class EventWarning(val code: String) {
    SUBSTITUTION_LIMIT_EXCEEDED("substitutionLimitExceeded"),
    REPEAT_PENALTY_KICKER("repeatPenaltyKicker"),
    PLAYER_ALREADY_SENT_OFF("playerAlreadySentOff"),
    /** 같은 선수의 일시 퇴장이 아직 진행 중이거나 그 선수가 출전 금지일 때 또 기록된 일시 퇴장. 인원·남은 시간·두 번째 판정 어디에도 안 센다. */
    OVERLAPPING_SIN_BIN("overlappingSinBin"),
    /** 교체로 이미 경기장 밖에 있는 선수의 퇴장·일시 퇴장. 기록은 남지만 인원은 줄지 않는다. */
    PLAYER_OFF_PITCH("playerOffPitch"),
    /** 출전 금지(두 번째 일시 퇴장을 마친) 선수를 다시 들여보낸 교체 */
    BARRED_PLAYER_RETURNED("barredPlayerReturned"),
}

data class ResolvedEvent(
    val event: RecordedEvent,
    val kind: ResolvedEventKind,
    val warnings: List<EventWarning>,
    /** 같은 팀·번호의 (겹치지 않은) 일시 퇴장이 앞서 있었던 일시 퇴장. 등번호가 없으면 언제나 false. */
    val isSecondSinBin: Boolean = false,
) {
    /** 퇴장 (이미 퇴장한 선수에게 다시 기록된 카드는 제외). 흐림·일시 퇴장 종료 판정에 쓴다. */
    val isSendingOff: Boolean
        get() = (kind == ResolvedEventKind.RED || kind == ResolvedEventKind.SECOND_YELLOW) &&
            EventWarning.PLAYER_ALREADY_SENT_OFF !in warnings

    /** 인원을 줄이는 퇴장 (교체로 이미 경기장 밖에 있던 선수의 퇴장은 제외) */
    val reducesTeam: Boolean
        get() = isSendingOff && EventWarning.PLAYER_OFF_PITCH !in warnings
}

sealed interface MatchWarning {
    val code: String

    data class BelowMinimumPlayers(val team: Team) : MatchWarning {
        override val code: String get() = "belowMinimumPlayers:${team.code}"
    }
}

internal data class PlayerKey(val team: Team, val number: Long)

/** 이벤트를 기록 순서대로 해석한다. 수정·삭제가 있으면 다시 계산되므로 저장하지 않는다. */
val MatchState.resolvedEvents: List<ResolvedEvent>
    get() {
        val cautioned = mutableSetOf<PlayerKey>()
        var subs = TeamPair(0, 0)
        // 인원을 줄인 퇴장 수 (reducesTeam)
        var sentOff = TeamPair(0, 0)
        val dismissed = mutableSetOf<PlayerKey>()
        val kickers = TeamPair(mutableSetOf<Long>(), mutableSetOf<Long>())
        // 교체로 나가 있는 선수
        val offPitch = mutableSetOf<PlayerKey>()
        // 겹치지 않은 마지막 일시 퇴장 시각
        val lastSinBinAt = mutableMapOf<PlayerKey, Double>()
        // 첫 두 번째 일시 퇴장 시각 (출전 금지 판정)
        val secondSinBinAt = mutableMapOf<PlayerKey, Double>()
        // 출전 금지 선수를 나가는 선수로 고른 교체가 있었음
        val filled = mutableSetOf<PlayerKey>()
        val sinBinLength = rules.sinBinLength.toDouble()
        val extraStart = extraTimeReachedAt
        return events.map { e ->
            val warnings = mutableListOf<EventWarning>()
            var isSecondSinBin = false
            val kind: ResolvedEventKind
            when (val p = e.payload) {
                is EventPayload.Goal -> kind = if (p.ownGoal) ResolvedEventKind.OWN_GOAL else ResolvedEventKind.GOAL
                is EventPayload.Card -> {
                    val player = p.number?.let { PlayerKey(p.team, it) }
                    if (player != null && player in dismissed) {
                        // 이미 퇴장한 선수: 경고 2장 뒤 레드를 따로 기록했거나 등번호를 잘못 넣은 경우. 인원에 다시 반영하지 않는다.
                        kind = when (p.kind) {
                            CardKind.RED -> ResolvedEventKind.RED
                            CardKind.SIN_BIN -> ResolvedEventKind.SIN_BIN
                            CardKind.YELLOW -> ResolvedEventKind.YELLOW
                        }
                        warnings += EventWarning.PLAYER_ALREADY_SENT_OFF
                    } else {
                        kind = when (p.kind) {
                            CardKind.RED -> ResolvedEventKind.RED
                            CardKind.SIN_BIN -> ResolvedEventKind.SIN_BIN
                            CardKind.YELLOW ->
                                if (rules.secondYellowSendOff && player != null && !cautioned.add(player)) {
                                    ResolvedEventKind.SECOND_YELLOW
                                } else {
                                    ResolvedEventKind.YELLOW
                                }
                        }
                        if (kind == ResolvedEventKind.SIN_BIN && player != null) {
                            val last = lastSinBinAt[player]
                            val second = secondSinBinAt[player]
                            if (second != null && playedSeconds(second, e.timestamp) >= sinBinLength) {
                                // 출전 금지 선수(두 번째 일시 퇴장을 마침)에게 또 일시 퇴장: 이미 빠진 사람이라 겹친 것으로 본다
                                warnings += EventWarning.OVERLAPPING_SIN_BIN
                            } else if (player in offPitch) {
                                // 교체로 나간 선수의 일시 퇴장: 경기장 인원은 그대로
                                warnings += EventWarning.PLAYER_OFF_PITCH
                            } else if (last != null && playedSeconds(last, e.timestamp) < sinBinLength) {
                                // 일시 퇴장 중에 또 일시 퇴장: 한 사람은 한 번만 빠진다 (IFAB 지침에는 겹치는 일시 퇴장이 없다)
                                warnings += EventWarning.OVERLAPPING_SIN_BIN
                            } else {
                                isSecondSinBin = last != null
                                lastSinBinAt[player] = e.timestamp
                                if (isSecondSinBin && player !in secondSinBinAt) secondSinBinAt[player] = e.timestamp
                            }
                        }
                        if (kind == ResolvedEventKind.RED || kind == ResolvedEventKind.SECOND_YELLOW) {
                            if (player != null && player in offPitch) {
                                // 교체로 나간 선수의 퇴장: 경기장 인원은 그대로
                                warnings += EventWarning.PLAYER_OFF_PITCH
                            } else {
                                sentOff = sentOff.with(p.team, sentOff[p.team] + 1)
                            }
                            if (player != null) dismissed += player
                        }
                    }
                }
                is EventPayload.Substitution -> {
                    kind = ResolvedEventKind.SUBSTITUTION
                    subs = subs.with(p.team, subs[p.team] + 1)
                    val limit = substitutionLimit(e.timestamp, extraStart)
                    if (limit != null && subs[p.team] > limit) warnings += EventWarning.SUBSTITUTION_LIMIT_EXCEEDED
                    p.numberOut?.let {
                        val player = PlayerKey(p.team, it)
                        offPitch += player
                        if (player in secondSinBinAt) filled += player
                    }
                    p.numberIn?.let {
                        val player = PlayerKey(p.team, it)
                        val second = secondSinBinAt[player]
                        if (second != null && player !in dismissed && playedSeconds(second, e.timestamp) >= sinBinLength) {
                            warnings += EventWarning.BARRED_PLAYER_RETURNED
                        }
                        offPitch -= player
                    }
                }
                is EventPayload.PenaltyKick -> {
                    kind = ResolvedEventKind.PENALTY_KICK
                    // 일시 퇴장 중인 선수도 키커 자격이 있으므로 퇴장 인원과 채우지 않은 출전 금지 선수만 뺀다 (Law 10.3)
                    if (p.number != null) {
                        val barred = secondSinBinAt.keys.count { it.team == p.team && it !in dismissed && it !in filled }
                        val eligible = rules.playersPerTeam - sentOff[p.team] - barred
                        if (p.number in kickers[p.team] && kickers[p.team].size < eligible) {
                            warnings += EventWarning.REPEAT_PENALTY_KICKER
                        }
                        kickers[p.team] += p.number
                    }
                }
            }
            ResolvedEvent(e, kind, warnings, isSecondSinBin)
        }
    }

val MatchState.substitutionCounts: TeamPair<Int>
    get() {
        var counts = TeamPair(0, 0)
        for (e in events) {
            val p = e.payload as? EventPayload.Substitution ?: continue
            counts = counts.with(p.team, counts[p.team] + 1)
        }
        return counts
    }

/** 인원을 줄인 퇴장(레드, 경고 누적) 수. 교체로 나간 선수의 퇴장은 세지 않는다. */
val MatchState.sentOffCounts: TeamPair<Int>
    get() {
        var counts = TeamPair(0, 0)
        for (r in resolvedEvents) {
            if (!r.reducesTeam) continue
            val team = r.event.payload.team
            counts = counts.with(team, counts[team] + 1)
        }
        return counts
    }

fun MatchState.playersOnField(now: Double): TeamPair<Long> {
    val sent = sentOffCounts
    var onField = TeamPair(rules.playersPerTeam - sent.home, rules.playersPerTeam - sent.away)
    for (s in activeSinBins(now)) onField = onField.with(s.team, onField[s.team] - 1)
    // 두 번째 일시 퇴장을 마친 선수는 교체로 채울 때까지 한 명 부족
    for (b in barredPlayers(now)) if (!b.filled) onField = onField.with(b.team, onField[b.team] - 1)
    return onField
}

fun MatchState.matchWarnings(now: Double): List<MatchWarning> {
    val minimum = rules.minimumPlayers ?: return emptyList()
    val onField = playersOnField(now)
    return Team.entries.filter { onField[it] < minimum }.map { MatchWarning.BelowMinimumPlayers(it) }
}

/** 연장전에 들어간 경우 마지막 정규 기간이 끝난 시각 */
internal val MatchState.extraTimeReachedAt: Double?
    get() {
        if (!rules.extraTime) return null
        val lastRegular = periods.firstOrNull { it.ref.kind == PeriodRef.Kind.REGULAR && it.ref.index.toLong() == rules.periodCount } ?: return null
        val end = lastRegular.endedAt ?: return null
        if (!(phase == Phase.PreExtraTime || periods.any { it.ref.kind == PeriodRef.Kind.EXTRA })) return null
        return end
    }

/** Long으로 더한다: validate() 범위 밖 규칙에서 Int가 넘치면 Swift(64비트 Int)와 결과가 달라진다. */
private fun MatchState.substitutionLimit(t: Double, extraStart: Double?): Long? {
    if (rules.returnSubstitutions) return null
    val limit = rules.substitutionLimit?.toLong() ?: return null
    if (extraStart != null && t >= extraStart) return limit + rules.extraTimeExtraSubs
    return limit
}
