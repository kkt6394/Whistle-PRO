import Testing
import RefereeCore
@testable import RefereeSession
@testable import WhistleDesign

struct MainStatusTests {
    let early = SinBinBadge(team: .away, number: 8, remaining: "2:00", seconds: 120)
    let late = SinBinBadge(team: .home, number: 4, remaining: "7:30", seconds: 450)

    @Test func nothingToShow() {
        #expect(MainStatus.select(shortHanded: [], sinBins: []) == nil)
    }

    @Test func shortHandedWinsOverSinBin() {
        #expect(MainStatus.select(shortHanded: [.away], sinBins: [early]) == .shortHanded([.away]))
    }

    @Test func earliestEndingSinBinWithCountOfOthers() {
        #expect(MainStatus.select(shortHanded: [], sinBins: [late, early]) == .sinBin(early, more: 1))
        #expect(MainStatus.select(shortHanded: [], sinBins: [late]) == .sinBin(late, more: 0))
    }

    @Test func tieKeepsFirstListed() {
        let twin = SinBinBadge(team: .home, number: 2, remaining: "2:00", seconds: 120)
        #expect(MainStatus.select(shortHanded: [], sinBins: [early, twin]) == .sinBin(early, more: 1))
    }
}

struct KickMarksTests {
    @Test func beforeFirstKickAllPending() {
        #expect(KickMarks.marks(results: [], perTeam: 5, limit: 7) == Array(repeating: .pending, count: 5))
    }

    @Test func takenKicksThenRemaining() {
        #expect(KickMarks.marks(results: [true, false], perTeam: 5, limit: 7) == [.scored, .missed, .pending, .pending, .pending])
    }

    @Test func longShootoutKeepsLatestMarks() {
        let results = [true, true, false, true, true, true, false, true]
        #expect(KickMarks.marks(results: results, perTeam: 5, limit: 6) == [.missed, .scored, .scored, .scored, .missed, .scored])
    }

    @Test func zeroOrNegativeLimitIsEmpty() {
        #expect(KickMarks.marks(results: [true], perTeam: 0, limit: 0).isEmpty)
        #expect(KickMarks.marks(results: [true], perTeam: 5, limit: -1).isEmpty)
    }
}
