import Foundation
import Observation
import RefereeCore

/// 진행 중인 경기 하나. 명령을 검증해 적용하고 즉시 저장한다.
@MainActor
@Observable
public final class MatchSession {
    public private(set) var record: MatchRecord
    public private(set) var state: MatchState
    /// 마지막 저장 실패 메시지. 다음 저장이 성공하면 nil.
    public private(set) var saveError: String?

    @ObservationIgnored private let store: any MatchLogStore
    @ObservationIgnored private let now: () -> Double

    public init(record: MatchRecord, store: any MatchLogStore, now: @escaping () -> Double = { Date().timeIntervalSince1970 }) {
        self.record = record
        self.store = store
        self.now = now
        state = MatchEngine.reduce(rules: record.setup.rules, commands: record.commands)
    }

    public static func start(
        setup: MatchSetup,
        store: any MatchLogStore,
        now: @escaping () -> Double = { Date().timeIntervalSince1970 }
    ) -> MatchSession {
        let record = MatchRecord(id: UUID(), createdAt: now(), setup: setup, commands: [])
        let session = MatchSession(record: record, store: store, now: now)
        session.persist()
        return session
    }

    public var setup: MatchSetup { record.setup }

    public var isFinished: Bool {
        if case .finished = state.phase { true } else { false }
    }

    public var canUndo: Bool {
        !MatchEngine.effectiveCommands(record.commands).isEmpty
    }

    /// 현재 시각으로 명령을 만들어 적용한다. 거부되면 로그에 남기지 않고 false.
    @discardableResult
    public func send(_ kind: CommandKind) -> Bool {
        let command = Command(timestamp: now(), kind: kind)
        var next = state
        do {
            try next.apply(command)
        } catch {
            return false
        }
        record.commands.append(command)
        state = next
        persist()
        return true
    }

    /// 마지막으로 적용된 명령을 되돌린다.
    @discardableResult
    public func undo() -> Bool {
        guard canUndo else { return false }
        record.commands.append(Command(timestamp: now(), kind: .undo))
        state = MatchEngine.reduce(rules: record.setup.rules, commands: record.commands)
        persist()
        return true
    }

    /// 끝난 경기를 보관함으로 옮긴다. 진행 중이거나 저장에 실패하면 false.
    @discardableResult
    public func archiveIfFinished() -> Bool {
        guard isFinished else { return false }
        do {
            try store.finishActive(record)
            saveError = nil
            return true
        } catch {
            saveError = String(describing: error)
            return false
        }
    }

    public func snapshot(at time: Double? = nil) -> MatchSnapshot {
        state.snapshot(at: time ?? now(), warningBeforeEnd: setup.warningBeforeEnd)
    }

    public func nextAlert(after time: Double? = nil) -> MatchAlert? {
        state.nextAlert(after: time ?? now(), warningBeforeEnd: setup.warningBeforeEnd)
    }

    private func persist() {
        do {
            try store.saveActive(record)
            saveError = nil
        } catch {
            saveError = String(describing: error)
        }
    }
}
