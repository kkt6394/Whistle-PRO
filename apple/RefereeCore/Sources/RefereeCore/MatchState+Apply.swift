extension MatchState {
    /// 명령 하나를 적용한다. 허용되지 않으면 EngineError를 던지고 상태는 그대로다.
    public mutating func apply(_ command: Command) throws(EngineError) {
        var next = self
        try next.applyUnchecked(command)
        self = next
    }

    private mutating func applyUnchecked(_ command: Command) throws(EngineError) {
        let t = command.timestamp
        switch command.kind {
        case .setKickoffTeam(let team): try setKickoff(team)
        case .startPeriod: try startPeriod(at: t)
        case .endPeriod: try endPeriod(at: t)
        case .adjustPeriodLength(let seconds): try adjustPeriodLength(seconds)
        case .abandonMatch(let reason): try abandon(reason: reason, at: t)
        case .toggleStoppage: try toggleStoppage(at: t)
        case .recordEvent(let payload): try recordEvent(payload, id: command.id, at: t)
        case .editEvent(let id, let payload): try editEvent(id: id, to: payload)
        case .deleteEvent(let id): try deleteEvent(id: id)
        case .setShootoutFirstTeam(let team): try setShootoutFirstTeam(team)
        case .undo:
            throw .invalidInPhase // undo는 MatchEngine.reduce가 로그 단계에서 처리한다
        }
    }

    private mutating func setKickoff(_ team: Team) throws(EngineError) {
        switch phase {
        case .notStarted: regularKickoff = team
        case .preExtraTime: extraKickoff = team
        default: throw .invalidInPhase
        }
    }

    private mutating func startPeriod(at t: Double) throws(EngineError) {
        let ref: PeriodRef
        switch phase {
        case .notStarted: ref = PeriodRef(.regular, 1)
        case .interval(let after): ref = PeriodRef(after.kind, after.index + 1)
        case .preExtraTime: ref = PeriodRef(.extra, 1)
        default: throw .invalidInPhase
        }
        let length = ref.kind == .regular ? regularPeriodLength : extraPeriodLength
        let offset = periods.reduce(0) { $0 + $1.nominalLength }
        periods.append(PeriodRecord(ref: ref, startedAt: t, endedAt: nil, nominalLength: length, offset: offset, stoppages: []))
        phase = .period(ref)
    }

    private mutating func endPeriod(at t: Double) throws(EngineError) {
        guard case .period(let ref) = phase else { throw .invalidInPhase }
        closeRunningPeriod(at: t)
        phase = phaseAfterEnding(ref)
    }

    /// 정규/연장 중 지금 해당하는 쪽의 길이를 바꾼다. 기간 진행 중이면 그 기간에도 즉시 적용된다.
    private mutating func adjustPeriodLength(_ seconds: Int) throws(EngineError) {
        guard (60...5400).contains(seconds) else { throw .invalidValue }
        let targetsExtra: Bool
        switch phase {
        case .notStarted: targetsExtra = false
        case .period(let ref), .interval(after: let ref): targetsExtra = ref.kind == .extra
        case .preExtraTime: targetsExtra = true
        case .penalties, .finished: throw .invalidInPhase
        }
        if targetsExtra { extraPeriodLength = seconds } else { regularPeriodLength = seconds }
        if phase.isPeriod { periods[periods.count - 1].nominalLength = seconds }
    }

    private mutating func abandon(reason: String, at t: Double) throws(EngineError) {
        if case .finished = phase { throw .invalidInPhase }
        if phase.isPeriod { closeRunningPeriod(at: t) }
        phase = .finished(.abandoned(reason: reason))
    }

    /// 진행 중인 기간과 열린 중단 구간을 t에 닫는다.
    mutating func closeRunningPeriod(at t: Double) {
        let i = periods.count - 1
        periods[i].endedAt = t
        if let s = periods[i].stoppages.indices.last, periods[i].stoppages[s].end == nil {
            periods[i].stoppages[s].end = t
        }
    }

    private func phaseAfterEnding(_ ref: PeriodRef) -> Phase {
        let tied = score.home == score.away
        switch ref.kind {
        case .regular:
            if ref.index < rules.periodCount { return .interval(after: ref) }
            if tied && rules.extraTime { return .preExtraTime }
            if tied && rules.penalties { return .penalties }
            return .finished(.completed)
        case .extra:
            if ref.index < 2 { return .interval(after: ref) }
            if tied && rules.penalties { return .penalties }
            return .finished(.completed)
        }
    }
}
