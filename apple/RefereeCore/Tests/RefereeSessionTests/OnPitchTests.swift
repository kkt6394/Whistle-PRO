import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct OnPitchTests {
    private static func setup(starters: [Int] = [1, 4, 5, 7, 9, 10]) -> MatchSetup {
        let all = Array(1...18)
        return MatchSetup(homeRoster: all.map { Player(number: $0, name: "P\($0)", starter: starters.contains($0) ? true : nil) })
    }

    private static func state(_ events: [CommandKind]) -> MatchState {
        makeState([(.startPeriod, 0)] + events.enumerated().map { ($0.element, Double(100 + $0.offset * 10)) })
    }

    @Test func onPitchIsNilWithoutStarters() {
        let s = Self.state([])
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup(starters: []), excluding: nil) == nil)
    }

    @Test func onPitchStartsWithStartersAndFollowsSubstitutions() {
        let s = Self.state([.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 17))])
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup()) == [1, 4, 5, 9, 10, 17])
    }

    @Test func sendOffWinsOverSubstitutionBack() {
        let s = Self.state([
            .recordEvent(.card(team: .home, number: 5, kind: .red)),
            .recordEvent(.substitution(team: .home, numberOut: 17, numberIn: 5)),
        ])
        let pitch = s.onPitchNumbers(team: .home, setup: Self.setup())
        #expect(pitch?.contains(5) == false)
    }

    @Test func sinBinStaysOnPitch() {
        let s = Self.state([.recordEvent(.card(team: .home, number: 4, kind: .sinBin))])
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup())?.contains(4) == true)
    }

    @Test func numberOutsideRosterMovesInAndOut() {
        let s = Self.state([.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 23))])
        let pitch = s.onPitchNumbers(team: .home, setup: Self.setup())
        #expect(pitch?.contains(23) == true && pitch?.contains(7) == false)
    }

    @Test func excludingTheEditedSubstitution() {
        let s = Self.state([.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 17))])
        let id = s.events[0].id
        let pitch = s.onPitchNumbers(team: .home, setup: Self.setup(), excluding: id)
        #expect(pitch?.contains(7) == true && pitch?.contains(17) == false)
    }

    @Test func threeStartersStillWork() {
        let s = Self.state([.recordEvent(.substitution(team: .home, numberOut: 1, numberIn: 12))])
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup(starters: [1, 2, 3])) == [2, 3, 12])
    }

    /// 경고 1장 선수 (명단 칩의 노란 카드, 2026-11-04 critique): 두 번째 경고·레드로 퇴장하면 빠진다
    @Test func cautionedNumbersAreSingleYellowsStillPlaying() {
        let s = Self.state([
            .recordEvent(.card(team: .home, number: 4, kind: .yellow)),
            .recordEvent(.card(team: .home, number: 9, kind: .yellow)),
            .recordEvent(.card(team: .home, number: 9, kind: .yellow)),
            .recordEvent(.card(team: .home, number: 10, kind: .yellow)),
            .recordEvent(.card(team: .home, number: 10, kind: .red)),
            .recordEvent(.card(team: .away, number: 7, kind: .yellow)),
            .recordEvent(.card(team: .home, number: 5, kind: .sinBin)),
            .recordEvent(.card(team: .home, number: nil, kind: .yellow)),
        ])
        #expect(s.cautionedNumbers(of: .home) == [4])
        #expect(s.cautionedNumbers(of: .away) == [7])
    }

    @Test func cautionedNumbersSkipTheEditedCard() {
        let s = Self.state([.recordEvent(.card(team: .home, number: 4, kind: .yellow))])
        #expect(s.cautionedNumbers(of: .home, excluding: s.events[0].id).isEmpty)
    }
}
