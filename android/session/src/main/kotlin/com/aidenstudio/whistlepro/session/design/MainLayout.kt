package com.aidenstudio.whistlepro.session.design

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Point(val x: Double, val y: Double)

data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val minX get() = x
    val maxX get() = x + width
    val minY get() = y
    val maxY get() = y + height
    val corners get() = listOf(Point(minX, minY), Point(maxX, minY), Point(minX, maxY), Point(maxX, maxY))
}

/**
 * 워치 메인 화면 배치 (스펙 §4.1·§4.2). 링, 링 안 다섯 줄, 버튼이 서로 겹치지 않는 칸을 받는다.
 * 각 줄의 폭은 그 줄 높이 범위에서 가장 좁은 링 안쪽 현(chord)이라 네 모서리가 모두 링 안에 든다.
 * 단위는 호출한 쪽의 단위(Apple pt, Wear dp)를 그대로 쓴다.
 */
data class MainLayout(
    val ringCenter: Point,
    /** 링 선 가운데까지의 반지름 */
    val ringRadius: Double,
    val lineWidth: Double,
    /** 위에서부터 상단 줄, 큰 시계, 추가시간, 점수, 상태 줄 */
    val rows: List<Rect>,
    val buttons: Rect,
    val clockFontSize: Double,
) {
    companion object {
        /** 링 안쪽 지름에 대한 각 줄 높이 비율 */
        internal val ROW_SHARES = listOf(0.12, 0.28, 0.11, 0.13, 0.12)

        /** 링이 비어 있는 아래쪽의 절반 각도 (290° 링 → 70° 열림) */
        internal const val OPEN_HALF_DEGREES = 35.0

        /**
         * Always-On(저전력)에서도 보이는 줄인지. 시계·추가시간·점수는 남기고 상단 줄·상태 줄은 숨긴다.
         * 추가시간 중에는 큰 시계가 규정 시간에서 멈춰 있으므로 +M:SS 줄이 있어야 한다.
         */
        fun showsRow(index: Int, reduced: Boolean): Boolean = !reduced || index in 1..3

        /** 사각 화면 (Swift MainLayout(size:)와 같은 계산). 버튼은 링 아래. */
        fun rect(width: Double, height: Double): MainLayout {
            val w = max(0.0, width)
            val h = max(0.0, height)
            val lineWidth = (w * 0.037).coerceIn(5.0, 7.5)
            val buttonHeight = (h * 0.135).coerceIn(24.0, 34.0)
            val top = h * 0.02
            val gap = h * 0.03
            val bottom = h * 0.035
            val diameter = max(0.0, min(w * 0.94, h - top - gap - buttonHeight - bottom))
            val center = Point(w / 2, top + diameter / 2)
            val ringRadius = max(0.0, diameter / 2 - lineWidth / 2)
            val inner = max(0.0, ringRadius - lineWidth / 2 - w * 0.02)
            val total = ROW_SHARES.sum() * inner * 2
            val rows = rowsInside(center, inner, center.y - total / 2, ROW_SHARES.map { it * inner * 2 })
            val inset = w * 0.14
            return MainLayout(
                center, ringRadius, lineWidth, rows,
                Rect(inset, top + diameter + gap, max(0.0, w - inset * 2), buttonHeight),
                rows[1].height * 0.95,
            )
        }

        /**
         * 원형 화면 (Wear OS). 링은 화면 가장자리를 따라 그리고, 버튼은 링이 비어 있는 아래쪽 틈에 둔다.
         * 버튼은 링 두 끝 사이의 폭 안, 화면 원 안에 들어가고, 다섯 줄은 링 안쪽에서 버튼 위에 놓인다.
         */
        fun round(diameter: Double): MainLayout {
            val d = max(0.0, diameter)
            val screenRadius = d / 2
            val center = Point(d / 2, d / 2)
            val lineWidth = (d * 0.037).coerceIn(5.0, 7.5)
            val margin = d * 0.02
            val ringRadius = max(0.0, screenRadius - margin - lineWidth / 2)
            val inner = max(0.0, ringRadius - lineWidth / 2 - d * 0.02)

            // 버튼: 링 끝(아래 55°·125°)의 안쪽 모서리보다 좁게, 화면 원 안에서 가장 아래
            val endAngle = Math.toRadians(90.0 - OPEN_HALF_DEGREES)
            val pad = d * 0.02
            val halfWidth = max(0.0, (ringRadius - lineWidth / 2) * cos(endAngle) - pad)
            val buttonHeight = (d * 0.13).coerceIn(22.0, 32.0)
            val safe = max(0.0, screenRadius - margin)
            val buttonBottom = center.y + sqrt(max(0.0, safe * safe - halfWidth * halfWidth))
            val buttons = Rect(center.x - halfWidth, buttonBottom - buttonHeight, halfWidth * 2, buttonHeight)

            // 줄: 링 안쪽, 버튼 위. 공간이 모자라면 비율을 유지한 채 줄인다.
            val gap = d * 0.02
            val natural = ROW_SHARES.sum() * inner * 2
            val ceiling = center.y - inner * 0.92
            val floor = min(center.y + natural / 2, buttons.minY - gap)
            val total = max(0.0, min(natural, floor - ceiling))
            val scale = if (natural > 0) total / natural else 0.0
            val rows = rowsInside(center, inner, floor - total, ROW_SHARES.map { it * inner * 2 * scale })
            return MainLayout(center, ringRadius, lineWidth, rows, buttons, rows[1].height * 0.95)
        }

        private fun rowsInside(center: Point, inner: Double, startY: Double, heights: List<Double>): List<Rect> {
            var y = startY
            return heights.map { rowHeight ->
                val far = max(abs(y - center.y), abs(y + rowHeight - center.y))
                val half = sqrt(max(0.0, inner * inner - far * far))
                Rect(center.x - half, y, half * 2, rowHeight).also { y += rowHeight }
            }
        }
    }
}
