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
}
