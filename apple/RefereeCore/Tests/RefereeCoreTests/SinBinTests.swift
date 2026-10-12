import Testing
@testable import RefereeCore

private let sinBinRules = RuleSet(sinBin: true, sinBinLength: 600)

struct SinBinTests {
    @Test func countsDownWithPlayedTime() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), card(.away, 8, .sinBin, at: 300, id: "s")])
        #expect(s.activeSinBins(at: 400) == [ActiveSinBin(eventId: "s", team: .away, number: 8, remainingSeconds: 500)])
        #expect(s.playersOnField(at: 400) == TeamPair(home: 11, away: 10))
    }

    @Test func expiresAfterLength() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), card(.away, 8, .sinBin, at: 300)])
        #expect(s.activeSinBins(at: 900).isEmpty)
        #expect(s.playersOnField(at: 900) == TeamPair(home: 11, away: 11))
    }

    @Test func pausesDuringInterval() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), card(.home, 3, .sinBin, at: 2500), cmd(.endPeriod, at: 2700),
                                   cmd(.startPeriod, at: 3600)])
        #expect(s.activeSinBins(at: 3000).first?.remaining == 400)
        #expect(s.activeSinBins(at: 3700).first?.remaining == 300)
    }

    @Test func issuedDuringIntervalStartsAtNextPeriod() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700), card(.home, 3, .sinBin, at: 2800),
                                   cmd(.startPeriod, at: 3600)])
        #expect(s.activeSinBins(at: 3600).first?.remaining == 600)
        #expect(s.activeSinBins(at: 3700).first?.remaining == 500)
    }

    @Test func fractionalTimestampsRoundRemainingUp() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), card(.home, 3, .sinBin, at: 10.4)])
        #expect(s.activeSinBins(at: 20).first?.remaining == 591)
        #expect(s.activeSinBins(at: 610.3).first?.remaining == 1)
    }
}
