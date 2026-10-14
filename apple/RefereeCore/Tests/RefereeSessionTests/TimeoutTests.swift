import Foundation
import Testing
@testable import RefereeSession

/// 지도 스냅숏처럼 오래 걸릴 수 있는 작업을 기다리는 시간 상한 (활동 카드)
struct TimeoutTests {
    @Test func returnsValueWhenFastEnough() async {
        let value = await withTimeout(seconds: 1) { () -> Int? in 7 }
        #expect(value == 7)
    }

    @Test func givesUpWhenTooSlow() async {
        let started = Date()
        let value = await withTimeout(seconds: 0.2) { () -> Int? in
            try? await Task.sleep(for: .seconds(5))
            return 7
        }
        #expect(value == nil)
        #expect(Date().timeIntervalSince(started) < 2)
    }

    @Test func passesThroughNil() async {
        let value = await withTimeout(seconds: 1) { () -> Int? in nil }
        #expect(value == nil)
    }
}
