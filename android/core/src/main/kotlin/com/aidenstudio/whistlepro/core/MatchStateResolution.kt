package com.aidenstudio.whistlepro.core

enum class ResolvedEventKind(val code: String) {
    GOAL("goal"), OWN_GOAL("ownGoal"), YELLOW("yellow"), SECOND_YELLOW("secondYellow"),
    RED("red"), SIN_BIN("sinBin"), SUBSTITUTION("substitution"), PENALTY_KICK("penaltyKick"),
}

enum class EventWarning(val code: String) {
    SUBSTITUTION_LIMIT_EXCEEDED("substitutionLimitExceeded"),
    REPEAT_PENALTY_KICKER("repeatPenaltyKicker"),
    PLAYER_ALREADY_SENT_OFF("playerAlreadySentOff"),
}

data class ResolvedEvent(val event: RecordedEvent, val kind: ResolvedEventKind, val warnings: List<EventWarning>) {
    /** 인원을 줄이는 퇴장 (이미 퇴장한 선수에게 다시 기록된 카드는 제외) */
    val isSendingOff: Boolean
        get() = (kind == ResolvedEventKind.RED || kind == ResolvedEventKind.SECOND_YELLOW) &&
            EventWarning.PLAYER_ALREADY_SENT_OFF !in warnings
}

sealed interface MatchWarning {
    val code: String

    data class BelowMinimumPlayers(val team: Team) : MatchWarning {
        override val code: String get() = "belowMinimumPlayers:${team.code}"
    }
}

private data class PlayerKey(val team: Team, val number: Int)

/** 이벤트를 기록 순서대로 해석한다. 수정·삭제가 있으면 다시 계산되므로 저장하지 않는다. */
val MatchState.resolvedEvents: List<ResolvedEvent>
    get() {
        val cautioned = mutableSetOf<PlayerKey>()
        var subs = TeamPair(0, 0)
        var sentOff = TeamPair(0, 0)
        val dismissed = mutableSetOf<PlayerKey>()
        val kickers = TeamPair(mutableSetOf<Int>(), mutableSetOf<Int>())
        val extraStart = extraTimeReachedAt
        return events.map { e ->
            val warnings = mutableListOf<EventWarning>()
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
                        if (kind == ResolvedEventKind.RED || kind == ResolvedEventKind.SECOND_YELLOW) {
                            sentOff = sentOff.with(p.team, sentOff[p.team] + 1)
                            if (player != null) dismissed += player
                        }
                    }
                }
                is EventPayload.Substitution -> {
                    kind = ResolvedEventKind.SUBSTITUTION
                    subs = subs.with(p.team, subs[p.team] + 1)
                    val limit = substitutionLimit(e.timestamp, extraStart)
                    if (limit != null && subs[p.team] > limit) warnings += EventWarning.SUBSTITUTION_LIMIT_EXCEEDED
                }
                is EventPayload.PenaltyKick -> {
                    kind = ResolvedEventKind.PENALTY_KICK
                    // 일시 퇴장 중인 선수도 키커 자격이 있으므로 퇴장 인원만 뺀다 (Law 10.3)
                    if (p.number != null) {
                        val eligible = rules.playersPerTeam - sentOff[p.team]
                        if (p.number in kickers[p.team] && kickers[p.team].size < eligible) {
                            warnings += EventWarning.REPEAT_PENALTY_KICKER
                        }
                        kickers[p.team] += p.number
                    }
                }
            }
            ResolvedEvent(e, kind, warnings)
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

/** 퇴장(레드, 경고 누적) 인원 */
val MatchState.sentOffCounts: TeamPair<Int>
    get() {
        var counts = TeamPair(0, 0)
        for (r in resolvedEvents) {
            if (!r.isSendingOff) continue
            val team = r.event.payload.team
            counts = counts.with(team, counts[team] + 1)
        }
        return counts
    }

fun MatchState.playersOnField(now: Double): TeamPair<Int> {
    val sent = sentOffCounts
    var onField = TeamPair(rules.playersPerTeam - sent.home, rules.playersPerTeam - sent.away)
    for (s in activeSinBins(now)) onField = onField.with(s.team, onField[s.team] - 1)
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
        val lastRegular = periods.firstOrNull { it.ref == PeriodRef(PeriodRef.Kind.REGULAR, rules.periodCount) } ?: return null
        val end = lastRegular.endedAt ?: return null
        if (!(phase == Phase.PreExtraTime || periods.any { it.ref.kind == PeriodRef.Kind.EXTRA })) return null
        return end
    }

private fun MatchState.substitutionLimit(t: Double, extraStart: Double?): Int? {
    if (rules.returnSubstitutions) return null
    val limit = rules.substitutionLimit ?: return null
    if (extraStart != null && t >= extraStart) return limit + rules.extraTimeExtraSubs
    return limit
}
