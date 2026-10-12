public enum Team: String, Codable, Sendable, CaseIterable {
    case home, away

    public var opponent: Team { self == .home ? .away : .home }
}

/// 홈/원정 한 쌍의 값. JSON으로는 [home, away] 배열로 인코딩한다.
public struct TeamPair<Value: Sendable & Equatable>: Sendable, Equatable {
    public var home: Value
    public var away: Value

    public init(home: Value, away: Value) {
        self.home = home
        self.away = away
    }

    public subscript(team: Team) -> Value {
        get { team == .home ? home : away }
        set {
            if team == .home { home = newValue } else { away = newValue }
        }
    }
}

extension TeamPair: Encodable where Value: Encodable {
    public func encode(to encoder: Encoder) throws {
        var container = encoder.unkeyedContainer()
        try container.encode(home)
        try container.encode(away)
    }
}
