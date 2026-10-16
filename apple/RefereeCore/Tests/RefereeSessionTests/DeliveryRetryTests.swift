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

/// 경기 확인 누락 후속: 워치가 활성화·reachable 될 때 "이 경기들 받았니?"를 폰에 묻는다
struct ReceiptCheckTests {
    @Test func asksPendingMatches() {
        var check = ReceiptCheck(interval: 60)
        let a = UUID(), b = UUID()
        do { let asked = check.begin(pending: [a, b], at: 0); #expect(asked == [a, b]) }
        #expect(check.inFlight)
    }

    @Test func doesNotAskWithoutPendingMatches() {
        var check = ReceiptCheck(interval: 60)
        do { let asked = check.begin(pending: [], at: 0); #expect(asked == nil) }
        #expect(!check.inFlight)
    }

    /// reachable이 깜빡여도 답을 기다리는 동안은 다시 묻지 않는다
    @Test func doesNotOverlapWhileWaitingForReply() {
        var check = ReceiptCheck(interval: 60)
        let a = UUID()
        do { let asked = check.begin(pending: [a], at: 0); #expect(asked != nil) }
        do { let asked = check.begin(pending: [a], at: 1); #expect(asked == nil) }
    }

    /// 답에 든 경기만 확인으로 본다 (묻지 않은 ID는 무시)
    @Test func confirmsOnlyAskedMatchesInReply() {
        var check = ReceiptCheck(interval: 60)
        let a = UUID(), b = UUID(), stranger = UUID()
        let asked = check.begin(pending: [a, b], at: 0)!
        let confirmed = check.answered(asked: asked, received: [b, stranger], at: 1)
        #expect(confirmed == [b])
        #expect(!check.inFlight)
    }

    /// 답을 받은 뒤에는 간격 안에 다시 묻지 않는다
    @Test func waitsIntervalAfterAnswer() {
        var check = ReceiptCheck(interval: 60)
        let a = UUID()
        let asked = check.begin(pending: [a], at: 0)!
        _ = check.answered(asked: asked, received: [], at: 1)
        do { let asked = check.begin(pending: [a], at: 30); #expect(asked == nil) }
        do { let asked = check.begin(pending: [a], at: 61); #expect(asked == [a]) }
    }

    /// 실패하면 다음 기회에 바로 다시 묻는다
    @Test func failureAllowsAskingAgain() {
        var check = ReceiptCheck(interval: 60)
        let a = UUID()
        do { let asked = check.begin(pending: [a], at: 0); #expect(asked != nil) }
        check.failed()
        do { let asked = check.begin(pending: [a], at: 1); #expect(asked == [a]) }
    }

    /// 메시지 한도 안에 들게 한 번에 묻는 수를 제한한다
    @Test func limitsIDsPerQuery() throws {
        var check = ReceiptCheck(interval: 60)
        let many = (0..<(ReceiptCheck.maxIDs + 10)).map { _ in UUID() }
        let begun = check.begin(pending: many, at: 0)
        let asked = try #require(begun)
        #expect(asked == Array(many.prefix(ReceiptCheck.maxIDs)))
        let size = try PropertyListSerialization.data(fromPropertyList: SyncPayload.receiptQuery(asked), format: .binary, options: 0).count
        #expect(size < 65_536)
    }

    /// 디스크를 읽기 전에 물을 수 있는지부터 본다: 답 대기 중이거나 간격 안이면 false, 상태는 바꾸지 않는다
    @Test func canAskWithoutTouchingState() {
        var check = ReceiptCheck(interval: 60)
        let a = UUID()
        #expect(check.canAsk(at: 0))
        #expect(check.canAsk(at: 0))
        #expect(!check.inFlight)
        let asked = check.begin(pending: [a], at: 0)!
        #expect(!check.canAsk(at: 1))
        _ = check.answered(asked: asked, received: [], at: 1)
        #expect(!check.canAsk(at: 30))
        #expect(check.canAsk(at: 61))
        check.failed()
        #expect(check.canAsk(at: 61))
    }

    /// 물은 경기 중 폰이 받았다고 한 것만 지운다: 묻고 난 뒤 보관된 경기는 폰이 받았다고 해도 남긴다
    @Test func deletesOnlyAskedMatchesNotThoseArchivedAfterAsking() {
        let a = UUID(), b = UUID(), later = UUID()
        #expect(ReceiptCheck.toDelete(asked: [a, b], received: [b, later]) == [b])
        #expect(ReceiptCheck.toDelete(asked: [a], received: []) == [])
    }

    /// 앱을 켤 때 다시 보내기: 폰에 닿고 물을 수 있거나 답을 기다리는 중이면 답(또는 실패) 뒤에 보낸다 — 이미 받은 경기를 다시 보내지 않게.
    /// 닿지 않거나 간격 안이라 묻지 않으면 바로 보낸다
    @Test func resendWaitsForReceiptAnswerOnlyWhenAsking() {
        var check = ReceiptCheck(interval: 60)
        #expect(!check.sendsAfterAnswer(reachable: false, at: 0))
        #expect(check.sendsAfterAnswer(reachable: true, at: 0))
        let asked = check.begin(pending: [UUID()], at: 0)!
        #expect(check.sendsAfterAnswer(reachable: true, at: 1))
        #expect(!check.sendsAfterAnswer(reachable: false, at: 1))
        _ = check.answered(asked: asked, received: [], at: 1)
        #expect(!check.sendsAfterAnswer(reachable: true, at: 30))
        #expect(check.sendsAfterAnswer(reachable: true, at: 61))
    }
}
