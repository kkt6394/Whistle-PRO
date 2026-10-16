import Foundation
import Testing
@testable import RefereeSession

/// 워치가 폰의 "받았다" 확인을 놓쳐도 다시 받게 하는 보조 장치 (실기기 21번: 앱을 다시 열어야 "보내지 않은 경기"가 사라졌다)
struct DeliveryRetryTests {
    /// 앱이 화면에 나타날 때마다 다시 보내되, 경기 중 손목을 들 때마다 보내지는 않는다
    @Test func resendOnActivationIsThrottled() {
        var throttle = ResendThrottle(interval: 30)
        let results = [100.0, 110, 129, 130].map { throttle.allows(at: $0) }
        #expect(results == [true, false, false, true])
    }

    /// 백그라운드로 깨어났을 때 받을 데이터가 남아 있는 동안 기다린다
    @Test func waitsUntilConditionHolds() async {
        let start = ContinuousClock.now
        let done = await waitUntil(timeout: .seconds(2), poll: .milliseconds(10)) {
            ContinuousClock.now - start > .milliseconds(50)
        }
        #expect(done)
    }

    @Test func waitGivesUpAfterTimeout() async {
        let done = await waitUntil(timeout: .milliseconds(100), poll: .milliseconds(10)) { false }
        #expect(!done)
    }
}

/// 경기 확인 누락 후속: 폰 앱이 꺼져 있는 동안 워치가 화면에 나타날 때마다 같은 경기를 다시 보냈다
struct MatchSendThrottleTests {
    @Test func sameMatchIsNotResentWithinInterval() {
        var throttle = MatchSendThrottle(interval: 600)
        let a = UUID(), b = UUID()
        do { let ok = throttle.allows(a, at: 0); #expect(ok) }
        do { let ok = throttle.allows(a, at: 30); #expect(!ok) }
        do { let ok = throttle.allows(b, at: 30); #expect(ok) }
        do { let ok = throttle.allows(a, at: 601); #expect(ok) }
    }

    /// 전송이 실패하면 바로 다시 보낼 수 있다
    @Test func failureClearsTheMatch() {
        var throttle = MatchSendThrottle(interval: 600)
        let a = UUID()
        do { let ok = throttle.allows(a, at: 0); #expect(ok) }
        throttle.failed(a)
        do { let ok = throttle.allows(a, at: 1); #expect(ok) }
    }
}
