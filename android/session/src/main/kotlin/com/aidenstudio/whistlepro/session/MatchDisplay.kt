package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.MatchWarning
import com.aidenstudio.whistlepro.core.PeriodRef
import com.aidenstudio.whistlepro.core.Phase
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import com.aidenstudio.whistlepro.core.activeSinBins
import com.aidenstudio.whistlepro.core.clock
import com.aidenstudio.whistlepro.core.currentPeriodLabel
import com.aidenstudio.whistlepro.core.intervalLength
import com.aidenstudio.whistlepro.core.intervalRemaining
import com.aidenstudio.whistlepro.core.kickoffTeam
import com.aidenstudio.whistlepro.core.matchWarnings
import com.aidenstudio.whistlepro.core.score
import com.aidenstudio.whistlepro.core.shootout
import com.aidenstudio.whistlepro.core.wholeSeconds
import java.util.Locale
import kotlin.math.max

data class SinBinBadge(
    val team: Team,
    val number: Int?,
    /** 남은 시간 "4:12" */
    val remaining: String,
    /** 남은 시간(초). 가장 먼저 끝나는 일시 퇴장을 고를 때 쓴다. */
    val seconds: Long,
)

data class ShootoutDisplay(
    val score: TeamPair<Int>,
    /** 다음 키커 팀 (승부차기 진행 중이고 선축 지정 후에만) */
    val next: Team?,
    /** 아직 먼저 찰 팀을 고르지 않았다 */
    val needsFirstTeam: Boolean,
    /** 팀별 킥 성공 여부 (찬 순서) */
    val results: TeamPair<List<Boolean>>,
    /** 기본 키커 수 */
    val kicksPerTeam: Int,
)

/** 링 표시용 진행값 (초) */
data class ClockProgress(val elapsed: Long, val length: Long)

/** 워치 화면에 그대로 찍을 값. 화면은 계산 없이 이것만 표시한다. */
data class MatchDisplay(
    val screen: Screen,
    /** 마지막으로 시작한 기간 (1H, Q2, ET1) */
    val periodLabel: String?,
    /** 휴식·연장 대기 중일 때 다음 기간 */
    val nextPeriodLabel: String?,
    /** 큰 시계. 경과 모드는 규정 시간에서 멈추고 초과분은 addedTime으로 보여준다. */
    val mainClock: String,
    /** "+2:13" (규정 시간 초과 시에만) */
    val addedTime: String?,
    /** 이 기간 누적 중단 시간 "1:24" (중단이 있었거나 진행 중일 때만) */
    val stoppage: String?,
    val stoppageRunning: Boolean,
    /** 휴식 중이면 남은 시간, 연장 대기 중이면 기다린 시간 */
    val intervalClock: String?,
    val score: TeamPair<Int>,
    val kickoffTeam: Team?,
    val sinBins: List<SinBinBadge>,
    /** 최소 인원 미만인 팀 */
    val shortHandedTeams: List<Team>,
    /** 승부차기 중이거나 승부차기가 있었던 경기 */
    val shootout: ShootoutDisplay?,
    /** 기간 진행 중일 때 경과·규정 길이 */
    val periodProgress: ClockProgress?,
    /** 휴식 중일 때 경과·휴식 길이 */
    val intervalProgress: ClockProgress?,
) {
    enum class Screen { NOT_STARTED, PERIOD, INTERVAL, PRE_EXTRA_TIME, PENALTIES, FINISHED }

    companion object {
        operator fun invoke(state: MatchState, now: Double, clockDisplay: ClockDisplay): MatchDisplay {
            val phase = state.phase
            val reading = state.clock(now)
            val status = state.shootout
            val shootout = if (phase == Phase.Penalties || status.kicks.home + status.kicks.away > 0) {
                val home = mutableListOf<Boolean>()
                val away = mutableListOf<Boolean>()
                for (e in state.events) {
                    val kick = e.payload as? EventPayload.PenaltyKick ?: continue
                    (if (kick.team == Team.HOME) home else away) += kick.scored
                }
                ShootoutDisplay(
                    score = status.goals,
                    next = if (phase == Phase.Penalties) status.next else null,
                    needsFirstTeam = phase == Phase.Penalties && state.shootoutFirstTeam == null,
                    results = TeamPair(home, away),
                    kicksPerTeam = state.rules.penaltyKicksPerTeam,
                )
            } else {
                null
            }
            val preExtraEnd = state.periods.lastOrNull()?.endedAt
            return MatchDisplay(
                screen = when (phase) {
                    Phase.NotStarted -> Screen.NOT_STARTED
                    is Phase.Period -> Screen.PERIOD
                    is Phase.Interval -> Screen.INTERVAL
                    Phase.PreExtraTime -> Screen.PRE_EXTRA_TIME
                    Phase.Penalties -> Screen.PENALTIES
                    is Phase.Finished -> Screen.FINISHED
                },
                periodLabel = state.currentPeriodLabel,
                nextPeriodLabel = when (phase) {
                    is Phase.Interval -> PeriodRef(phase.after.kind, phase.after.index + 1).label(state.rules.periodCount)
                    Phase.PreExtraTime -> PeriodRef(PeriodRef.Kind.EXTRA, 1).label(state.rules.periodCount)
                    else -> null
                },
                mainClock = if (reading != null) {
                    format(if (clockDisplay == ClockDisplay.ELAPSED) reading.matchSecond - reading.addedTime else reading.remaining)
                } else {
                    format(if (clockDisplay == ClockDisplay.ELAPSED) 0 else state.regularPeriodLength.toLong())
                },
                addedTime = reading?.addedTime?.takeIf { it > 0 }?.let { "+" + formatShort(it) },
                stoppage = reading?.takeIf { it.stoppageTotal > 0 || it.stoppageRunning }?.let { formatShort(it.stoppageTotal) },
                stoppageRunning = reading?.stoppageRunning ?: false,
                intervalClock = if (phase == Phase.PreExtraTime && preExtraEnd != null) {
                    format(max(0, wholeSeconds(now - preExtraEnd)))
                } else {
                    state.intervalRemaining(now)?.let(::format)
                },
                score = state.score,
                kickoffTeam = state.kickoffTeam,
                sinBins = state.activeSinBins(now).map { SinBinBadge(it.team, it.number, formatShort(it.remaining), it.remaining) },
                shortHandedTeams = state.matchWarnings(now).map { (it as MatchWarning.BelowMinimumPlayers).team },
                shootout = shootout,
                periodProgress = if (reading != null && phase is Phase.Period) {
                    ClockProgress(reading.elapsed, reading.elapsed - reading.addedTime + reading.remaining)
                } else {
                    null
                },
                intervalProgress = (phase as? Phase.Interval)?.let { p ->
                    state.intervalRemaining(now)?.let { remaining ->
                        val total = state.intervalLength(p.after).toLong()
                        ClockProgress(max(0, total - remaining), total)
                    }
                },
            )
        }

        // 기기 언어와 상관없이 아라비아 숫자로 쓴다 (Locale.ROOT)
        /**
         * "MM:SS". 100분이 넘으면 "100:00"처럼 분 자리가 늘어난다.
         * (Swift String(format: "%02d")는 32비트를 넘는 손상 값에서 깨진 숫자를 내지만 여기서는 그대로 쓴다)
         */
        fun format(seconds: Long): String {
            val s = max(0, seconds)
            return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
        }

        /** "M:SS" */
        fun formatShort(seconds: Long): String {
            val s = max(0, seconds)
            return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
        }
    }
}
