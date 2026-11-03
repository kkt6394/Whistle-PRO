package com.aidenstudio.whistlepro.session.design

/** PDF 한 쪽에 쌓을 조각 (보고서 제목·한 줄 등). 조각은 쪽 경계에서 자르지 않는다. */
data class PagePiece(
    val height: Double,
    /** 앞 조각과의 간격 (쪽 맨 위에서는 두지 않는다) */
    val spacingBefore: Double,
    /** 다음 조각과 같은 쪽에 둔다 (제목이 쪽 맨 아래에 혼자 남지 않게) */
    val keepWithNext: Boolean = false,
    /** 이 조각이 새 쪽 맨 위에 오면 먼저 다시 그릴 표 머리 조각의 인덱스 */
    val repeatsHeader: Int? = null,
)

data class PlacedPiece(
    val index: Int,
    val page: Int,
    /** 쪽 내용 영역 위에서부터 */
    val y: Double,
    /** 이 조각 바로 위에 다시 그린 표 머리 조각 인덱스 (그렸을 때만) */
    val header: Int? = null,
    /** 다시 그린 표 머리의 y (조각의 y는 머리 높이만큼 아래) */
    val headerY: Double? = null,
)

/**
 * 조각을 위에서부터 쌓다가 넘치면 다음 쪽 맨 위로 (백로그: 긴 그림 한 장을 A4 높이로 잘라 글자 줄이 잘렸다).
 * 한 쪽보다 큰 조각은 새 쪽에 혼자 둔다.
 */
object PagePacker {
    fun pack(pieces: List<PagePiece>, contentHeight: Double, laterPageTop: Double = 0.0): List<PlacedPiece> {
        val placed = mutableListOf<PlacedPiece>()
        var page = 0
        var bottom = 0.0
        var empty = true
        pieces.forEachIndexed { index, piece ->
            // keepWithNext가 이어지면 사슬 끝 조각까지 같은 쪽에 (굵은 줄 → 열 머리 → 첫 줄).
            // 사슬이 한 쪽보다 길면 바로 다음 조각까지만 본다
            var chain = piece.height
            var next = index
            while (pieces[next].keepWithNext && next + 1 < pieces.size) {
                next += 1
                chain += pieces[next].spacingBefore + pieces[next].height
            }
            if (chain > contentHeight && piece.keepWithNext && index + 1 < pieces.size) {
                chain = piece.height + pieces[index + 1].spacingBefore + pieces[index + 1].height
            }
            val need = (if (empty) 0.0 else piece.spacingBefore) + chain
            if (!empty && bottom + need > contentHeight) {
                page += 1
                bottom = laterPageTop
                empty = true
            }
            // 새 쪽 맨 위(첫 쪽 제외)에 표 줄이 오면 머리를 먼저 다시 그린다
            var header: Int? = null
            var headerY: Double? = null
            var top: Double
            if (empty) {
                top = if (page == 0) 0.0 else laterPageTop
                val h = piece.repeatsHeader
                if (page > 0 && h != null && h in pieces.indices) {
                    header = h
                    headerY = top
                    top += pieces[h].height
                }
            } else {
                top = bottom + piece.spacingBefore
            }
            placed += PlacedPiece(index, page, top, header, headerY)
            bottom = top + piece.height
            empty = false
        }
        return placed
    }

    fun pageCount(placed: List<PlacedPiece>): Int = (placed.maxOfOrNull { it.page } ?: 0) + 1
}
