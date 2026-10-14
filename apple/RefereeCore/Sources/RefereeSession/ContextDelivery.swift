/// 폰 → 워치로 보내는 마지막 내용(프리셋·구독)과 전달 여부.
/// 워치 앱이 없거나 연결 전이면 기억해 두었다가 보내고, 워치 앱이 새로 설치되면(재설치 포함) 다시 보낸다.
/// 새로 설치된 워치 앱은 이전 내용을 갖고 있지 않기 때문이다.
public struct ContextDelivery<Payload> {
    private var last: Payload?
    private var undelivered = false
    private var installed = false

    public init() {}

    /// 보낼 내용을 기억한다. 지금 보낼 수 있으면 그 내용을, 아니면 nil을 돌려준다.
    public mutating func send(_ payload: Payload, canDeliver: Bool) -> Payload? {
        last = payload
        undelivered = !canDeliver
        return canDeliver ? payload : nil
    }

    /// 워치 상태가 바뀌었을 때 지금 보내야 할 내용 (없으면 nil)
    public mutating func watchStateChanged(installed nowInstalled: Bool, canDeliver: Bool) -> Payload? {
        let newlyInstalled = nowInstalled && !installed
        installed = nowInstalled
        guard canDeliver, let last, undelivered || newlyInstalled else { return nil }
        undelivered = false
        return last
    }
}
