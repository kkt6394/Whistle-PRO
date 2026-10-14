import Testing
import RefereeCore
@testable import RefereeSession

/// 동전 던지기: 결과는 앞면/뒷면 둘 뿐이고 어느 쪽으로도 치우치지 않는다.
struct CoinTossTests {
    /// 시드 고정 생성기 (SplitMix64)
    private struct Seeded: RandomNumberGenerator {
        var state: UInt64
        mutating func next() -> UInt64 {
            state &+= 0x9E37_79B9_7F4A_7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
            z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
            return z ^ (z >> 31)
        }
    }

    @Test func tossIsFair() {
        var generator = Seeded(state: 42)
        let tosses = (0..<1000).map { _ in CoinSide.toss(using: &generator) }
        let heads = tosses.filter { $0 == .heads }.count
        #expect((450...550).contains(heads), "앞면 \(heads)/1000")
    }

    @Test func sameSeedGivesSameResult() {
        var a = Seeded(state: 7), b = Seeded(state: 7)
        #expect((0..<20).map { _ in CoinSide.toss(using: &a) } == (0..<20).map { _ in CoinSide.toss(using: &b) })
    }

    /// 결과 진동은 한 번, 경기 알림과 겹치지 않는 종류
    @Test func resultHapticIsSingleAndDistinctFromAlerts() {
        let alerts: [AlertKind] = [.warningBeforeEnd, .periodEnd, .breakEndingSoon, .breakEnd, .sinBinEnd]
        #expect(CoinSide.resultHaptic.repeats == 1)
        #expect(!alerts.map(\.hapticPattern).contains(CoinSide.resultHaptic))
    }
}
