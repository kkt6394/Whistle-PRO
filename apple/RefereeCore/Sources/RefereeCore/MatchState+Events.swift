extension EventPayload {
    var shirtNumbers: [Int] {
        switch self {
        case .goal(_, let n, _), .card(_, let n, _), .penaltyKick(_, let n, _): [n].compactMap { $0 }
        case .substitution(_, let out, let into): [out, into].compactMap { $0 }
        }
    }
}

extension MatchState {
    mutating func recordEvent(_ payload: EventPayload, id: String, at t: Double) throws(EngineError) {
        try validateContent(payload)
        try validatePhase(for: payload)
        if case .penaltyKick(let team, _, _) = payload { try checkShootoutTurn(team) }
        events.append(RecordedEvent(id: id, timestamp: t, payload: payload, time: try eventTime(at: t)))
        finishShootoutIfDecided()
    }

    /// 같은 종류 안에서만 내용을 바꾼다. 시각은 원래 기록 시각을 유지한다.
    /// 승부차기 킥은 승부차기 진행 중에만, 팀은 바꿀 수 없다(차례가 꼬이므로).
    mutating func editEvent(id: String, to payload: EventPayload) throws(EngineError) {
        guard let i = events.firstIndex(where: { $0.id == id }) else { throw .unknownEvent }
        guard events[i].payload.typeName == payload.typeName else { throw .invalidValue }
        try validateContent(payload)
        if payload.isPenaltyKick {
            guard phase == .penalties, events[i].payload.team == payload.team else { throw .invalidInPhase }
        }
        events[i].payload = payload
        finishShootoutIfDecided()
    }

    /// 승부차기 킥은 승부차기 진행 중의 마지막 킥만 지울 수 있다.
    mutating func deleteEvent(id: String) throws(EngineError) {
        guard let i = events.firstIndex(where: { $0.id == id }) else { throw .unknownEvent }
        if events[i].payload.isPenaltyKick {
            let lastKick = events.lastIndex { $0.payload.isPenaltyKick }
            guard phase == .penalties, lastKick == i else { throw .invalidInPhase }
        }
        events.remove(at: i)
    }

    private func validateContent(_ payload: EventPayload) throws(EngineError) {
        guard payload.shirtNumbers.allSatisfy({ (0...99).contains($0) }) else { throw .invalidValue }
        if case .card(_, _, .sinBin) = payload, !rules.sinBin { throw .invalidValue }
    }

    /// 골은 기간 진행 중에만, 카드는 경기 시작 후 종료 전까지, 교체는 승부차기 전까지, 승부차기 킥은 승부차기 중에만.
    private func validatePhase(for payload: EventPayload) throws(EngineError) {
        let allowed = switch payload {
        case .goal: phase.isPeriod
        case .card: phase.isPeriod || phase.isInterval || phase == .preExtraTime || phase == .penalties
        case .substitution: phase.isPeriod || phase.isInterval || phase == .preExtraTime
        case .penaltyKick: phase == .penalties
        }
        if !allowed { throw .invalidInPhase }
    }

    private func eventTime(at t: Double) throws(EngineError) -> EventTime {
        guard let p = periods.last else { throw .invalidInPhase }
        let elapsed = Int(p.elapsedSeconds(at: t).rounded(.down))
        return EventTime(period: p.ref, duringPlay: p.endedAt == nil, matchSecond: p.offset + elapsed)
    }
}
