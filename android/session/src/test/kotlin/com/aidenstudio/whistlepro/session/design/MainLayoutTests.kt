package com.aidenstudio.whistlepro.session.design

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wear OS 원형 메인 배치 (B1 §3을 원에 적용). 지름(dp): 192 가장 작은 원형, 216 갤럭시 40mm, 227·240 갤럭시 44mm급 */
class MainLayoutTests {
    private val diameters = listOf(192.0, 216.0, 227.0, 240.0)
    private val bands = listOf(0.0, 16.0)

    private fun MainLayout.inside(p: Point) =
        hypot(p.x - contentCenter.x, p.y - contentCenter.y) <= contentRadius + 0.001

    @Test fun roundMainFitsInsideCircle() {
        for (d in diameters) for (band in bands) checkLayout(d, band)
    }

    /** 휴식 화면의 [시작] 알약은 동그라미 둘 폭보다 넓고, 네 모서리 모두 내용 원 안 */
    @Test fun intervalPillIsWiderThanTwoCircles() {
        for (d in diameters) for (band in bands) {
            val l = MainLayout.interval(d, band)
            val name = "지름 $d 띠 $band"
            assertTrue(l.buttons.width > MainLayout.round(d, band).buttons.width, name)
            assertTrue(l.buttons.corners.all { l.inside(it) }, name)
        }
    }

    private fun checkLayout(d: Double, band: Double) {
        run {
            val l = MainLayout.round(d, band)
            val name = "지름 $d 띠 $band"
            // 테두리 선 바깥 가장자리가 화면 원 안
            assertTrue(l.border.rect.width / 2 + l.lineWidth / 2 <= d / 2, name)
            assertTrue(l.contentRadius < l.border.rect.width / 2 - l.lineWidth / 2, name)
            val boxes = mapOf("clock" to l.clock, "subClock" to l.subClock, "home" to l.home, "away" to l.away, "buttons" to l.buttons)
            for ((box, rect) in boxes) for (c in rect.corners) assertTrue(l.inside(c), "$name $box $rect")
            // 상단 띠 아래에서 시작하고, 위에서 아래로 겹치지 않는다
            assertTrue(l.clock.minY >= l.contentCenter.y - l.contentRadius + band - 0.001, name)
            assertTrue(l.clock.maxY <= l.subClock.minY + 0.001, name)
            assertTrue(l.subClock.maxY <= l.teams.minY + 0.001, name)
            assertTrue(l.teams.maxY <= l.buttons.minY + 0.001, name)
            assertTrue(l.home.maxX + MainLayout.DIVIDER_GAP <= l.away.minX + 0.001, name)
            assertTrue(l.home.minX >= l.teams.minX - 0.001 && l.away.maxX <= l.teams.maxX + 0.001, name)
            // 팀 칸에 점수 줄과 일시 퇴장 줄이 다 들어간다
            assertTrue(l.scoreRowHeight + l.sinBinLines * l.sinBinRowHeight <= l.teams.height + 0.001, name)
            for (rect in listOf(l.clock, l.home, l.away, l.buttons)) assertTrue(rect.width > 0 && rect.height > 0, "$name $rect")
            // 시계 "38:12"(약 2.9em)가 줄어들지 않고 들어간다
            assertTrue(l.clockFontSize * 2.9 <= l.clock.width + 0.001, "$name ${l.clockFontSize} ${l.clock.width}")
        }
    }

    /**
     * 상단 띠(고리 [r − band, r], 위 가운데 ± sweep/2)는 시계 줄 위 끝보다 위에 있다.
     * 실제 띠(240dp: 13 × 1.25 글자 × 1.25 = 20.3)와 시계 줄을 밀어 내리는 큰 띠(40)로 본다
     */
    @Test fun headerBandStaysAboveClock() {
        for ((d, band) in listOf(240.0 to 20.3125, 240.0 to 40.0, 192.0 to 16.25, 216.0 to 40.0)) {
            val l = MainLayout.round(d, band)
            val c = l.contentCenter
            val half = l.headerSweepDegrees / 2 * PI / 180
            for (i in 0..40) for (j in 0..10) {
                val angle = -half + 2 * half * i / 40
                val r = l.contentRadius - band + band * j / 10
                val y = c.y - r * cos(angle)
                assertTrue(y <= l.clock.minY + 0.001, "지름 $d 띠 $band 각도 ${angle * 180 / PI} 반지름 $r: $y > ${l.clock.minY}")
            }
        }
        // 실제 띠에서는 글자 자리가 남는다 (240: 약 42°)
        assertTrue(MainLayout.round(240.0, 20.3125).headerSweepDegrees > 30)
    }

    /** 띠가 크면 시계 줄은 현이 아니라 띠 아래 끝에서 시작한다 (clockTop의 띠 제한) */
    @Test fun largeBandPushesClockDown() {
        val l = MainLayout.round(240.0, 40.0)
        val contentTop = l.contentCenter.y - l.contentRadius + 40
        assertTrue(kotlin.math.abs(l.clock.minY - contentTop) < 0.001, "${l.clock.minY} $contentTop")
        assertTrue(l.clock.minY > MainLayout.round(240.0, 0.0).clock.minY)
        assertTrue(l.clockFontSize * 2.9 <= l.clock.width + 0.001)
        for (rect in listOf(l.clock, l.subClock, l.home, l.away, l.buttons)) for (p in rect.corners) assertTrue(l.inside(p), "$rect")
        assertTrue(l.teams.maxY <= l.buttons.minY + 0.001)
    }

    /** 갤럭시 44mm급: 작은 줄을 보이면서 B1 하한을 모두 지킨다 */
    @Test fun roundMainMeetsMinimumsOnGalaxy44() {
        for (d in listOf(227.0, 240.0)) for (band in bands) {
            val l = MainLayout.round(d, band)
            val name = "지름 $d 띠 $band"
            assertTrue(l.buttonHeight >= 44, "$name ${l.buttonHeight}")
            assertTrue(l.clockFontSize >= 44, "$name ${l.clockFontSize}")
            assertTrue(l.scoreFontSize >= 30, "$name ${l.scoreFontSize}")
            assertTrue(l.sinBinFontSize >= 13, "$name ${l.sinBinFontSize}")
            assertTrue(l.subClockFontSize >= 17, "$name ${l.subClockFontSize}")
            assertTrue(l.subClock.height >= l.subClockFontSize, "$name ${l.subClock}")
            assertEquals(1.0, l.shrink, name)
            assertTrue(l.sinBinLines >= 1)
        }
    }

    /** 갤럭시 40mm: 작은 줄을 보이면서 버튼·시계는 (거의) 하한 그대로 */
    @Test fun galaxy40KeepsButtonsAndClock() {
        for (band in bands) {
            val l = MainLayout.round(216.0, band)
            assertTrue(l.shrink >= 0.95, "${l.shrink}")
            assertTrue(l.buttonHeight >= 44 * l.shrink - 0.001, "${l.buttonHeight}")
            assertTrue(l.clockFontSize >= 44 * l.shrink - 0.001, "${l.clockFontSize}")
            assertTrue(l.scoreFontSize >= 30 * l.shrink - 0.001, "${l.scoreFontSize}")
        }
    }

    /** 둥근 버튼 둘(폭 ≥ 지름 2개 + 간격 6)이 버튼 줄에 들어간다 */
    @Test fun roundButtonsTwoFitChord() {
        for (d in diameters) for (band in bands) {
            val l = MainLayout.round(d, band)
            assertTrue(l.buttons.width >= 2 * l.buttonHeight + MainLayout.BUTTON_SPACING - 0.001, "$d ${l.buttons}")
            for (c in l.buttons.corners) assertTrue(l.inside(c), "$d $c")
        }
    }

    /** 버튼이 둘로 줄어 남는 자리는 동그라미를 키우되, 시계·점수는 10-21 값 아래로 내려가지 않는다 */
    @Test fun twoButtonsGrowWhileClockKeeps() {
        // 지름 to (시계, 점수, 버튼) — 버튼 셋 때의 값 (abdae68 측정). 216은 작은 줄을 늘 보이게 한 뒤(2026-10-23) 시계가 하한 44.
        // 227은 작은 줄을 17로 키우면서(2026-10-26) 시계가 47.87 → 45.47 (원에서 시계가 커지면 줄이 내려가 비싸다). 240은 그대로
        val before = mapOf(216.0 to Triple(44.0, 30.0, 44.0), 227.0 to Triple(45.47, 30.0, 44.0), 240.0 to Triple(49.03, 40.0, 46.15))
        for ((d, v) in before) {
            val l = MainLayout.round(d, MainLayout.headerBand(d))
            assertTrue(l.buttonHeight >= v.third + 2.0, "$d 버튼 ${l.buttonHeight}")
            assertTrue(l.clockFontSize >= v.first - 0.01, "$d 시계 ${l.clockFontSize}")
            assertTrue(l.scoreFontSize >= v.second - 0.01, "$d 점수 ${l.scoreFontSize}")
        }
        val small = MainLayout.round(192.0, MainLayout.headerBand(192.0))
        // 작은 줄 17(2026-10-26)로 0.9109 → 0.9108: 사실상 같다
        assertTrue(small.shrink >= 0.9108, "shrink ${small.shrink}")
        assertTrue(small.buttonHeight >= 40.07, "버튼 ${small.buttonHeight}")
    }

    /** 큰 글자로 띠가 두 배가 돼도 기본 배치와 같은 겹침·원 안 검사를 통과한다 */
    @Test fun twoButtonsFitWithLargeBand() {
        for (d in diameters) checkLayout(d, MainLayout.headerBand(d) * 2)
    }

    /**
     * 작은 줄은 어느 크기·큰 띠에서도 숨기지 않는다 (2026-10-23 사용자 결정: 모든 시계가 같은 구성).
     * 글자는 일시 퇴장 줄(13)보다 큰 17 — "남은 시간이 작고 회색이라 안 보인다"는 테스터 피드백(2026-10-26)
     */
    @Test fun subClockAlwaysShown() {
        for (d in diameters) for (band in listOf(MainLayout.headerBand(d), MainLayout.headerBand(d) * 2)) {
            val l = MainLayout.round(d, band)
            assertTrue(l.subClock.height >= l.subClockFontSize, "지름 $d 띠 $band ${l.subClock}")
            assertEquals(MainLayout.SUB_CLOCK_FONT * l.shrink, l.subClockFontSize, 1e-9)
            assertTrue(l.subClockFontSize > l.sinBinFontSize, "작은 줄이 일시 퇴장 줄보다 크다")
        }
    }

    /** 큰 원형: 시계·점수는 버튼 셋 때 값 이상, 버튼도 그 이상 (abdae68 측정) */
    @Test fun largeRoundKeepsClockAndScore() {
        // 지름 to (시계, 점수, 버튼)
        for ((d, v) in mapOf(260.0 to Triple(51.24, 40.0, 47.63), 280.0 to Triple(53.46, 40.0, 47.87))) {
            val l = MainLayout.round(d, MainLayout.headerBand(d))
            assertTrue(l.clockFontSize >= v.first - 0.01, "$d 시계 ${l.clockFontSize}")
            assertTrue(l.scoreFontSize >= v.second - 0.01, "$d 점수 ${l.scoreFontSize}")
            assertTrue(l.buttonHeight >= v.third, "$d 버튼 ${l.buttonHeight}")
            checkLayout(d, MainLayout.headerBand(d))
        }
    }

    /** 줄이는 경로(shrink < 1)도 둘 배치에서 하한 비율과 원 안을 지킨다 */
    @Test fun shrinkPathStillFits() {
        for ((d, band) in listOf(192.0 to MainLayout.headerBand(192.0) * 2, 180.0 to MainLayout.headerBand(180.0))) {
            val l = MainLayout.round(d, band)
            assertTrue(l.shrink < 1, "$d ${l.shrink}")
            assertTrue(l.buttonHeight >= 44 * l.shrink - 0.001, "$d ${l.buttonHeight}")
            assertTrue(l.clockFontSize >= 44 * l.shrink - 0.001, "$d ${l.clockFontSize}")
            assertTrue(l.scoreFontSize >= 30 * l.shrink - 0.001, "$d ${l.scoreFontSize}")
            checkLayout(d, band)
        }
    }

    /** 가장 작은 원형: 작은 줄을 숨기지 않고 모두 같은 비율로 줄인다 */
    @Test fun smallestRoundShrinksWithSubClock() {
        val l = MainLayout.round(192.0, 16.0)
        assertTrue(l.subClock.height > 0)
        assertTrue(l.shrink < 1)
        assertTrue(l.buttonHeight >= 44 * l.shrink - 0.001, "${l.buttonHeight} ${l.shrink}")
        assertTrue(l.clockFontSize >= 44 * l.shrink - 0.001, "${l.clockFontSize} ${l.shrink}")
        assertTrue(l.scoreFontSize >= 30 * l.shrink - 0.001, "${l.scoreFontSize} ${l.shrink}")
    }

    /** 큰 화면에서는 시계·점수·띠 글자가 커진다 */
    @Test fun largestWatchGrowsEverything() {
        val large = MainLayout.round(240.0, 16.0)
        val small = MainLayout.round(216.0, 16.0)
        assertTrue(large.clockFontSize > small.clockFontSize, "${large.clockFontSize} ${small.clockFontSize}")
        assertTrue(large.scoreFontSize > small.scoreFontSize, "${large.scoreFontSize} ${small.scoreFontSize}")
        assertTrue(large.buttonHeight > 44 && large.clockFontSize > 44 && large.scoreFontSize > 30)
        assertTrue(large.sinBinLines >= 1)
        assertTrue(large.headerFontSize > small.headerFontSize)
    }

    @Test fun zeroSizeDoesNotCrash() {
        val l = MainLayout.round(0.0, 16.0)
        for (rect in listOf(l.clock, l.subClock, l.teams, l.home, l.away, l.buttons)) {
            assertTrue(rect.width >= 0 && rect.height >= 0, "$rect")
        }
        assertTrue(l.sinBinLines >= 1)
        assertTrue(l.contentRadius >= 0)
    }

    /** Always-On: 테두리·시계·추가시간·점수만 남긴다 (B1 §3 Always-On) */
    @Test fun alwaysOnKeepsClockAddedTimeAndScore() {
        val all = MainLayout.Row.entries
        assertEquals(
            listOf(MainLayout.Row.BORDER, MainLayout.Row.CLOCK, MainLayout.Row.ADDED_TIME, MainLayout.Row.SCORE),
            all.filter { MainLayout.showsRow(it, reduced = true) },
        )
        assertEquals(all, all.filter { MainLayout.showsRow(it, reduced = false) })
    }

    /** 상단 띠 높이 = 띠 글자(13 × WatchScale) × 1.25. 테스트가 쓰던 값(192: 16.25, 240: 20.3125)과 같다 */
    @Test fun headerBandFollowsHeaderFont() {
        assertEquals(16.25, MainLayout.headerBand(192.0), 1e-9)
        assertEquals(20.3125, MainLayout.headerBand(240.0), 1e-9)
        for (d in diameters) assertEquals(MainLayout.round(d, 0.0).headerFontSize * 1.25, MainLayout.headerBand(d), 1e-9)
    }
}
