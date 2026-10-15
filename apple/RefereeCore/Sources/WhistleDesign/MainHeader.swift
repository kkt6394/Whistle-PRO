/// 워치 메인 상단 띠의 한 조각 (B1 스펙 §3.1)
public enum MainHeaderItem: Equatable, Sendable {
    case warning
    case period(String)
    case stoppage(String, running: Bool)
}

/// 상단 띠 순서. 띠 폭이 좁아(시스템 시각 왼쪽) 긴 문구는 줄어들거나 잘리므로,
/// 놓치면 안 되는 ⚠를 맨 앞에 둔다. 그다음 기간 이름, 중단 시간.
public enum MainHeader {
    public static func items(period: String?, stoppage: String?, stoppageRunning: Bool, warning: Bool) -> [MainHeaderItem] {
        var items: [MainHeaderItem] = []
        if warning { items.append(.warning) }
        if let period { items.append(.period(period)) }
        if let stoppage { items.append(.stoppage(stoppage, running: stoppageRunning)) }
        return items
    }
}
