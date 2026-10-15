import Foundation
import Testing
@testable import RefereeCore

struct RuleSetTests {
    @Test func fifaStandardDefaults() {
        let r = RuleSet.fifaStandard
        #expect(r.periodCount == 2)
        #expect(r.periodLength == 2700)
        #expect(r.halfTimeLength == 900)
        #expect(r.extraTime == false)
        #expect(r.extraTimePeriodLength == 900)
        #expect(r.extraTimeBreakLength == 60)
        #expect(r.penalties == false)
        #expect(r.penaltyKicksPerTeam == 5)
        #expect(r.playersPerTeam == 11)
        #expect(r.minimumPlayers == 7)
        #expect(r.substitutionLimit == 5)
        #expect(r.returnSubstitutions == false)
        #expect(r.extraTimeExtraSubs == 0)
        #expect(r.secondYellowSendOff)
        #expect(r.sinBin == false)
        #expect(r.sinBinLength == 600)
    }

    /// "킥오프 교대" 규칙을 지웠다 (2026-10-15 사용자 결정: 경기 규칙 제8조는 항상 교대). 예전 데이터의 키는 무시한다
    @Test func ignoresRemovedAlternateKickoffKey() throws {
        let json = #"{"alternateKickoff": false}"#
        #expect(try JSONDecoder().decode(RuleSet.self, from: Data(json.utf8)) == RuleSet.fifaStandard)
    }

    @Test func decodesPartialJSONOverFifaDefaults() throws {
        let json = #"{"periodCount": 4, "periodLength": 1500}"#
        let r = try JSONDecoder().decode(RuleSet.self, from: Data(json.utf8))
        #expect(r.periodCount == 4)
        #expect(r.periodLength == 1500)
        #expect(r.halfTimeLength == 900)
        #expect(r.minimumPlayers == 7)
    }

    @Test func explicitNullTurnsOptionalRulesOff() throws {
        let json = #"{"minimumPlayers": null, "substitutionLimit": null}"#
        let r = try JSONDecoder().decode(RuleSet.self, from: Data(json.utf8))
        #expect(r.minimumPlayers == nil)
        #expect(r.substitutionLimit == nil)
    }

    @Test func roundTripKeepsDisabledOptionals() throws {
        var r = RuleSet.fifaStandard
        r.substitutionLimit = nil
        r.minimumPlayers = nil
        let back = try JSONDecoder().decode(RuleSet.self, from: JSONEncoder().encode(r))
        #expect(back == r)
    }

    @Test func validateAcceptsFifaStandard() throws {
        try RuleSet.fifaStandard.validate()
    }

    @Test(arguments: [
        (RuleSet(periodCount: 3), RuleSet.Field.periodCount),
        (RuleSet(periodLength: 30), .periodLength),
        (RuleSet(halfTimeLength: 3601), .halfTimeLength),
        (RuleSet(quarterBreakLength: 1801), .quarterBreakLength),
        (RuleSet(extraTimePeriodLength: 1801), .extraTimePeriodLength),
        (RuleSet(extraTimeBreakLength: 901), .extraTimeBreakLength),
        (RuleSet(penaltyKicksPerTeam: 0), .penaltyKicksPerTeam),
        (RuleSet(playersPerTeam: 2), .playersPerTeam),
        (RuleSet(playersPerTeam: 7, minimumPlayers: 8), .minimumPlayers),
        (RuleSet(substitutionLimit: 21), .substitutionLimit),
        (RuleSet(extraTimeExtraSubs: 4), .extraTimeExtraSubs),
        (RuleSet(sinBinLength: 30), .sinBinLength),
    ])
    func validateRejectsOutOfRange(rules: RuleSet, field: RuleSet.Field) {
        #expect(throws: RuleSetError(field: field)) { try rules.validate() }
    }

    @Test func deviationsListOnlyChangedFields() {
        let r = RuleSet(periodCount: 4, periodLength: 1500, substitutionLimit: nil)
        #expect(r.deviations() == [.periodCount, .periodLength, .substitutionLimit])
        #expect(RuleSet.fifaStandard.deviations().isEmpty)
    }
}
