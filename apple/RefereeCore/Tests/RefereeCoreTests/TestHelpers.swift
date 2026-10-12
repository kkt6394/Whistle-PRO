import Foundation
@testable import RefereeCore

func cmd(_ kind: CommandKind, at t: Double, id: String = UUID().uuidString) -> Command {
    Command(id: id, timestamp: t, kind: kind)
}

func play(_ rules: RuleSet, _ commands: [Command]) -> MatchState {
    MatchEngine.reduce(rules: rules, commands: commands)
}

/// FIFA 표준 규칙으로 재생
func play(_ commands: [Command]) -> MatchState {
    play(.fifaStandard, commands)
}

func goal(_ team: Team, at t: Double, number: Int? = nil, id: String = UUID().uuidString) -> Command {
    cmd(.recordEvent(.goal(team: team, number: number, ownGoal: false)), at: t, id: id)
}

func card(_ team: Team, _ number: Int?, _ kind: CardKind, at t: Double, id: String = UUID().uuidString) -> Command {
    cmd(.recordEvent(.card(team: team, number: number, kind: kind)), at: t, id: id)
}

func sub(_ team: Team, at t: Double, id: String = UUID().uuidString) -> Command {
    cmd(.recordEvent(.substitution(team: team, numberOut: nil, numberIn: nil)), at: t, id: id)
}

/// FIFA 표준 전·후반을 득점 없이 끝내는 명령 (후반 종료 시각 6300)
func twoHalves() -> [Command] {
    [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6300)]
}
