import RefereeCore

/// 워치 이벤트 입력에서 고르는 종류
public enum EventKindChoice: String, CaseIterable, Sendable {
    case goal, ownGoal, yellow, red, sinBin, substitution

    /// 일시 퇴장은 규칙에서 켰을 때만 보인다.
    public static func available(for rules: RuleSet) -> [EventKindChoice] {
        allCases.filter { $0 != .sinBin || rules.sinBin }
    }

    /// 교체는 나가는 번호와 들어오는 번호를 차례로 받는다.
    public var needsSecondNumber: Bool { self == .substitution }

    /// 자책골의 team은 자책한 선수의 팀이다 (득점은 상대 팀).
    public func payload(team: Team, number: Int?, secondNumber: Int?) -> EventPayload {
        switch self {
        case .goal: .goal(team: team, number: number, ownGoal: false)
        case .ownGoal: .goal(team: team, number: number, ownGoal: true)
        case .yellow: .card(team: team, number: number, kind: .yellow)
        case .red: .card(team: team, number: number, kind: .red)
        case .sinBin: .card(team: team, number: number, kind: .sinBin)
        case .substitution: .substitution(team: team, numberOut: number, numberIn: secondNumber)
        }
    }
}

/// 숫자 패드 입력 (최대 두 자리)
public struct NumberEntry: Equatable, Sendable {
    public private(set) var digits: String
    /// 번호 수정으로 미리 채운 번호: 처음 누른 숫자가 바꾼다 (뒤에 이어 붙으면 4 → 5가 45가 된다)
    private var prefilled: Bool

    public init(value: Int? = nil) {
        digits = value.map(String.init) ?? ""
        prefilled = value != nil
    }

    public mutating func append(_ digit: Int) {
        guard (0...9).contains(digit) else { return }
        if prefilled {
            digits = ""
            prefilled = false
        }
        guard digits.count < 2 else { return }
        if digits == "0" { digits = "" }
        digits += String(digit)
    }

    public mutating func deleteLast() {
        prefilled = false
        if !digits.isEmpty { digits.removeLast() }
    }

    public var value: Int? { Int(digits) }
}

/// 이벤트의 등번호 자리. 교체는 primary=나가는 번호, incoming=들어오는 번호.
public enum NumberSlot: Sendable {
    case primary, incoming
}

extension EventPayload {
    public func number(in slot: NumberSlot) -> Int? {
        switch (self, slot) {
        case (.substitution(_, let out, _), .primary): out
        case (.substitution(_, _, let into), .incoming): into
        case (.goal(_, let n, _), .primary), (.card(_, let n, _), .primary), (.penaltyKick(_, let n, _), .primary): n
        default: nil
        }
    }

    public func withNumber(_ number: Int?, in slot: NumberSlot) -> EventPayload {
        switch (self, slot) {
        case (.goal(let team, _, let own), .primary): .goal(team: team, number: number, ownGoal: own)
        case (.card(let team, _, let kind), .primary): .card(team: team, number: number, kind: kind)
        case (.penaltyKick(let team, _, let scored), .primary): .penaltyKick(team: team, number: number, scored: scored)
        case (.substitution(let team, _, let into), .primary): .substitution(team: team, numberOut: number, numberIn: into)
        case (.substitution(let team, let out, _), .incoming): .substitution(team: team, numberOut: out, numberIn: number)
        default: self
        }
    }
}
