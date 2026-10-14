import Testing
@testable import RefereeSession

/// 폰 → 워치 구독·프리셋 전달: 워치 앱이 새로 설치되면 마지막 내용을 다시 보낸다.
/// (실기기: 워치 앱을 다시 설치하면 폰 앱을 다시 열 때까지 워치가 잠겨 있었다)
struct ContextDeliveryTests {
    @Test func sendsImmediatelyWhenWatchIsReachable() {
        var delivery = ContextDelivery<String>()
        _ = delivery.watchStateChanged(installed: true, canDeliver: true)
        #expect(delivery.send("pro", canDeliver: true) == "pro")
    }

    @Test func holdsUntilWatchAppIsInstalled() {
        var delivery = ContextDelivery<String>()
        #expect(delivery.send("pro", canDeliver: false) == nil)
        #expect(delivery.watchStateChanged(installed: true, canDeliver: true) == "pro")
    }

    @Test func resendsLastContextWhenWatchAppIsReinstalled() {
        var delivery = ContextDelivery<String>()
        _ = delivery.watchStateChanged(installed: true, canDeliver: true)
        #expect(delivery.send("pro", canDeliver: true) == "pro")
        // 워치 앱 삭제 → 다시 설치
        #expect(delivery.watchStateChanged(installed: false, canDeliver: false) == nil)
        #expect(delivery.watchStateChanged(installed: true, canDeliver: true) == "pro")
    }

    @Test func noResendWhenNothingChanged() {
        var delivery = ContextDelivery<String>()
        _ = delivery.watchStateChanged(installed: true, canDeliver: true)
        _ = delivery.send("pro", canDeliver: true)
        #expect(delivery.watchStateChanged(installed: true, canDeliver: true) == nil)
    }

    @Test func nothingToResendBeforeFirstSend() {
        var delivery = ContextDelivery<String>()
        #expect(delivery.watchStateChanged(installed: true, canDeliver: true) == nil)
    }
}
