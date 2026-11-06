public enum ResolvedEventKind: String, Sendable {
    case goal, ownGoal, yellow, secondYellow, red, sinBin, substitution, penaltyKick
}

public enum EventWarning: String, Sendable {
    case substitutionLimitExceeded, repeatPenaltyKicker, playerAlreadySentOff
    /// 같은 선수의 일시 퇴장이 아직 진행 중이거나 그 선수가 출전 금지일 때 또 기록된 일시 퇴장. 인원·남은 시간·두 번째 판정 어디에도 안 센다.
    case overlappingSinBin
    /// 교체로 이미 경기장 밖에 있는 선수의 퇴장·일시 퇴장. 기록은 남지만 인원은 줄지 않는다.
    case playerOffPitch
    /// 출전 금지(두 번째 일시 퇴장을 마친) 선수를 다시 들여보낸 교체
    case barredPlayerReturned
}

public struct ResolvedEvent: Equatable, Sendable {
    public var event: RecordedEvent
    public var kind: ResolvedEventKind
    public var warnings: [EventWarning]
    /// 같은 팀·번호의 (겹치지 않은) 일시 퇴장이 앞서 있었던 일시 퇴장. 등번호가 없으면 언제나 false.
    public var isSecondSinBin: Bool = false

    /// 퇴장 (이미 퇴장한 선수에게 다시 기록된 카드는 제외). 흐림·일시 퇴장 종료 판정에 쓴다.
    public var isSendingOff: Bool {
        (kind == .red || kind == .secondYellow) && !warnings.contains(.playerAlreadySentOff)
    }

    /// 인원을 줄이는 퇴장 (교체로 이미 경기장 밖에 있던 선수의 퇴장은 제외)
    public var reducesTeam: Bool {
        isSendingOff && !warnings.contains(.playerOffPitch)
    }
}

public enum MatchWarning: Equatable, Sendable {
    case belowMinimumPlayers(Team)

    public var code: String {
        switch self {
        case .belowMinimumPlayers(let team): "belowMinimumPlayers:\(team.rawValue)"
        }
    }
}

struct PlayerKey: Hashable {
    let team: Team
    let number: Int
}

extension MatchState {
    /// 이벤트를 기록 순서대로 해석한다. 수정·삭제가 있으면 다시 계산되므로 저장하지 않는다.
    public var resolvedEvents: [ResolvedEvent] {
        var cautioned: Set<PlayerKey> = []
        var subs = TeamPair(home: 0, away: 0)
        // 인원을 줄인 퇴장 수 (reducesTeam)
        var sentOff = TeamPair(home: 0, away: 0)
        var dismissed: Set<PlayerKey> = []
        var kickers = TeamPair<Set<Int>>(home: [], away: [])
        // 교체로 나가 있는 선수
        var offPitch: Set<PlayerKey> = []
        // 겹치지 않은 마지막 일시 퇴장 시각
        var lastSinBinAt: [PlayerKey: Double] = [:]
        // 첫 두 번째 일시 퇴장 시각 (출전 금지 판정)
        var secondSinBinAt: [PlayerKey: Double] = [:]
        // 출전 금지 선수를 나가는 선수로 고른 교체가 있었음
        var filled: Set<PlayerKey> = []
        let sinBinLength = Double(rules.sinBinLength)
        let extraStart = extraTimeReachedAt
        return events.map { e in
            var warnings: [EventWarning] = []
            var isSecondSinBin = false
            let kind: ResolvedEventKind
            switch e.payload {
            case .goal(_, _, let ownGoal):
                kind = ownGoal ? .ownGoal : .goal
            case .card(let team, let number, let card):
                let player = number.map { PlayerKey(team: team, number: $0) }
                if let player, dismissed.contains(player) {
                    // 이미 퇴장한 선수: 경고 2장 뒤 레드를 따로 기록했거나 등번호를 잘못 넣은 경우. 인원에 다시 반영하지 않는다.
                    kind = switch card {
                    case .red: .red
                    case .sinBin: .sinBin
                    case .yellow: .yellow
                    }
                    warnings.append(.playerAlreadySentOff)
                } else {
                    switch card {
                    case .red: kind = .red
                    case .sinBin: kind = .sinBin
                    case .yellow:
                        if rules.secondYellowSendOff, let player, !cautioned.insert(player).inserted {
                            kind = .secondYellow
                        } else {
                            kind = .yellow
                        }
                    }
                    if kind == .sinBin, let player {
                        if let second = secondSinBinAt[player], playedSeconds(from: second, to: e.timestamp) >= sinBinLength {
                            // 출전 금지 선수(두 번째 일시 퇴장을 마침)에게 또 일시 퇴장: 이미 빠진 사람이라 겹친 것으로 본다
                            warnings.append(.overlappingSinBin)
                        } else if offPitch.contains(player) {
                            // 교체로 나간 선수의 일시 퇴장: 경기장 인원은 그대로
                            warnings.append(.playerOffPitch)
                        } else if let last = lastSinBinAt[player], playedSeconds(from: last, to: e.timestamp) < sinBinLength {
                            // 일시 퇴장 중에 또 일시 퇴장: 한 사람은 한 번만 빠진다 (IFAB 지침에는 겹치는 일시 퇴장이 없다)
                            warnings.append(.overlappingSinBin)
                        } else {
                            isSecondSinBin = lastSinBinAt[player] != nil
                            lastSinBinAt[player] = e.timestamp
                            if isSecondSinBin && secondSinBinAt[player] == nil { secondSinBinAt[player] = e.timestamp }
                        }
                    }
                    if kind == .red || kind == .secondYellow {
                        if let player, offPitch.contains(player) {
                            // 교체로 나간 선수의 퇴장: 경기장 인원은 그대로
                            warnings.append(.playerOffPitch)
                        } else {
                            sentOff[team] += 1
                        }
                        if let player { dismissed.insert(player) }
                    }
                }
            case .substitution(let team, let numberOut, let numberIn):
                kind = .substitution
                subs[team] += 1
                if let limit = substitutionLimit(at: e.timestamp, extraStart: extraStart), subs[team] > limit {
                    warnings.append(.substitutionLimitExceeded)
                }
                if let numberOut {
                    let player = PlayerKey(team: team, number: numberOut)
                    offPitch.insert(player)
                    if secondSinBinAt[player] != nil { filled.insert(player) }
                }
                if let numberIn {
                    let player = PlayerKey(team: team, number: numberIn)
                    if let second = secondSinBinAt[player], !dismissed.contains(player),
                       playedSeconds(from: second, to: e.timestamp) >= sinBinLength {
                        warnings.append(.barredPlayerReturned)
                    }
                    offPitch.remove(player)
                }
            case .penaltyKick(let team, let number, _):
                kind = .penaltyKick
                // 일시 퇴장 중인 선수도 키커 자격이 있으므로 퇴장 인원과 채우지 않은 출전 금지 선수만 뺀다 (Law 10.3)
                if let number {
                    let barred = secondSinBinAt.keys.filter {
                        $0.team == team && !dismissed.contains($0) && !filled.contains($0)
                    }.count
                    let eligible = rules.playersPerTeam - sentOff[team] - barred
                    if kickers[team].contains(number) && kickers[team].count < eligible {
                        warnings.append(.repeatPenaltyKicker)
                    }
                    kickers[team].insert(number)
                }
            }
            return ResolvedEvent(event: e, kind: kind, warnings: warnings, isSecondSinBin: isSecondSinBin)
        }
    }

    public var substitutionCounts: TeamPair<Int> {
        var counts = TeamPair(home: 0, away: 0)
        for e in events {
            if case .substitution(let team, _, _) = e.payload { counts[team] += 1 }
        }
        return counts
    }

    /// 인원을 줄인 퇴장(레드, 경고 누적) 수. 교체로 나간 선수의 퇴장은 세지 않는다.
    public var sentOffCounts: TeamPair<Int> {
        var counts = TeamPair(home: 0, away: 0)
        for r in resolvedEvents where r.reducesTeam {
            counts[r.event.payload.team] += 1
        }
        return counts
    }

    public func playersOnField(at now: Double) -> TeamPair<Int> {
        var onField = sentOffCounts
        onField.home = rules.playersPerTeam - onField.home
        onField.away = rules.playersPerTeam - onField.away
        for s in activeSinBins(at: now) { onField[s.team] -= 1 }
        // 두 번째 일시 퇴장을 마친 선수는 교체로 채울 때까지 한 명 부족
        for b in barredPlayers(at: now) where !b.filled { onField[b.team] -= 1 }
        return onField
    }

    public func matchWarnings(at now: Double) -> [MatchWarning] {
        guard let minimum = rules.minimumPlayers else { return [] }
        let onField = playersOnField(at: now)
        return Team.allCases.filter { onField[$0] < minimum }.map { .belowMinimumPlayers($0) }
    }

    /// 연장전에 들어간 경우 마지막 정규 기간이 끝난 시각
    var extraTimeReachedAt: Double? {
        guard rules.extraTime,
              let lastRegular = periods.first(where: { $0.ref == PeriodRef(.regular, rules.periodCount) }),
              let end = lastRegular.endedAt,
              phase == .preExtraTime || periods.contains(where: { $0.ref.kind == .extra })
        else { return nil }
        return end
    }

    private func substitutionLimit(at t: Double, extraStart: Double?) -> Int? {
        guard !rules.returnSubstitutions, let limit = rules.substitutionLimit else { return nil }
        if let extraStart, t >= extraStart { return limit + rules.extraTimeExtraSubs }
        return limit
    }
}
