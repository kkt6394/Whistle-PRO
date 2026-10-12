import Foundation
import RefereeCore

public enum ClockDisplay: String, Codable, Sendable {
    case elapsed, remaining
}

public enum TeamColor: String, Codable, Sendable, CaseIterable {
    case red, blue, white, black, yellow, green, orange, purple
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

    public init(
        rules: RuleSet = .fifaStandard,
        homeName: String = "HOME",
        awayName: String = "AWAY",
        homeColor: TeamColor = .red,
        awayColor: TeamColor = .blue,
        clockDisplay: ClockDisplay = .elapsed,
        warningBeforeEnd: Int = 300
    ) {
        self.rules = rules
        self.homeName = homeName
        self.awayName = awayName
        self.homeColor = homeColor
        self.awayColor = awayColor
        self.clockDisplay = clockDisplay
        self.warningBeforeEnd = warningBeforeEnd
    }
}

/// 저장되는 경기 기록. 상태는 commands를 재생해서 다시 만든다.
public struct MatchRecord: Codable, Equatable, Sendable, Identifiable {
    public var id: UUID
    public var createdAt: Double
    public var setup: MatchSetup
    public var commands: [Command]

    public init(id: UUID, createdAt: Double, setup: MatchSetup, commands: [Command]) {
        self.id = id
        self.createdAt = createdAt
        self.setup = setup
        self.commands = commands
    }
}
