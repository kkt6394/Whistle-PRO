/// 작업을 seconds까지만 기다린다. 넘기면 작업을 취소하고 nil (지도 스냅숏처럼 네트워크에 따라 끝없이 걸릴 수 있는 작업).
public func withTimeout<Value: Sendable>(seconds: Double, _ operation: @escaping @Sendable () async -> Value?) async -> Value? {
    await withTaskGroup(of: Value?.self) { group in
        group.addTask { await operation() }
        group.addTask {
            try? await Task.sleep(for: .seconds(seconds))
            return nil
        }
        let first = await group.next() ?? nil
        group.cancelAll()
        return first
    }
}
