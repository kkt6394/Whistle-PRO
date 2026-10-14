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

    /// 실기기(워치 SE)에서 0.4초 간격 notification 두 번은 한 번으로 느껴졌다 (앞 진동이 끝나기 전이라 무시됨).
    @Test func repeatedHapticsLeaveRoomForEachTap() {
        for kind in kinds where kind.hapticPattern.repeats > 1 {
            #expect(kind.hapticPattern.interval >= 0.6, "\(kind)")
        }
    }
}
