import Foundation
import Testing
@testable import RefereeSession

struct TeamColorTests {
    @Test func hexRoundTrip() {
        #expect(TeamColor(hex: "#C8102E")?.hex == "#C8102E")
        #expect(TeamColor(hex: "c8102e")?.hex == "#C8102E")
        #expect(TeamColor(hex: "#C8102") == nil)
        #expect(TeamColor(hex: "zzzzzz") == nil)
    }

    @Test func encodesAsHexAndReadsLegacyNames() throws {
        let data = try JSONEncoder().encode(TeamColor.red)
        #expect(String(decoding: data, as: UTF8.self) == "\"#FF3B30\"")
        let legacy = try JSONDecoder().decode([TeamColor].self, from: Data(#"["red","blue","white","black","yellow","green","orange","purple"]"#.utf8))
        #expect(legacy == [.red, .blue, .white, .black, .yellow, .green, .orange, .purple])
        #expect(throws: DecodingError.self) { try JSONDecoder().decode(TeamColor.self, from: Data(#""teal""#.utf8)) }
    }

    @Test func clampsOutOfRangeComponents() {
        let c = TeamColor(red: 1.2, green: -0.1, blue: 0.5)
        #expect(c.hex == "#FF0080")
    }

    @Test func similarColorsAreHardToTell() {
        #expect(TeamColor.red.isHardToTell(from: .red))
        #expect(TeamColor.white.isHardToTell(from: TeamColor(hex: "#F2F2F2")!))
        #expect(!TeamColor.red.isHardToTell(from: .blue))
        #expect(!TeamColor.white.isHardToTell(from: .black))
        // 어두운 색끼리는 더 멀어야 한다: 진보라와 남색(ΔE 33)은 경고, 견본끼리(검정·남색 ΔE 46, 자주·남색)는 경고 없음
        #expect(TeamColor(hex: "#371A89")!.isHardToTell(from: .navy))
        #expect(!TeamColor.black.isHardToTell(from: .navy))
        #expect(!TeamColor.maroon.isHardToTell(from: .navy))
        // 밝은 색끼리는 기준 그대로: 노랑·주황(ΔE 32)은 경고 없음
        #expect(!TeamColor.yellow.isHardToTell(from: .orange))
    }

    @Test func quickPicksStartWithLegacyColors() {
        #expect(Array(TeamColor.quickPicks.prefix(8)) == [.red, .blue, .white, .black, .yellow, .green, .orange, .purple])
        #expect(TeamColor.quickPicks.count == 13)
    }

    @Test func setupWithLegacyColorNamesDecodes() throws {
        var json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(MatchSetup())) as! [String: Any]
        json["homeColor"] = "white"
        json["awayColor"] = "black"
        let setup = try JSONDecoder().decode(MatchSetup.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(setup.homeColor == .white && setup.awayColor == .black)
    }

    @Test func secondaryColorRoundTripsAndOmitsWhenNil() throws {
        var setup = MatchSetup(homeColor: .red, awayColor: .white)
        setup.homeColor2 = .black
        let data = try JSONEncoder().encode(setup)
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(json["homeColor2"] as? String == "#000000")
        #expect(json["awayColor2"] == nil)
        let decoded = try JSONDecoder().decode(MatchSetup.self, from: data)
        #expect(decoded.homeColor2 == .black && decoded.awayColor2 == nil)
    }

    @Test func oldSetupWithoutSecondaryColorDecodes() throws {
        var json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(MatchSetup())) as! [String: Any]
        json["homeColor"] = "white"
        json["awayColor"] = "black"
        let setup = try JSONDecoder().decode(MatchSetup.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(setup.homeColor2 == nil && setup.awayColor2 == nil)
    }

    @Test func secondaryEqualToPrimaryNormalizesToNil() {
        var setup = MatchSetup(homeColor: .red, awayColor: .blue)
        setup.homeColor2 = TeamColor(hex: "#FF3B30")
        setup.awayColor2 = .black
        let n = setup.normalizedColors()
        #expect(n.homeColor2 == nil && n.awayColor2 == .black)
    }

    @Test func pairwiseWarningUsesSecondaryColors() {
        var seoul = MatchSetup(homeColor: .red, awayColor: .black); seoul.homeColor2 = .black
        #expect(seoul.teamColorsHardToTell)                   // 빨강·검정 대 검정
        var vsWhite = seoul; vsWhite.awayColor = .white
        #expect(!vsWhite.teamColorsHardToTell)                // 빨강·검정 대 흰색
        var plain = MatchSetup(homeColor: .red, awayColor: .blue); plain.homeColor2 = .maroon
        #expect(!plain.teamColorsHardToTell)                  // 같은 팀 안의 비슷한 색은 경고 없음
    }

    @Test func summaryAndListItemCarrySecondaryColor() {
        var setup = MatchSetup(homeColor: .red, awayColor: .blue)
        setup.homeColor2 = .black
        let record = MatchRecord(id: UUID(), createdAt: 0, setup: setup, commands: [])
        let summary = MatchSummary(record: record)
        #expect(summary.homeColor2 == .black && summary.awayColor2 == nil)
        let item = MatchListItem(summary)
        var other = summary
        other.homeColor2 = .white
        #expect(item != MatchListItem(other))
        #expect(item == MatchListItem(summary))
    }
}
