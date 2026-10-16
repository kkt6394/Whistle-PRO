/// 경기 상세 탭 (디자인 개편 §5.2): 내용이 있는 탭만
public enum DetailTab: Hashable, Sendable, CaseIterable {
    case record, workout, map

    public static func available(hasWorkout: Bool, hasMap: Bool) -> [DetailTab] {
        var tabs: [DetailTab] = [.record]
        if hasWorkout { tabs.append(.workout) }
        if hasMap { tabs.append(.map) }
        return tabs
    }
}
