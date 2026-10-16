import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct MatchNoteTests {
    @Test func competitionRoundTripsAndDefaultsNil() throws {
        let setup = MatchSetup(competition: "2026 서울 리그")
        let data = try JSONEncoder().encode(setup)
        #expect(try JSONDecoder().decode(MatchSetup.self, from: data).competition == "2026 서울 리그")
        let json = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(MatchSetup())) as? [String: Any])
        #expect(json["competition"] == nil)   // 예전 워치·Kotlin과 같은 모양
    }
}
