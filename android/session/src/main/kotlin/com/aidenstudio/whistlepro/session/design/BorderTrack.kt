package com.aidenstudio.whistlepro.session.design

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 화면 모양을 따르는 진행 테두리의 경로 (B1 스펙 §2). 위쪽 가운데에서 시작해 시계방향으로 한 바퀴 돈다.
 * 위치는 경로 길이 비율(0..1)로 쓴다. 좌표는 화면 좌표(아래로 y 증가)다.
 * Swift의 SwiftUI `Path`·`BorderProgress` 대신 [segments]가 직선·호 조각을 돌려주고, Compose `Path`는 `:wear`에서 만든다.
 */
class BorderTrack(
    /** 선 가운데가 지나는 사각형 */
    val rect: Rect,
    topRadius: Double,
    bottomRadius: Double,
) {
    /** 위쪽 두 모서리 반지름 */
    val topRadius: Double

    /** 아래쪽 두 모서리 반지름 */
    val bottomRadius: Double

    init {
        val limit = max(0.0, min(rect.width, rect.height) / 2)
        var top = min(max(0.0, topRadius), limit)
        var bottom = min(max(0.0, bottomRadius), limit)
        // 위·아래 모서리가 세로로 겹치지 않게
        if (top + bottom > rect.height && top + bottom > 0) {
            val scale = rect.height / (top + bottom)
            top *= scale
            bottom *= scale
        }
        this.topRadius = top
        this.bottomRadius = bottom
    }

    constructor(rect: Rect, cornerRadius: Double) : this(rect, cornerRadius, cornerRadius)

    private val topStraight get() = max(0.0, rect.width - 2 * topRadius)
    private val bottomStraight get() = max(0.0, rect.width - 2 * bottomRadius)
    private val sideStraight get() = max(0.0, rect.height - topRadius - bottomRadius)
    private val topArc get() = PI / 2 * topRadius
    private val bottomArc get() = PI / 2 * bottomRadius

    val length: Double get() = topStraight + bottomStraight + 2 * sideStraight + 2 * topArc + 2 * bottomArc

    /** 한 바퀴를 이루는 아홉 구간: 위 직선 반, 오른쪽 위 모서리, 오른쪽 변, … , 왼쪽 위 모서리, 위 직선 반 */
    private val spans: List<Span>
        get() {
            val t = topRadius
            val b = bottomRadius
            val midX = rect.minX + rect.width / 2
            return listOf(
                Span.Straight(topStraight / 2, Point(midX, rect.minY), 1.0, 0.0),
                Span.Corner(Point(rect.maxX - t, rect.minY + t), t, -90.0),
                Span.Straight(sideStraight, Point(rect.maxX, rect.minY + t), 0.0, 1.0),
                Span.Corner(Point(rect.maxX - b, rect.maxY - b), b, 0.0),
                Span.Straight(bottomStraight, Point(rect.maxX - b, rect.maxY), -1.0, 0.0),
                Span.Corner(Point(rect.minX + b, rect.maxY - b), b, 90.0),
                Span.Straight(sideStraight, Point(rect.minX, rect.maxY - b), 0.0, -1.0),
                Span.Corner(Point(rect.minX + t, rect.minY + t), t, 180.0),
                Span.Straight(topStraight / 2, Point(rect.minX + t, rect.minY), 1.0, 0.0),
            )
        }

    /** 경로 위 위치(0..1)의 점. 0과 1은 위쪽 가운데. */
    fun point(fraction: Double): Point {
        var d = fraction.coerceIn(0.0, 1.0) * length
        for (span in spans) {
            if (d <= span.length) return span.at(d)
            d -= span.length
        }
        return Point(rect.minX + rect.width / 2, rect.minY)
    }

    /** 경로 위 from..to(0..1, 시계방향) 구간을 그릴 직선·호 조각. to ≤ from이면 비어 있다. */
    fun segments(from: Double, to: Double): List<BorderSegment> {
        val total = length
        val start = from.coerceIn(0.0, 1.0) * total
        val end = to.coerceIn(0.0, 1.0) * total
        if (end <= start) return emptyList()
        var cursor = 0.0
        return spans.mapNotNull { span ->
            val lo = max(start, cursor)
            val hi = min(end, cursor + span.length)
            val piece = if (hi > lo) span.piece(lo - cursor, hi - cursor) else null
            cursor += span.length
            piece
        }
    }

    override fun equals(other: Any?): Boolean =
        other is BorderTrack && rect == other.rect && topRadius == other.topRadius && bottomRadius == other.bottomRadius

    override fun hashCode(): Int = (rect.hashCode() * 31 + topRadius.hashCode()) * 31 + bottomRadius.hashCode()

    override fun toString(): String = "BorderTrack(rect=$rect, topRadius=$topRadius, bottomRadius=$bottomRadius)"

    private sealed interface Span {
        val length: Double
        fun at(travelled: Double): Point
        fun piece(from: Double, to: Double): BorderSegment

        /** start에서 (dx, dy) 방향으로 가는 직선 */
        class Straight(override val length: Double, val start: Point, val dx: Double, val dy: Double) : Span {
            override fun at(travelled: Double) = Point(start.x + dx * travelled, start.y + dy * travelled)
            override fun piece(from: Double, to: Double) = BorderSegment.Line(at(from), at(to))
        }

        /** 모서리 원 위 시작 각도(도, 0° = 오른쪽, 화면 좌표라 각도가 커지면 시계방향)부터 도는 사분원 */
        class Corner(val center: Point, val radius: Double, val fromDegrees: Double) : Span {
            override val length get() = PI / 2 * radius
            override fun at(travelled: Double): Point {
                if (radius <= 0) return center
                val angle = fromDegrees * PI / 180 + travelled / radius
                return Point(center.x + radius * cos(angle), center.y + radius * sin(angle))
            }
            override fun piece(from: Double, to: Double) = BorderSegment.Arc(
                center, radius, fromDegrees + from / radius * 180 / PI, (to - from) / radius * 180 / PI,
            )
        }
    }

    companion object {
        /** 원형 화면: 화면 원에서 inset만큼 안쪽 원 (정사각형 + 반지름 = 변의 반) */
        fun circle(diameter: Double, inset: Double): BorderTrack {
            val side = max(0.0, diameter - 2 * inset)
            return BorderTrack(Rect(inset, inset, side, side), side / 2)
        }
    }
}

/** 테두리 한 구간의 조각. Compose `Path.lineTo`·`arcTo`로 그대로 옮긴다. */
sealed interface BorderSegment {
    val start: Point
    val end: Point
    val length: Double

    data class Line(override val start: Point, override val end: Point) : BorderSegment {
        override val length get() = kotlin.math.hypot(end.x - start.x, end.y - start.y)
    }

    /** 각도는 도, 0° = 오른쪽(3시), 시계방향으로 증가 (Compose `arcTo`와 같은 기준) */
    data class Arc(val center: Point, val radius: Double, val startDegrees: Double, val sweepDegrees: Double) : BorderSegment {
        override val start get() = pointAt(startDegrees)
        override val end get() = pointAt(startDegrees + sweepDegrees)
        override val length get() = radius * sweepDegrees * PI / 180

        private fun pointAt(degrees: Double): Point {
            val angle = degrees * PI / 180
            return Point(center.x + radius * cos(angle), center.y + radius * sin(angle))
        }
    }
}
