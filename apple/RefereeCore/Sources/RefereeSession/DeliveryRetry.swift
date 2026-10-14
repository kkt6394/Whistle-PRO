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
