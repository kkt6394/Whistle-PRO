package com.aidenstudio.whistlepro.session.design

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BorderTrackTests {
    // 가로 100, 세로 140, 모서리 20: 직선 위·아래 60, 좌우 100, 모서리 4개 = 2π·20
    private val track = BorderTrack(Rect(10.0, 5.0, 100.0, 140.0), cornerRadius = 20.0)

    private fun near(a: Point, b: Point, tolerance: Double = 0.01) = abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance

    @Test fun lengthIsStraightsPlusCorners() {
        assertTrue(abs(track.length - (2 * 60 + 2 * 100 + 2 * PI * 20)) < 0.001)
    }

    @Test fun startsAndEndsAtTopCenter() {
        assertTrue(near(track.point(0.0), Point(60.0, 5.0)))
        assertTrue(near(track.point(1.0), Point(60.0, 5.0)))
    }

    /** 화면 좌표(아래로 y 증가)에서 시계방향: 위쪽 가운데 → 오른쪽 → 아래 → 왼쪽 */
    @Test fun goesClockwise() {
        val early = track.point(0.01)
        assertTrue(early.x > 60 && abs(early.y - 5) < 0.001, "위쪽 변을 따라 오른쪽으로")
        // 위 가운데에서 30(위 직선 반) + 10π(모서리)만큼 가면 오른쪽 변 시작
        assertTrue(near(track.point((30 + 10 * PI) / track.length), Point(110.0, 25.0)))
        assertTrue(track.point(0.5).y > 140, "반 바퀴면 아래쪽 변")
        assertTrue(track.point(1 - 1.0 / 8).x < 60, "끝 무렵은 왼쪽 위")
    }

    @Test fun cornerPointLiesOnArc() {
        // 오른쪽 위 모서리 가운데: 위 직선 반(30) + 모서리 반(5π)
        val p = track.point((30 + 5 * PI) / track.length)
        val center = Point(90.0, 25.0)
        assertTrue(abs(hypot(p.x - center.x, p.y - center.y) - 20) < 0.001)
        assertTrue(p.x > center.x && p.y < center.y)
    }

    @Test fun fractionIsClamped() {
        assertTrue(near(track.point(-1.0), track.point(0.0)))
        assertTrue(near(track.point(2.0), track.point(1.0)))
    }

    /** Swift trimmedPathEndsAtPoint의 Kotlin판: 조각 목록의 끝점이 point(fraction)과 같아야 채움이 맞다 */
    @Test fun segmentsEndAtPoint() {
        for (track in listOf(track, BorderTrack.circle(200.0, 10.0))) {
            for (fraction in listOf(0.1, 0.25, 0.4, 0.5, 0.75, 0.9)) {
                val pieces = track.segments(0.0, fraction)
                assertTrue(near(pieces.first().start, track.point(0.0)), "$fraction 시작")
                assertTrue(near(pieces.last().end, track.point(fraction)), "$fraction 끝 ${pieces.last().end} vs ${track.point(fraction)}")
                pieces.zipWithNext { a, b -> assertTrue(near(a.end, b.start), "$fraction 이어짐") }
            }
        }
    }

    /** 가운데 구간만 잘라도 길이가 비율만큼, 시작·끝이 point와 같다 */
    @Test fun segmentsCoverTheRange() {
        val pieces = track.segments(0.2, 0.7)
        assertTrue(abs(pieces.sumOf { it.length } - track.length * 0.5) < 0.001)
        assertTrue(near(pieces.first().start, track.point(0.2)))
        assertTrue(near(pieces.last().end, track.point(0.7)))
        assertTrue(abs(track.segments(0.0, 1.0).sumOf { it.length } - track.length) < 0.001)
        assertEquals(emptyList(), track.segments(0.6, 0.6))
        assertEquals(emptyList(), track.segments(0.7, 0.2))
    }

    @Test fun radiusIsClampedToHalfShortSide() {
        assertEquals(20.0, BorderTrack(Rect(0.0, 0.0, 40.0, 100.0), cornerRadius = 50.0).topRadius)
        assertEquals(0.0, BorderTrack(Rect(0.0, 0.0, 0.0, 0.0), cornerRadius = 10.0).bottomRadius)
        assertEquals(0.0, BorderTrack(Rect(0.0, 0.0, 40.0, 40.0), cornerRadius = -3.0).topRadius)
    }

    @Test fun squareCornersStillWork() {
        val square = BorderTrack(Rect(0.0, 0.0, 100.0, 100.0), cornerRadius = 0.0)
        assertTrue(abs(square.length - 400) < 0.001)
        assertTrue(near(square.point(0.125), Point(100.0, 0.0)))
        assertTrue(near(square.point(0.375), Point(100.0, 100.0)))
    }

    /** 위·아래 모서리 반지름이 다를 수 있다 */
    @Test fun separateTopAndBottomRadii() {
        val t = BorderTrack(Rect(0.0, 0.0, 100.0, 140.0), topRadius = 10.0, bottomRadius = 30.0)
        assertTrue(t.topRadius == 10.0 && t.bottomRadius == 30.0)
        // 위 직선 80, 아래 직선 40, 좌우 직선 100, 모서리 둘씩 π·10/2·2 + π·30/2·2
        assertTrue(abs(t.length - (80 + 40 + 2 * 100 + PI * 10 + PI * 30)) < 0.001)
        // 위 가운데에서 위 직선 반(40) + 위 모서리(5π)를 가면 오른쪽 변 시작 (100, 10)
        assertTrue(near(t.point((40 + 5 * PI) / t.length), Point(100.0, 10.0)))
        assertTrue(near(t.segments(0.0, 0.6).last().end, t.point(0.6)))
    }

    @Test fun radiiFitHeightTogether() {
        val t = BorderTrack(Rect(0.0, 0.0, 200.0, 60.0), topRadius = 50.0, bottomRadius = 50.0)
        assertTrue(t.topRadius + t.bottomRadius <= 60 + 0.001)
    }

    /** 원형 화면: 위 가운데 → 오른쪽 → 아래 → 왼쪽 (지름 200, 안쪽 10 → 중심 100, 반지름 90) */
    @Test fun circleStartsAtTopAndGoesClockwise() {
        val circle = BorderTrack.circle(200.0, 10.0)
        assertTrue(near(circle.point(0.0), Point(100.0, 10.0)))
        assertTrue(near(circle.point(0.25), Point(190.0, 100.0)))
        assertTrue(near(circle.point(0.5), Point(100.0, 190.0)))
        assertTrue(near(circle.point(0.75), Point(10.0, 100.0)))
        // 1/8 바퀴는 오른쪽 위 45°
        val angle = -PI / 4
        assertTrue(near(circle.point(0.125), Point(100 + 90 * cos(angle), 100 + 90 * sin(angle))))
    }

    @Test fun circleLengthIsCircumference() {
        assertTrue(abs(BorderTrack.circle(200.0, 10.0).length - 2 * PI * 90) < 0.001)
        assertEquals(0.0, BorderTrack.circle(10.0, 20.0).length)
    }
}
