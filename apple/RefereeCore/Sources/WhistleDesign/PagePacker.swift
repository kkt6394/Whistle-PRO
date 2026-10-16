import CoreGraphics

/// PDF 한 쪽에 쌓을 조각 (보고서 제목·한 줄 등). 조각은 쪽 경계에서 자르지 않는다.
public struct PagePiece: Equatable, Sendable {
    public var height: CGFloat
    /// 앞 조각과의 간격 (쪽 맨 위에서는 두지 않는다)
    public var spacingBefore: CGFloat
    /// 다음 조각과 같은 쪽에 둔다 (제목이 쪽 맨 아래에 혼자 남지 않게)
    public var keepWithNext: Bool

    public init(height: CGFloat, spacingBefore: CGFloat, keepWithNext: Bool = false) {
        self.height = height
        self.spacingBefore = spacingBefore
        self.keepWithNext = keepWithNext
    }
}

public struct PlacedPiece: Equatable, Sendable {
    public var index: Int
    public var page: Int
    /// 쪽 내용 영역 위에서부터
    public var y: CGFloat

    public init(index: Int, page: Int, y: CGFloat) {
        self.index = index
        self.page = page
        self.y = y
    }
}

/// 조각을 위에서부터 쌓다가 넘치면 다음 쪽 맨 위로 (백로그: 긴 그림 한 장을 A4 높이로 잘라 글자 줄이 잘렸다).
/// 한 쪽보다 큰 조각은 새 쪽에 혼자 둔다.
public enum PagePacker {
    public static func pack(_ pieces: [PagePiece], contentHeight: CGFloat) -> [PlacedPiece] {
        var placed: [PlacedPiece] = []
        var page = 0
        var bottom: CGFloat = 0
        var empty = true
        for (index, piece) in pieces.enumerated() {
            // keepWithNext가 이어지면 사슬 끝 조각까지 같은 쪽에 (굵은 줄 → 열 머리 → 첫 줄).
            // 사슬이 한 쪽보다 길면 바로 다음 조각까지만 본다
            var chain = piece.height
            var next = index
            while pieces[next].keepWithNext, next + 1 < pieces.count {
                next += 1
                chain += pieces[next].spacingBefore + pieces[next].height
            }
            if chain > contentHeight, piece.keepWithNext, index + 1 < pieces.count {
                chain = piece.height + pieces[index + 1].spacingBefore + pieces[index + 1].height
            }
            let need = (empty ? 0 : piece.spacingBefore) + chain
            if !empty && bottom + need > contentHeight {
                page += 1
                bottom = 0
                empty = true
            }
            let top = empty ? 0 : bottom + piece.spacingBefore
            placed.append(PlacedPiece(index: index, page: page, y: top))
            bottom = top + piece.height
            empty = false
        }
        return placed
    }

    public static func pageCount(_ placed: [PlacedPiece]) -> Int {
        (placed.map(\.page).max() ?? 0) + 1
    }
}
