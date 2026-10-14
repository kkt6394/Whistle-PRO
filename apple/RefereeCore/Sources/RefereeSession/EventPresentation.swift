import RefereeCore

/// 축구식 분 표기. 화면 문구로 바꾸는 일은 앱이 한다.
public enum EventMinute: Equatable, Sendable {
    /// 23'
    case regular(Int)
    /// 45+2'
    case added(base: Int, extra: Int)
    /// 휴식·연장 대기 중 기록 (끝난 기간 라벨)
    case afterPeriod(String)
    /// 승부차기 킥
    case shootout
}

public struct EventRow: Equatable, Identifiable, Sendable {
    public var id: String
    public var minute: EventMinute
    public var kind: ResolvedEventKind
    public var team: Team
    /// "#9", "#7 → #14", 번호가 없으면 ""
    public var detail: String
    /// 승부차기 킥 성공 여부 (킥이 아니면 nil)
    public var scored: Bool?
    public var warnings: [EventWarning]
}

/// 이벤트를 기록한 직후 심판에게 보여줄 안내
public enum RecordFeedback: Equatable, Sendable {
    case none
    /// 경고 누적 퇴장 확인 (등번호를 바로 고칠 수 있어야 한다)
    case secondYellow(eventID: String)
    case warning(EventWarning)
}

extension MatchState {
    /// 이벤트 로그 화면용. 최근 것이 먼저.
    public var eventRows: [EventRow] {
        rows { _, _ in nil }
    }

    /// 명단이 있으면 번호 뒤에 이름을 붙인다 ("#10 홍길동").
    public func eventRows(setup: MatchSetup) -> [EventRow] {
        rows(name: setup.playerName(team:number:))
    }

    private func rows(name: (Team, Int?) -> String?) -> [EventRow] {
        resolvedEvents.reversed().map { r in
            let team = r.event.payload.team
            func label(_ number: Int?) -> String? {
                guard let number else { return nil }
                return name(team, number).map { "#\(number) \($0)" } ?? "#\(number)"
            }
            var scored: Bool?
            let detail: String
            switch r.event.payload {
            case .goal(_, let n, _), .card(_, let n, _):
                detail = label(n) ?? ""
            case .penaltyKick(_, let n, let s):
                detail = label(n) ?? ""
                scored = s
            case .substitution(_, let out, let into):
                detail = out == nil && into == nil
                    ? ""
                    : "\(label(out) ?? "?") → \(label(into) ?? "?")"
            }
            return EventRow(
                id: r.event.id,
                minute: r.kind == .penaltyKick ? .shootout : minute(for: r.event.time),
                kind: r.kind,
                team: r.event.payload.team,
                detail: detail,
                scored: scored,
                warnings: r.warnings)
        }
    }

    /// 0:00~0:59 → 1', 규정 시간 이후 → 45+2' (45:00~45:59가 45+1')
    public func minute(for time: EventTime) -> EventMinute {
        guard time.duringPlay, let p = periods.first(where: { $0.ref == time.period }) else {
            return .afterPeriod(time.period.label(periodCount: rules.periodCount))
        }
        let end = p.offset + p.nominalLength
        if time.matchSecond >= end {
            return .added(base: end / 60, extra: (time.matchSecond - end) / 60 + 1)
        }
        return .regular(time.matchSecond / 60 + 1)
    }

    public func feedback(forEvent id: String) -> RecordFeedback {
        guard let r = resolvedEvents.first(where: { $0.event.id == id }) else { return .none }
        if r.kind == .secondYellow && !r.warnings.contains(.playerAlreadySentOff) { return .secondYellow(eventID: id) }
        if let warning = r.warnings.first { return .warning(warning) }
        return .none
    }
}
