import Testing
import RefereeCore
@testable import RefereeSession

struct HapticPatternTests {
    private let kinds: [AlertKind] = [.warningBeforeEnd, .periodEnd, .breakEndingSoon, .breakEnd, .sinBinEnd]

    @Test func everyAlertHasDistinctPattern() {
        #expect(Set(kinds.map(\.hapticPattern)).count == kinds.count)
    }

    @Test func periodEndIsTheLongestPattern() {
        let longest = kinds.map(\.hapticPattern.repeats).max()
        #expect(AlertKind.periodEnd.hapticPattern.repeats == longest)
    }

    /// 실기기(워치 SE)에서 directionUp 한 번은 손목으로 느끼지 못했다. 예고도 강한 진동이어야 한다.
    @Test func warningsUseStrongTones() {
        let strong: Set<HapticTone> = [.notification, .retry]
        #expect(strong.contains(AlertKind.warningBeforeEnd.hapticPattern.tone))
        #expect(strong.contains(AlertKind.breakEndingSoon.hapticPattern.tone))
    }
}
