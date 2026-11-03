import RefereeCore

/// [실행 취소]가 되돌릴 마지막 명령. 메뉴의 버튼 아래 한 줄과 되돌린 뒤 메인 작은 줄 안내에 쓴다 (2026-11-04 A6 critique).
/// 화면 문구로 바꾸는 일은 앱이 한다.
public enum UndoTarget: Equatable, Sendable {
    /// 이벤트 기록 (그 줄)
    case recorded(EventRow)
    /// 이벤트 번호 수정 (고친 뒤의 줄)
    case edited(EventRow)
    /// 이벤트 삭제 (지우기 전의 줄)
    case deleted(EventRow)
    /// 중단을 시작함 / 중단을 끝내고 재개함
    case stoppage(started: Bool)
    /// 기간 시작·종료 (기간 라벨 "1H" 등)
    case periodStarted(String)
    case periodEnded(String)
    case kickoffTeam
    case shootoutFirstTeam
    case periodLength
    case abandoned
}

extension MatchSession {
    /// 되돌릴 것이 없으면 nil
    public var undoTarget: UndoTarget? {
        guard canUndo else { return nil }
        let effective = MatchEngine.effectiveCommands(record.commands)
        guard let last = effective.last else { return nil }
        let setup = self.setup
        func row(_ id: String, in state: MatchState) -> EventRow? {
            state.eventRows(setup: setup).first { $0.id == id }
        }
        switch last.kind {
        case .recordEvent:
            return row(last.id, in: state).map(UndoTarget.recorded)
        case .editEvent(let id, _):
            return row(id, in: state).map(UndoTarget.edited)
        case .deleteEvent(let id):
            let before = MatchEngine.reduce(rules: record.setup.rules, commands: Array(effective.dropLast()))
            return row(id, in: before).map(UndoTarget.deleted)
        case .toggleStoppage:
            let open = state.periods.last?.stoppages.last.map { $0.end == nil } ?? false
            return .stoppage(started: open)
        case .startPeriod:
            return state.periods.last.map { .periodStarted($0.ref.label(periodCount: state.rules.periodCount)) }
        case .endPeriod:
            return state.periods.last.map { .periodEnded($0.ref.label(periodCount: state.rules.periodCount)) }
        case .setKickoffTeam: return .kickoffTeam
        case .setShootoutFirstTeam: return .shootoutFirstTeam
        case .adjustPeriodLength: return .periodLength
        case .abandonMatch: return .abandoned
        case .undo: return nil
        }
    }
}
