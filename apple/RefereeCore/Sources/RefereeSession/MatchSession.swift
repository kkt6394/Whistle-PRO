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
    /// 마지막으로 받아들인 명령의 id (방금 기록한 이벤트를 찾을 때 쓴다)
    public private(set) var lastCommandID: String?

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
        now: @escaping () -> Double = { Date().timeIntervalSince1970 },
        unlocked: Bool = true
    ) -> MatchSession {
        let record = MatchRecord(id: UUID(), createdAt: now(), setup: setup, commands: [], unlocked: unlocked)
        let session = MatchSession(record: record, store: store, now: now)
        session.persist()
        return session
    }

    public var setup: MatchSetup { record.setup }

    public var isFinished: Bool {
        if case .finished = state.phase { true } else { false }
    }

    /// 첫 킥오프(startPeriod)와 그 이전 명령은 되돌리지 않는다.
    /// 되돌리면 시작 버튼이 없는 화면에 갇히기 때문이다.
    public var canUndo: Bool {
        let effective = MatchEngine.effectiveCommands(record.commands)
        guard let kickoff = effective.firstIndex(where: { $0.kind == .startPeriod }) else { return !effective.isEmpty }
        return effective.count > kickoff + 1
    }

    /// [킥오프 취소]를 보일지: 첫 기간(전반, 쿼터제면 1쿼터)이 진행 중이고 아직 한 번도 끝내지 않았을 때만 (2026-11-06).
    /// 하프타임·후반·연장·승부차기·종료(전반 중 경기 중단 포함)면 거짓. 전반 종료를 실행 취소해 전반으로 돌아오면 다시 참.
    /// 첫 킥오프는 canUndo로 되돌리지 않는다 — 실수로 누른 킥오프는 이것으로 경기 자체를 없던 일로 한다
    public var canCancelKickoff: Bool {
        state.phase == .period(PeriodRef(.regular, 1)) && state.periods.count == 1
    }

    /// 현재 시각으로 명령을 만들어 적용한다. 거부되면 로그에 남기지 않고 false.
    /// at을 주면 그 시각으로 찍는다 (이벤트는 점수 칸을 누른 순간, 2026-10-28). 순서가 꼬이지 않게 마지막 명령과 지금 사이로 자른다.
    @discardableResult
    public func send(_ kind: CommandKind, at: Double? = nil) -> Bool {
        let current = now()
        var timestamp = current
        if let at {
            let earliest = min(record.commands.last?.timestamp ?? at, current)
            timestamp = min(max(at, earliest), current)
        }
        let command = Command(timestamp: timestamp, kind: kind)
        var next = state
        do {
            try next.apply(command)
        } catch {
            return false
        }
        record.commands.append(command)
        lastCommandID = command.id
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
        state.snapshot(at: time ?? now(), warningBeforeEnd: setup.warningBeforeEnd,
                       breakWarningBeforeEnd: setup.breakWarningBeforeEnd)
    }

    public func nextAlert(after time: Double? = nil) -> MatchAlert? {
        state.nextAlert(after: time ?? now(), warningBeforeEnd: setup.warningBeforeEnd,
                        breakWarningBeforeEnd: setup.breakWarningBeforeEnd)
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
