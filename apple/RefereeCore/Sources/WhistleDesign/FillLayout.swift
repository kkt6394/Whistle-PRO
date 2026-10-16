import CoreGraphics

/// 화면 세로 배분의 한 요소 (B2 스펙 §2·§3). height는 이 요소의 최소(=기준) 높이다.
public struct FillItem: Equatable, Sendable {
    public enum Role: Equatable, Sendable {
        case fixed
        /// 남는 높이를 받는 주인공. 여럿이면 똑같이 나눈다
        case hero
        /// 주인공이 상한에 닿은 뒤 남는 높이를 받는다
        case second
        /// 모자라면 order가 작은 것부터 숨긴다
        case hideable(order: Int)
    }

    public var height: CGFloat
    public var role: Role

    public init(_ height: CGFloat, _ role: Role = .fixed) {
        self.height = height
        self.role = role
    }
}

public struct FillResult: Equatable, Sendable {
    /// 요소별 높이 (숨긴 요소는 0)
    public var heights: [CGFloat]
    public var hidden: [Bool]
    /// 주인공·두 번째 요소가 다 받고도 남으면 간격이 커진다
    public var spacing: CGFloat
    /// 숨길 줄을 숨겨서라도 모두 들어가는지. false면 화면이 핵심 버튼만 고정하고 나머지를 스크롤한다
    public var fits: Bool
}

/// 남는 높이 배분: 숨길 줄 → 주인공(상한) → 두 번째 → 간격 (B2 스펙 §2)
public enum FillLayout {
    /// 화면이 높이를 나눌 때 남겨 두는 여유: 배분한 높이를 그대로 쓰면 반올림으로 넘쳐 스크롤이 생긴다 (B2 리뷰: 화면마다 쓰던 4)
    public static let slack: CGFloat = 4

    public static func layout(available: CGFloat, spacing: CGFloat, items: [FillItem], heroCap: CGFloat? = 60) -> FillResult {
        var hidden = items.map { _ in false }
        // 높이 0인 고정 항목은 화면이 그리지 않는 조건부 항목이라 간격도 세지 않는다
        func shown(_ index: Int) -> Bool {
            !hidden[index] && !(items[index].height == 0 && items[index].role == .fixed)
        }
        func total() -> CGFloat {
            let visible = items.indices.filter(shown)
            return visible.reduce(0) { $0 + items[$1].height } + spacing * CGFloat(max(0, visible.count - 1))
        }
        let hideOrder = items.indices.compactMap { index -> (Int, Int)? in
            if case .hideable(let order) = items[index].role { return (order, index) }
            return nil
        }.sorted { $0 < $1 }.map(\.1)
        for index in hideOrder where total() > available {
            hidden[index] = true
        }

        var heights = items.indices.map { hidden[$0] ? 0 : items[$0].height }
        let shortfall = available - total()
        let fits = shortfall >= -0.001
        var spare = max(0, shortfall)

        // 주인공끼리 똑같이 나누고, 상한에 닿은 주인공의 남은 몫은 아직 덜 찬 주인공에게 다시 나눈다
        var growing = items.indices.filter { !hidden[$0] && items[$0].role == .hero }
        while !growing.isEmpty, spare > 0.001 {
            let each = spare / CGFloat(growing.count)
            for index in growing {
                let grow = heroCap.map { min(each, max(0, $0 - heights[index])) } ?? each
                heights[index] += grow
                spare -= grow
            }
            growing = growing.filter { index in heroCap.map { heights[index] < $0 - 0.001 } ?? true }
        }
        let seconds = items.indices.filter { !hidden[$0] && items[$0].role == .second }
        if !seconds.isEmpty, spare > 0 {
            for index in seconds { heights[index] += spare / CGFloat(seconds.count) }
            spare = 0
        }
        let visibleCount = items.indices.filter(shown).count
        let finalSpacing = visibleCount > 1 ? spacing + spare / CGFloat(visibleCount - 1) : spacing
        return FillResult(heights: heights, hidden: hidden, spacing: finalSpacing, fits: fits)
    }
}
