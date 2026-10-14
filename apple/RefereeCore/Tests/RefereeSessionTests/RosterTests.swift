import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct RosterTests {
    private static let sample = MatchSetup(
        homeRoster: [Player(number: 10, name: "홍길동"), Player(number: 7, name: "Kim")],
        awayRoster: [Player(number: 10, name: "Park")])

    @Test func decodesSetupWithoutRoster() throws {
        var json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(MatchSetup())) as! [String: Any]
        json.removeValue(forKey: "homeRoster")
        json.removeValue(forKey: "awayRoster")
        let setup = try JSONDecoder().decode(MatchSetup.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(setup.homeRoster.isEmpty)
        #expect(setup.awayRoster.isEmpty)
    }

    @Test func rosterRoundTrips() throws {
        let decoded = try JSONDecoder().decode(MatchSetup.self, from: JSONEncoder().encode(Self.sample))
        #expect(decoded == Self.sample)
    }

    /// 이전 앱 버전(명단 없는 MatchSetup)이 새 기록을 받아도 읽을 수 있다
    @Test func oldDecoderIgnoresRoster() throws {
        struct OldSetup: Decodable {
            var rules: RuleSet
            var homeName: String
            var awayName: String
            var homeColor: TeamColor
            var awayColor: TeamColor
            var clockDisplay: ClockDisplay
            var warningBeforeEnd: Int
        }
        let old = try JSONDecoder().decode(OldSetup.self, from: JSONEncoder().encode(Self.sample))
        #expect(old.homeName == "HOME")
    }

    @Test func playerNameLooksUpByTeamAndNumber() {
        #expect(Self.sample.playerName(team: .home, number: 10) == "홍길동")
        #expect(Self.sample.playerName(team: .away, number: 10) == "Park")
        #expect(Self.sample.playerName(team: .away, number: 7) == nil)
        #expect(Self.sample.playerName(team: .home, number: nil) == nil)
    }

    @Test func parsesPastedLines() {
        let result = Roster.parse("10 홍길동\n7, Kim\n#9\tLee\n\n  \nKim 5\n123 Long\n11\n")
        #expect(result.players == [Player(number: 7, name: "Kim"), Player(number: 9, name: "Lee"), Player(number: 10, name: "홍길동")])
        #expect(result.skippedLines == 3)
    }

    @Test func parseKeepsLastDuplicateAndTruncatesName() {
        let result = Roster.parse("4 First\r\n4 Second\n5 " + String(repeating: "가", count: 30))
        #expect(result.players.map(\.name) == ["Second", String(repeating: "가", count: 20)])
        #expect(result.skippedLines == 0)
    }

    @Test func validateFindsIssues() {
        let players = [Player(number: 100, name: "A"), Player(number: 3, name: " "), Player(number: 3, name: "B"),
                       Player(number: 3, name: "C"), Player(number: 8, name: String(repeating: "x", count: 21))]
        #expect(Roster.validate(players) == [.numberOutOfRange(100), .emptyName(3), .duplicateNumber(3), .nameTooLong(8)])
        #expect(Roster.validate((0..<41).map { Player(number: $0, name: "P") }) == [.tooManyPlayers])
        #expect(Roster.validate(Self.sample.homeRoster).isEmpty)
    }

    @Test func normalizedTrimsAndSorts() {
        #expect(Roster.normalized([Player(number: 9, name: " B "), Player(number: 2, name: "A")])
            == [Player(number: 2, name: "A"), Player(number: 9, name: "B")])
    }

    @Test func eventRowDetailIncludesName() {
        let state = makeState([
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .home, number: 10, ownGoal: false)), 60),
            (.recordEvent(.card(team: .away, number: 10, kind: .yellow)), 120),
            (.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 14)), 180),
            (.recordEvent(.card(team: .away, number: 23, kind: .yellow)), 240),
        ])
        #expect(state.eventRows(setup: Self.sample).map(\.detail) == ["#23", "#7 Kim → #14", "#10 Park", "#10 홍길동"])
        #expect(state.eventRows.map(\.detail) == ["#23", "#7 → #14", "#10", "#10"])
    }

    @Test func reportUsesNames() {
        let record = makeRecord(Self.sample, [
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .home, number: 10, ownGoal: false)), 60),
        ])
        let report = MatchReport(bundle: MatchBundle(record: record, workout: nil))
        #expect(report.goals.map(\.detail) == ["#10 홍길동"])
    }
}
