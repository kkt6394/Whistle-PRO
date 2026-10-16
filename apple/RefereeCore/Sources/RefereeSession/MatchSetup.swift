import Foundation
import RefereeCore

public enum ClockDisplay: String, Codable, Sendable {
    case elapsed, remaining
}

/// 팀 색 (sRGB 0...1). JSON에는 "#RRGGBB"로 쓰고, 예전 이름("red" 등)도 읽는다 (2026-10-16: 아무 색이나 고른다)
public struct TeamColor: Codable, Hashable, Sendable {
    public let red: Double
    public let green: Double
    public let blue: Double

    public init(red: Double, green: Double, blue: Double) {
        func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
        self.red = clamp(red); self.green = clamp(green); self.blue = clamp(blue)
    }

    public init?(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }

    public var hex: String {
        func byte(_ v: Double) -> Int { Int((v * 255).rounded()) }
        return String(format: "#%02X%02X%02X", byte(red), byte(green), byte(blue))
    }

    public static let red = TeamColor(hex: "#FF3B30")!
    public static let blue = TeamColor(hex: "#007AFF")!
    public static let white = TeamColor(hex: "#FFFFFF")!
    public static let black = TeamColor(hex: "#000000")!
    public static let yellow = TeamColor(hex: "#FFCC00")!
    public static let green = TeamColor(hex: "#34C759")!
    public static let orange = TeamColor(hex: "#FF9500")!
    public static let purple = TeamColor(hex: "#AF52DE")!
    public static let sky = TeamColor(hex: "#5AC8FA")!
    public static let navy = TeamColor(hex: "#1C2E6B")!
    public static let maroon = TeamColor(hex: "#7B1E3A")!
    public static let pink = TeamColor(hex: "#FF6FAE")!
    public static let gray = TeamColor(hex: "#8E8E93")!

    /// 편집 화면의 빠른 견본 (앞 8개는 예전 이름 색)
    public static let quickPicks: [TeamColor] = [.red, .blue, .white, .black, .yellow, .green, .orange, .purple,
                                                 .sky, .navy, .maroon, .pink, .gray]

    /// 예전 형식의 이름 (2026-10-16 이전 프리셋·기록)
    static let legacyNames: [String: TeamColor] = [
        "red": .red, "blue": .blue, "white": .white, "black": .black,
        "yellow": .yellow, "green": .green, "orange": .orange, "purple": .purple,
    ]

    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        if let named = Self.legacyNames[raw] { self = named; return }
        guard let color = TeamColor(hex: raw) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "알 수 없는 팀 색: \(raw)"))
        }
        self = color
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(hex)
    }

    /// 워치에서 두 팀 점을 구분하기 어려울 만큼 비슷한지 (CIE Lab ΔE76 < 25)
    public func isHardToTell(from other: TeamColor) -> Bool {
        let a = lab, b = other.lab
        let d = ((a.l - b.l) * (a.l - b.l) + (a.a - b.a) * (a.a - b.a) + (a.b - b.b) * (a.b - b.b)).squareRoot()
        return d < 25
    }

    private var lab: (l: Double, a: Double, b: Double) {
        func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let r = linear(red), g = linear(green), bl = linear(blue)
        // sRGB D65 → XYZ, 기준 백색으로 나눔
        let x = (0.4124 * r + 0.3576 * g + 0.1805 * bl) / 0.95047
        let y = 0.2126 * r + 0.7152 * g + 0.0722 * bl
        let z = (0.0193 * r + 0.1192 * g + 0.9505 * bl) / 1.08883
        func f(_ t: Double) -> Double { t > 0.008856 ? cbrt(t) : 7.787 * t + 16 / 116 }
        let fx = f(x), fy = f(y), fz = f(z)
        return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }
}

/// 한 경기의 설정. 규칙(RuleSet)과 화면·팀 정보를 함께 담는다.
public struct MatchSetup: Codable, Equatable, Sendable {
    public var rules: RuleSet
    public var homeName: String
    public var awayName: String
    public var homeColor: TeamColor
    public var awayColor: TeamColor
    public var clockDisplay: ClockDisplay
    /// 종료 N초 전 예고 진동 (0이면 끔)
    public var warningBeforeEnd: Int
    /// GPS 경로 기록 (끄면 배터리를 아끼는 대신 히트맵·스프린트가 없다)
    public var recordsRoute: Bool
    /// 선수 명단 (없으면 등번호만 입력한다)
    public var homeRoster: [Player]
    public var awayRoster: [Player]
    /// 대회명 (선택, 보고서 머리에 쓴다). 없으면 nil — JSON에서도 키를 생략한다
    public var competition: String?

    public init(
        rules: RuleSet = .fifaStandard,
        homeName: String = "HOME",
        awayName: String = "AWAY",
        homeColor: TeamColor = .red,
        awayColor: TeamColor = .blue,
        clockDisplay: ClockDisplay = .elapsed,
        warningBeforeEnd: Int = 300,
        recordsRoute: Bool = true,
        homeRoster: [Player] = [],
        awayRoster: [Player] = [],
        competition: String? = nil
    ) {
        self.rules = rules
        self.homeName = homeName
        self.awayName = awayName
        self.homeColor = homeColor
        self.awayColor = awayColor
        self.clockDisplay = clockDisplay
        self.warningBeforeEnd = warningBeforeEnd
        self.recordsRoute = recordsRoute
        self.homeRoster = homeRoster
        self.awayRoster = awayRoster
        self.competition = competition
    }

    private enum CodingKeys: String, CodingKey {
        case rules, homeName, awayName, homeColor, awayColor, clockDisplay, warningBeforeEnd, recordsRoute
        case homeRoster, awayRoster, competition
    }

    /// 나중에 추가된 필드는 없으면 기본값을 쓴다 (이전 버전 기록 호환).
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            rules: try c.decode(RuleSet.self, forKey: .rules),
            homeName: try c.decode(String.self, forKey: .homeName),
            awayName: try c.decode(String.self, forKey: .awayName),
            homeColor: try c.decode(TeamColor.self, forKey: .homeColor),
            awayColor: try c.decode(TeamColor.self, forKey: .awayColor),
            clockDisplay: try c.decode(ClockDisplay.self, forKey: .clockDisplay),
            warningBeforeEnd: try c.decode(Int.self, forKey: .warningBeforeEnd),
            recordsRoute: try c.decodeIfPresent(Bool.self, forKey: .recordsRoute) ?? true,
            homeRoster: try c.decodeIfPresent([Player].self, forKey: .homeRoster) ?? [],
            awayRoster: try c.decodeIfPresent([Player].self, forKey: .awayRoster) ?? [],
            competition: try c.decodeIfPresent(String.self, forKey: .competition))
    }

    public func roster(of team: Team) -> [Player] {
        team == .home ? homeRoster : awayRoster
    }

    /// 명단에서 등번호로 찾은 이름 (번호가 없거나 명단에 없으면 nil)
    public func playerName(team: Team, number: Int?) -> String? {
        guard let number else { return nil }
        return roster(of: team).first { $0.number == number }?.name
    }
}

/// 저장되는 경기 기록. 상태는 commands를 재생해서 다시 만든다.
public struct MatchRecord: Codable, Equatable, Sendable, Identifiable {
    public var id: UUID
    public var createdAt: Double
    public var setup: MatchSetup
    public var commands: [Command]
    /// 킥오프 때 유료 기능이 열려 있었는지. 경기 중에는 바꾸지 않는다 (스펙 §6).
    public var unlocked: Bool

    public init(id: UUID, createdAt: Double, setup: MatchSetup, commands: [Command], unlocked: Bool = true) {
        self.id = id
        self.createdAt = createdAt
        self.setup = setup
        self.commands = commands
        self.unlocked = unlocked
    }

    private enum CodingKeys: String, CodingKey {
        case id, createdAt, setup, commands, unlocked
    }

    /// 유료 기능이 생기기 전(M2~M4)에 저장한 기록에는 unlocked가 없다 → 열린 것으로 읽는다.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            id: try c.decode(UUID.self, forKey: .id),
            createdAt: try c.decode(Double.self, forKey: .createdAt),
            setup: try c.decode(MatchSetup.self, forKey: .setup),
            commands: try c.decode([Command].self, forKey: .commands),
            unlocked: try c.decodeIfPresent(Bool.self, forKey: .unlocked) ?? true)
    }
}
