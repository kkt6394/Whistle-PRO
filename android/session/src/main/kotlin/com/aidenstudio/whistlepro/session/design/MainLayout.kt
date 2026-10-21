package com.aidenstudio.whistlepro.session.design

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
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
 * 워치 메인·휴식 화면 배치 (B1 스펙 §3)의 Wear OS 원형판. Swift `MainLayout(size:)`의 하한·배분·`take`·`shrink` 순서를
 * 그대로 쓰고, 둥근 사각형 모서리 대신 원의 현으로 줄 폭을 정한다.
 * - 테두리: 화면 원에서 `edge + lineWidth/2` 안쪽 원. 위 가운데에서 시작해 시계방향 ([BorderTrack.circle])
 * - 상단 띠: 테두리 안쪽 위 호를 따르는 곡선 글자. 띠 높이(headerBand)는 내용 원 위쪽에서 뺀다
 * - 테두리 안: 시계 줄 · 작은 줄(남은 시간·정지시간) · 팀 칸(홈 | 원정) · 버튼 줄(동그라미 둘). 각 줄의 폭은 그 줄 높이 범위에서
 *   가장 좁은 내용 원의 현이라 네 모서리가 모두 원 안에 든다
 * 하한(MIN_*)을 먼저 지키고, 남는 높이는 시계·점수가 버튼 셋 배치였다면 받았을 크기까지 먼저 받은 뒤
 * 버튼(동그라미 둘) → 시계·점수 나머지 → 일시 퇴장 줄 수 순으로 나눈다. 하한이 안 들어가면 작은 줄을 먼저 숨기고, 그래도 모자라면
 * 모두 같은 비율(shrink)로 줄인다. 단위는 dp.
 */
data class MainLayout(
    val lineWidth: Double,
    val border: BorderTrack,
    /** 내용 원: 테두리 선 안쪽에서 여백만큼 더 들어간 원 */
    val contentCenter: Point,
    val contentRadius: Double,
    /** 상단 곡선 띠의 높이 (내용 원 위쪽 끝부터) */
    val headerBand: Double,
    /** 상단 띠 글자가 쓸 수 있는 호의 각도 범위(위 가운데 기준 좌우 합). 띠 고리 안쪽 끝이 시계 줄 위 끝보다 위인 범위 */
    val headerSweepDegrees: Double,
    val headerFontSize: Double,
    val clock: Rect,
    /** 큰 시계 바로 아래 작은 줄: 남은 시간(또는 경과·45+2:13)과 정지시간. 숨기면 높이 0 */
    val subClock: Rect,
    val teams: Rect,
    val home: Rect,
    val away: Rect,
    val buttons: Rect,
    val clockFontSize: Double,
    val scoreFontSize: Double,
    val sinBinFontSize: Double,
    val subClockFontSize: Double,
    val scoreRowHeight: Double,
    val sinBinRowHeight: Double,
    val sinBinLines: Int,
    /** 하한이 안 들어가 모두 줄인 비율 (1이면 하한 그대로 이상) */
    val shrink: Double,
    /** 하한이 안 들어가 작은 줄을 숨겼다 */
    val subClockHidden: Boolean,
) {
    val buttonHeight: Double get() = buttons.height

    /** 메인 화면의 그리는 부분 (Always-On에서 무엇을 남길지) */
    enum class Row { BORDER, HEADER, CLOCK, ADDED_TIME, SUB_CLOCK, SCORE, SIN_BIN, SHORT_HANDED, BUTTONS }

    companion object {
        const val MIN_BUTTON_HEIGHT = 44.0
        const val MIN_CLOCK_FONT = 44.0
        const val MIN_SCORE_FONT = 30.0
        const val MIN_SIN_BIN_FONT = 13.0
        /** 두 팀 칸 사이 (가운데에 1dp 선) */
        const val DIVIDER_GAP = 6.0
        /** 버튼 둘 사이 간격 */
        const val BUTTON_SPACING = 6.0
        /** 상단 띠 기본 글자 크기 (WatchScale로 키운다) */
        const val HEADER_FONT = 13.0
        /** 상단 띠 높이 / 띠 글자 크기 */
        const val HEADER_BAND_LEADING = 1.25

        /** 화면 끝에서 테두리 선 바깥까지 */
        internal const val EDGE = 1.5
        /** 테두리 선 안쪽에서 내용까지 */
        internal const val PADDING = 2.5
        internal const val GAP = 2.0
        /** 시계 글자 / 줄 높이: 줄 높이는 숫자 높이(약 0.72em)에 여유를 둔 0.82em */
        internal const val CLOCK_FILL = 1 / 0.82
        /** 점수 글자 / 줄 높이 */
        internal const val SCORE_FILL = 1.0
        /** 일시 퇴장·작은 줄 높이 / 글자 */
        internal const val SIN_BIN_LEADING = 1.25
        /** "38:12" 폭 ≈ 2.8em, 점수 두 자리 + 점 ≈ 2em */
        internal const val CLOCK_EM = 2.9
        internal const val SCORE_EM = 2.0
        internal const val MAX_CLOCK_FONT = 56.0
        internal const val MAX_SCORE_FONT = 40.0
        internal const val MAX_SIN_BIN_LINES = 3
        internal const val MAX_BUTTON_HEIGHT = 56.0

        private val reducedRows = setOf(Row.BORDER, Row.CLOCK, Row.ADDED_TIME, Row.SCORE)

        /**
         * Always-On(저전력)에서도 보이는 부분인지. 테두리·시계·추가시간·점수만 남긴다 (B1 §3 Always-On).
         * 저조도 화면은 갱신이 느려 일시 퇴장 초 카운트가 틀리게 보일 수 있다.
         */
        fun showsRow(row: Row, reduced: Boolean): Boolean = !reduced || row in reducedRows

        /** 지름 diameter인 원형 화면의 상단 띠 높이: 띠 글자(`HEADER_FONT` × WatchScale) × [HEADER_BAND_LEADING]. [round]에 넘긴다 */
        fun headerBand(diameter: Double): Double = WatchScale(max(0.0, diameter)).scaled(HEADER_FONT) * HEADER_BAND_LEADING

        /** 원형 화면 (Wear OS). diameter는 화면 지름, headerBand는 상단 곡선 띠 높이(글자 크기 × 1.25). */
        fun round(diameter: Double, headerBand: Double): MainLayout = layout(diameter, headerBand, buttonCount = 2)

        /**
         * 휴식·연장 대기 화면: 버튼이 [시작] 알약 하나라 동그라미 둘 폭([round])보다 넓어야 한다 → 버튼 셋 때의 배치를 그대로 쓴다
         * (알약 폭 = 그 줄 폭).
         */
        fun interval(diameter: Double, headerBand: Double): MainLayout = layout(diameter, headerBand, buttonCount = 3)

        /** buttonCount 3은 예전 배치(시계 → 점수 → 줄 수 → 버튼). 둘일 때 시계·점수의 하한을 구하는 데도 쓴다 */
        private fun layout(diameter: Double, headerBand: Double, buttonCount: Int): MainLayout {
            val reference = if (buttonCount == 2) layout(diameter, headerBand, 3) else null
            val d = max(0.0, diameter)
            val center = Point(d / 2, d / 2)
            val lineWidth = (d * 0.03).coerceIn(4.0, 6.5)
            val border = BorderTrack.circle(d, EDGE + lineWidth / 2)
            val radius = max(0.0, border.rect.width / 2 - lineWidth / 2 - PADDING)
            val band = headerBand.coerceIn(0.0, radius)
            val contentTop = center.y - radius + band

            fun halfChord(depth: Double) = sqrt(max(0.0, radius * radius - depth * depth))

            /** 내용 원 안에서 minY부터 height만큼의 줄. 폭은 그 범위에서 가장 좁은 현 */
            fun row(minY: Double, height: Double): Rect {
                val h = max(0.0, height)
                val half = halfChord(max(abs(minY - center.y), abs(minY + h - center.y)))
                return Rect(center.x - half, minY, half * 2, h)
            }

            /** 시계 줄 위 끝: 글자 크기 font의 "38:12"가 들어가는 현까지 내린다 (Swift clockDrop의 원형판) */
            fun clockTop(font: Double): Double {
                val half = font * CLOCK_EM / 2
                val top = if (half >= radius) center.y else center.y - halfChord(half)
                return max(contentTop, top)
            }

            fun buttonRowWidth(h: Double) = min(radius * 2, h * buttonCount + BUTTON_SPACING * (buttonCount - 1))

            /** 지름 h인 동그라미 버튼들이 들어가는 가장 아래 끝 (Swift buttonLift의 원형판) */
            fun buttonsBottom(h: Double) = center.y + halfChord(buttonRowWidth(h) / 2)

            fun available(scale: Double) =
                buttonsBottom(MIN_BUTTON_HEIGHT * scale) - clockTop(MIN_CLOCK_FONT * scale) - GAP * 2

            // 하한부터 채우고 남는 높이를 나눈다
            val clockRowMin = MIN_CLOCK_FONT / CLOCK_FILL
            val scoreRowMin = MIN_SCORE_FONT / SCORE_FILL
            val sinBinRowMin = MIN_SIN_BIN_FONT * SIN_BIN_LEADING
            val subClockRowMin = MIN_SIN_BIN_FONT * SIN_BIN_LEADING
            val withoutSubClock = MIN_BUTTON_HEIGHT + clockRowMin + scoreRowMin + sinBinRowMin
            // 하한이 안 들어가면 ① 작은 줄을 숨기고 ② 그래도 모자라면 모두 같은 비율로 줄인다.
            // 줄이면 버튼 줄·시계 줄이 좁아져 원의 더 넓은 곳까지 내려갈 수 있으므로 비율은 이분법으로 찾는다
            // 버튼 둘일 때도 숨김 여부는 버튼 셋 배치와 같게 둔다 (작은 줄이 보이는 화면이 늘어 시계 자리가 줄지 않게)
            val subClockHidden = reference?.subClockHidden ?: (available(1.0) < withoutSubClock + subClockRowMin)
            val minimum = if (subClockHidden) withoutSubClock else withoutSubClock + subClockRowMin
            val shrink = if (available(1.0) >= minimum) 1.0 else largest(1.0) { available(it) >= it * minimum }
            val top = clockTop(MIN_CLOCK_FONT * shrink)
            val baseBottom = buttonsBottom(MIN_BUTTON_HEIGHT * shrink)
            var spare = max(0.0, available(shrink) - minimum * shrink)
            fun take(want: Double): Double {
                val given = min(max(0.0, want), spare)
                spare -= given
                return given
            }

            /** take(want)만큼 받은 높이 안에서 cost(x) ≤ 받은 높이인 가장 큰 x (0..limit). 쓰고 남은 높이는 돌려준다 */
            fun grow(want: Double, limit: Double, cost: (Double) -> Double): Double {
                val given = take(want)
                val x = largest(max(0.0, limit)) { cost(it) <= given + 1e-9 }
                spare = max(0.0, spare + given - cost(x))
                return x
            }

            val cellWidth = max(0.0, (radius * 2 - DIVIDER_GAP) / 2)
            val clockCap = max(MIN_CLOCK_FONT, min(MAX_CLOCK_FONT, radius * 2 / CLOCK_EM))
            val scoreCap = max(MIN_SCORE_FONT, min(MAX_SCORE_FONT, cellWidth / SCORE_EM))
            // 시계·점수가 버튼보다 먼저 받는 크기: 둘일 때는 버튼 셋 배치의 값, 셋일 때는 상한까지
            val clockFirst = if (reference == null) clockCap else min(clockCap, max(MIN_CLOCK_FONT, reference.clockFontSize / shrink))
            val scoreFirst = if (reference == null) scoreCap else min(scoreCap, max(MIN_SCORE_FONT, reference.scoreFontSize / shrink))
            // 원에서는 시계가 커지면 줄도 그 폭의 현까지 내려가야 한다. 내려가는 높이도 시계가 받은 몫에서 쓴다
            val clockWant = if (reference == null) (clockFirst - MIN_CLOCK_FONT) / CLOCK_FILL else spare
            val clockGrown = grow(clockWant, clockFirst - MIN_CLOCK_FONT) { x ->
                clockTop((MIN_CLOCK_FONT + x) * shrink) - top + x * shrink / CLOCK_FILL
            }
            val scoreGrown = take((scoreFirst - MIN_SCORE_FONT) / SCORE_FILL) * SCORE_FILL
            // 버튼이 커지면 버튼 줄이 넓어져 위로 올라가야 한다. 올라가는 높이도 버튼이 받은 몫에서 쓴다
            fun growButtons() = (
                MIN_BUTTON_HEIGHT + grow(spare, MAX_BUTTON_HEIGHT - MIN_BUTTON_HEIGHT) { x ->
                    x * shrink + baseBottom - buttonsBottom((MIN_BUTTON_HEIGHT + x) * shrink)
                }
                ) * shrink
            var buttonHeight = if (reference != null) growButtons() else 0.0
            val clockBase = MIN_CLOCK_FONT + clockGrown
            val clockMore = grow(spare, clockCap - clockBase) { y ->
                clockTop((clockBase + y) * shrink) - clockTop(clockBase * shrink) + y * shrink / CLOCK_FILL
            }
            var clockFont = (clockBase + clockMore) * shrink
            var scoreFont = (MIN_SCORE_FONT + scoreGrown + take((scoreCap - MIN_SCORE_FONT - scoreGrown) / SCORE_FILL) * SCORE_FILL) * shrink
            var lines = 1
            while (lines < MAX_SIN_BIN_LINES && spare >= sinBinRowMin) {
                spare -= sinBinRowMin
                lines += 1
            }
            if (reference == null) buttonHeight = growButtons()
            val sinBinFont = MIN_SIN_BIN_FONT * shrink
            val subClockFont = MIN_SIN_BIN_FONT * shrink

            val clockRowHeight = max(clockFont, MIN_CLOCK_FONT * shrink) / CLOCK_FILL
            val clock = row(clockTop(clockFont), clockRowHeight)
            // 줄 폭에 다섯 글자가 줄지 않고 들어가게 한다
            clockFont = min(clockFont, clock.width / CLOCK_EM)
            val rowWidth = buttonRowWidth(buttonHeight)
            val buttons = Rect(center.x - rowWidth / 2, buttonsBottom(buttonHeight) - buttonHeight, rowWidth, buttonHeight)
            val subClock = row(clock.maxY, if (subClockHidden) 0.0 else subClockFont * SIN_BIN_LEADING)
            // 팀 칸은 작은 줄과 버튼 줄 사이를 모두 쓴다 (남은 높이는 칸이 받는다)
            val teamsTop = subClock.maxY + GAP
            val teams = row(teamsTop, buttons.minY - GAP - teamsTop)
            val half = max(0.0, (teams.width - DIVIDER_GAP) / 2)
            val home = Rect(teams.minX, teams.minY, half, teams.height)
            val away = Rect(teams.maxX - half, teams.minY, half, teams.height)
            // 점수 상한은 내용 원 지름으로 잡았다. 실제 칸(현)이 더 좁으면 두 자리가 들어가게 줄인다
            scoreFont = min(scoreFont, half / SCORE_EM)
            // 띠 글자는 고리 [r − band, r]에 놓인다. 고리 안쪽 끝이 시계 줄 위 끝보다 위에 있는 각도까지만 쓴다
            val inner = radius - band
            val sweep = if (inner <= 0) 0.0 else 2 * acos(((center.y - clock.minY) / inner).coerceIn(-1.0, 1.0)) * 180 / PI

            return MainLayout(
                lineWidth = lineWidth,
                border = border,
                contentCenter = center,
                contentRadius = radius,
                headerBand = band,
                headerSweepDegrees = sweep,
                headerFontSize = WatchScale(d).scaled(HEADER_FONT),
                clock = clock,
                subClock = subClock,
                teams = teams,
                home = home,
                away = away,
                buttons = buttons,
                clockFontSize = clockFont,
                scoreFontSize = scoreFont,
                sinBinFontSize = sinBinFont,
                subClockFontSize = subClockFont,
                scoreRowHeight = scoreFont / SCORE_FILL,
                sinBinRowHeight = sinBinFont * SIN_BIN_LEADING,
                sinBinLines = lines,
                shrink = shrink,
                subClockHidden = subClockHidden,
            )
        }

        /** 0..limit에서 ok(x)인 가장 큰 x. ok는 x가 커질수록 참에서 거짓으로만 바뀐다. 0도 거짓이면 0 */
        private fun largest(limit: Double, ok: (Double) -> Boolean): Double {
            if (ok(limit)) return limit
            var lo = 0.0
            var hi = limit
            repeat(60) {
                val mid = (lo + hi) / 2
                if (ok(mid)) lo = mid else hi = mid
            }
            return lo
        }
    }
}
