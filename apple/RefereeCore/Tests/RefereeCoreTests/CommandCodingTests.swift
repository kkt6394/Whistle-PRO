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
