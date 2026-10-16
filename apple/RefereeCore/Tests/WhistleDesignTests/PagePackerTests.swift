import CoreGraphics
import Testing
@testable import WhistleDesign

/// PDF 쪽 나누기 (백로그: 쪽 경계에서 글자 줄이 잘리고, A4 배수를 살짝 넘으면 거의 빈 쪽이 생겼다)
struct PagePackerTests {
    private func piece(_ h: CGFloat, spacing: CGFloat = 4, keep: Bool = false) -> PagePiece {
        PagePiece(height: h, spacingBefore: spacing, keepWithNext: keep)
    }

    @Test func piecesStackOnOnePageWhenTheyFit() {
        let placed = PagePacker.pack([piece(20), piece(30), piece(10)], contentHeight: 100)
        #expect(placed == [PlacedPiece(index: 0, page: 0, y: 0), PlacedPiece(index: 1, page: 0, y: 24), PlacedPiece(index: 2, page: 0, y: 58)])
    }

    /// 줄은 쪽 경계에서 잘리지 않고 다음 쪽 맨 위로 간다 (맨 위에서는 앞 간격을 두지 않는다)
    @Test func pieceThatDoesNotFitMovesToNextPageTop() {
        let placed = PagePacker.pack([piece(60), piece(30), piece(30)], contentHeight: 100)
        #expect(placed.map(\.page) == [0, 0, 1])
        #expect(placed[2].y == 0)
    }

    /// 제목은 첫 줄과 같은 쪽에
    @Test func headingKeepsWithNextLine() {
        let placed = PagePacker.pack([piece(80), piece(10, keep: true), piece(20)], contentHeight: 100)
        #expect(placed.map(\.page) == [0, 1, 1])
        #expect(placed[1].y == 0 && placed[2].y == 14)
    }

    /// 묶음이 이어지면 사슬 끝까지 함께 (경기 방식 제목 → 그 줄 → 서명 칸): 제목만 앞 쪽에 남지 않는다
    @Test func keepChainMovesTogether() {
        let placed = PagePacker.pack([piece(60), piece(10, keep: true), piece(10, keep: true), piece(20)], contentHeight: 100)
        #expect(placed.map(\.page) == [0, 1, 1, 1])
        #expect(placed[1].y == 0)
    }

    /// 한 쪽보다 긴 사슬은 바로 다음 조각까지만 묶는다 (사슬 머리가 통째로 다음 쪽으로 밀리지 않는다)
    @Test func overlongChainFallsBackToNextPiece() {
        let placed = PagePacker.pack([piece(10), piece(40, keep: true), piece(40, keep: true), piece(40)], contentHeight: 100)
        #expect(placed.map(\.page) == [0, 0, 1, 1])
    }

    /// 내용이 쪽 높이와 같으면 빈 쪽이 생기지 않는다
    @Test func exactFitMakesNoExtraPage() {
        let placed = PagePacker.pack([piece(50, spacing: 0), piece(50, spacing: 0)], contentHeight: 100)
        #expect(Set(placed.map(\.page)) == [0])
        #expect(PagePacker.pageCount(placed) == 1)
    }

    /// 한 쪽보다 큰 조각은 새 쪽 맨 위에 혼자 둔다
    @Test func oversizedPieceGetsItsOwnPage() {
        let placed = PagePacker.pack([piece(10), piece(150), piece(10)], contentHeight: 100)
        #expect(placed.map(\.page) == [0, 1, 2])
        #expect(PagePacker.pageCount([]) == 1)
    }
}
