import Foundation
/// 앱이 화면에 나타날 때 확인받지 못한 경기를 다시 보내는 간격 제한.
/// 경기 중 손목을 들 때마다(앱 활성화) 파일을 다시 보내지 않게 한다.
public struct ResendThrottle: Sendable {
    public let interval: Double
    private var last: Double?

    public init(interval: Double) {
        self.interval = interval
    }

    public mutating func allows(at now: Double) -> Bool {
        if let last, now - last < interval { return false }
        last = now
        return true
    }
}

/// 경기마다 마지막으로 보낸 시각: 같은 경기를 interval 안에 다시 보내지 않는다.
/// 폰 앱이 꺼져 있으면 전송은 끝나도(파일은 폰에 도착) 확인이 오지 않아, 워치가 화면에 나타날 때마다 다시 보냈다 (경기 확인 누락 후속)
public struct MatchSendThrottle: Sendable {
    public let interval: Double
    private var lastSent: [UUID: Double] = [:]

    public init(interval: Double) {
        self.interval = interval
    }

    public mutating func allows(_ matchID: UUID, at now: Double) -> Bool {
        if let last = lastSent[matchID], now - last < interval { return false }
        lastSent[matchID] = now
        return true
    }

    /// 전송이 실패했으면 바로 다시 보낼 수 있게
    public mutating func failed(_ matchID: UUID) {
        lastSent[matchID] = nil
    }
}

/// 워치가 폰에 "이 경기들 받았니?"를 묻는 정리 경로의 판단 (경기 확인 누락 후속).
/// 답을 기다리는 동안은 다시 묻지 않고, 답을 받은 뒤 interval 안에는 다시 묻지 않는다 (reachable이 깜빡여도 겹치지 않게).
/// 실패하면 다음 기회(활성화·reachable)에 바로 다시 묻는다.
public struct ReceiptCheck: Sendable {
    /// 한 번에 묻는 경기 수 상한: 메시지 한도(64KB) 안에 들게 (경기 ID 하나 약 40바이트)
    public static let maxIDs = 500
    public let interval: Double
    public private(set) var inFlight = false
    private var lastAnswered: Double?

    public init(interval: Double) {
        self.interval = interval
    }

    /// 물을 경기 목록. 묻지 않을 때(답 대기 중, 간격 안, 남은 경기 없음)는 nil.
    public mutating func begin(pending: [UUID], at now: Double) -> [UUID]? {
        guard !inFlight, !pending.isEmpty else { return nil }
        if let lastAnswered, now - lastAnswered < interval { return nil }
        inFlight = true
        return Array(pending.prefix(Self.maxIDs))
    }

    /// 답을 받았다: 물은 경기 중 폰이 받았다고 한 경기 (묻지 않은 ID는 무시한다)
    public mutating func answered(asked: [UUID], received: [UUID], at now: Double) -> [UUID] {
        inFlight = false
        lastAnswered = now
        let receivedSet = Set(received)
        return asked.filter { receivedSet.contains($0) }
    }

    /// 보내지 못했거나 답이 오지 않았다
    public mutating func failed() {
        inFlight = false
    }
}

/// 조건이 참이 될 때까지 기다린다. 시간이 다 되면 false.
/// (백그라운드로 깨어난 워치가 받을 데이터를 다 받을 때까지 잠들지 않게 할 때 쓴다)
public func waitUntil(timeout: Duration, poll: Duration,
                      _ condition: @Sendable () async -> Bool) async -> Bool {
    let deadline = ContinuousClock.now + timeout
    while ContinuousClock.now < deadline {
        if await condition() { return true }
        try? await Task.sleep(for: poll)
        if Task.isCancelled { return false }
    }
    return await condition()
}
