package com.aidenstudio.whistlepro.session.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PDF 쪽 나누기 (백로그: 쪽 경계에서 글자 줄이 잘리고, A4 배수를 살짝 넘으면 거의 빈 쪽이 생겼다) */
class PagePackerTests {
    private fun piece(h: Double, spacing: Double = 4.0, keep: Boolean = false) = PagePiece(h, spacing, keep)

    @Test fun piecesStackOnOnePageWhenTheyFit() {
        val placed = PagePacker.pack(listOf(piece(20.0), piece(30.0), piece(10.0)), 100.0)
        assertEquals(listOf(PlacedPiece(0, 0, 0.0), PlacedPiece(1, 0, 24.0), PlacedPiece(2, 0, 58.0)), placed)
    }

    /** 줄은 쪽 경계에서 잘리지 않고 다음 쪽 맨 위로 간다 (맨 위에서는 앞 간격을 두지 않는다) */
    @Test fun pieceThatDoesNotFitMovesToNextPageTop() {
        val placed = PagePacker.pack(listOf(piece(60.0), piece(30.0), piece(30.0)), 100.0)
        assertEquals(listOf(0, 0, 1), placed.map { it.page })
        assertEquals(0.0, placed[2].y)
    }

    /** 제목은 첫 줄과 같은 쪽에 */
    @Test fun headingKeepsWithNextLine() {
        val placed = PagePacker.pack(listOf(piece(80.0), piece(10.0, keep = true), piece(20.0)), 100.0)
        assertEquals(listOf(0, 1, 1), placed.map { it.page })
        assertTrue(placed[1].y == 0.0 && placed[2].y == 14.0)
    }

    /** 묶음이 이어지면 사슬 끝까지 함께: 제목만 앞 쪽에 남지 않는다 */
    @Test fun keepChainMovesTogether() {
        val placed = PagePacker.pack(listOf(piece(60.0), piece(10.0, keep = true), piece(10.0, keep = true), piece(20.0)), 100.0)
        assertEquals(listOf(0, 1, 1, 1), placed.map { it.page })
        assertEquals(0.0, placed[1].y)
    }

    /** 한 쪽보다 긴 사슬은 바로 다음 조각까지만 묶는다 (사슬 머리가 통째로 다음 쪽으로 밀리지 않는다) */
    @Test fun overlongChainFallsBackToNextPiece() {
        val placed = PagePacker.pack(listOf(piece(10.0), piece(40.0, keep = true), piece(40.0, keep = true), piece(40.0)), 100.0)
        assertEquals(listOf(0, 0, 1, 1), placed.map { it.page })
    }

    /** 내용이 쪽 높이와 같으면 빈 쪽이 생기지 않는다 */
    @Test fun exactFitMakesNoExtraPage() {
        val placed = PagePacker.pack(listOf(piece(50.0, 0.0), piece(50.0, 0.0)), 100.0)
        assertEquals(setOf(0), placed.map { it.page }.toSet())
        assertEquals(1, PagePacker.pageCount(placed))
    }

    /** 한 쪽보다 큰 조각은 새 쪽 맨 위에 혼자 둔다 */
    @Test fun oversizedPieceGetsItsOwnPage() {
        val placed = PagePacker.pack(listOf(piece(10.0), piece(150.0), piece(10.0)), 100.0)
        assertEquals(listOf(0, 1, 2), placed.map { it.page })
        assertEquals(1, PagePacker.pageCount(emptyList()))
    }
}
