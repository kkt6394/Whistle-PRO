import Foundation
import RefereeCore

/// [킥오프 취소] 뒤 킥오프 대기에 되살릴 설정 (2026-11-06 Ruling 11·12).
/// 버린 경기 기록의 setup 전체(팀 이름·규칙·기간 길이)를 설정(draft)으로 되돌리고, 팀 색은 기존 방식대로 "이번 경기만 색"으로 둔다:
/// draft에는 지금 설정의 색을 남기고, 버린 경기 색이 그와 다르면 colorOverride. 앱이 다시 실행돼 복원한 경기도 기록만으로 같다.
/// 프리셋 id는 기록에 없어 고른 프리셋은 그대로 둔다 (폰이 새 프리셋을 보내면 draft를 덮는 것은 허용)
public enum KickoffCancel {
    public static func restore(setup: MatchSetup, draft: MatchSetup) -> (draft: MatchSetup, colorOverride: TeamColors?) {
        let draftColors = TeamColors(setup: draft)
        let colors = TeamColors(setup: setup)
        return (draftColors.applied(to: setup), colors == draftColors ? nil : colors)
    }
}
