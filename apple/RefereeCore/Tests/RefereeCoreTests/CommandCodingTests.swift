import Foundation
import Testing
@testable import RefereeCore

struct CommandCodingTests {
    private func decode(_ json: String) throws -> Command {
        try JSONDecoder().decode(Command.self, from: Data(json.utf8))
    }

    @Test func decodesSimpleCommandWithoutId() throws {
        let c = try decode(#"{"type": "startPeriod", "t": 12.5}"#)
        #expect(!c.id.isEmpty)
        #expect(c.timestamp == 12.5)
        #expect(c.kind == .startPeriod)
    }

    /// Kotlin CommandCodingTests와 같게: 32비트를 넘는 정수는 읽고 엔진이 무시한다, 64비트를 넘으면 실패
    @Test func integersBeyond32BitsDecodeAndEngineIgnoresThem() throws {
        let adjust = try decode(#"{"type": "adjustPeriodLength", "t": 1, "id": "a", "seconds": 3000000000}"#)
        #expect(MatchEngine.reduce(rules: .fifaStandard, commands: [adjust]).ignoredCommandIDs == ["a"])
        _ = try decode(#"{"type": "recordGoal", "t": 2, "id": "g", "team": "home", "number": 3000000000}"#)
        #expect(throws: (any Error).self) {
            try decode(#"{"type": "adjustPeriodLength", "t": 1, "seconds": 10000000000000000000}"#)
        }
    }

    /// Kotlin CommandCodingTests.nonStandardLiteralsAreRejected와 같은 목록: 표준이 아닌 숫자·불리언 리터럴은 거부
    @Test func nonStandardLiteralsAreRejected() throws {
        for literal in ["NaN", "Infinity", "-Infinity", "12d", "0x1p3", "9f", "1e400", "-1e400", "+5", "01", ".5", "5.", "1e", "TRUE"] {
            #expect(throws: (any Error).self, "\(literal)") { try decode(#"{"type": "startPeriod", "t": \#(literal)}"#) }
            #expect(throws: (any Error).self, "\(literal)") {
                try decode(#"{"type": "adjustPeriodLength", "t": 1, "seconds": \#(literal)}"#)
            }
        }
        for literal in ["TRUE", "True", "1", "0", "yes"] {
            #expect(throws: (any Error).self, "\(literal)") {
                try decode(#"{"type": "recordGoal", "t": 1, "team": "home", "ownGoal": \#(literal)}"#)
            }
        }
        for (literal, value) in [("12", 12.0), ("-0.5", -0.5), ("1e3", 1000), ("1E+3", 1000), ("2.5e-1", 0.25)] {
            #expect(try decode(#"{"type": "startPeriod", "t": \#(literal)}"#).timestamp == value)
        }
    }

    @Test func missingIdsAreUnique() throws {
        let a = try decode(#"{"type": "startPeriod", "t": 0}"#)
        let b = try decode(#"{"type": "startPeriod", "t": 0}"#)
        #expect(a.id != b.id)
    }

    @Test func decodesRecordGoal() throws {
        let c = try decode(#"{"type": "recordGoal", "t": 100, "id": "g1", "team": "home", "number": 9}"#)
        #expect(c.id == "g1")
        #expect(c.kind == .recordEvent(.goal(team: .home, number: 9, ownGoal: false)))
    }

    @Test func decodesRecordCardWithoutNumber() throws {
        let c = try decode(#"{"type": "recordCard", "t": 1, "team": "away", "kind": "sinBin"}"#)
        #expect(c.kind == .recordEvent(.card(team: .away, number: nil, kind: .sinBin)))
    }

    @Test func decodesEditEventWithNestedPayload() throws {
        let json = #"{"type": "editEvent", "t": 5, "eventId": "g1", "event": {"type": "goal", "team": "away", "number": 11, "ownGoal": true}}"#
        let c = try decode(json)
        #expect(c.kind == .editEvent(id: "g1", .goal(team: .away, number: 11, ownGoal: true)))
    }

    @Test func unknownTypeThrows() {
        #expect(throws: DecodingError.self) { try decode(#"{"type": "dance", "t": 0}"#) }
        #expect(throws: DecodingError.self) { try decode(#"{"type": "recordDance", "t": 0, "team": "home"}"#) }
    }

    @Test func everyKindRoundTrips() throws {
        let kinds: [CommandKind] = [
            .setKickoffTeam(.home),
            .startPeriod,
            .endPeriod,
            .adjustPeriodLength(1800),
            .toggleStoppage,
            .setShootoutFirstTeam(.away),
            .recordEvent(.goal(team: .home, number: nil, ownGoal: false)),
            .recordEvent(.card(team: .away, number: 4, kind: .yellow)),
            .recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 14)),
            .recordEvent(.penaltyKick(team: .away, number: 10, scored: true)),
            .editEvent(id: "x", .card(team: .home, number: 3, kind: .red)),
            .deleteEvent(id: "x"),
            .undo,
            .abandonMatch(reason: "폭우"),
        ]
        let commands = kinds.enumerated().map { i, kind in Command(id: "c\(i)", timestamp: Double(i), kind: kind) }
        let back = try JSONDecoder().decode([Command].self, from: JSONEncoder().encode(commands))
        #expect(back == commands)
    }
}
