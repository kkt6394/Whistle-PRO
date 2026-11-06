package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.AlertKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PhysicalInputTests {
    private fun shootout(needsFirstTeam: Boolean = false, next: Team? = null) = ShootoutDisplay(
        score = TeamPair(0, 0), next = next, needsFirstTeam = needsFirstTeam,
        results = TeamPair(emptyList(), emptyList()), kicksPerTeam = 5,
    )

    private fun match(
        screen: MatchDisplay.Screen,
        stoppage: Boolean = false,
        shootout: ShootoutDisplay? = null,
        goalOpen: Boolean = false,
        needsKickoffTeam: Boolean = false,
    ) = PhysicalContext.Match(screen, stoppage, shootout, goalOpen, needsKickoffTeam)

    @Test fun mappingTable() {
        val rows: List<Triple<String, PhysicalContext, List<PhysicalAction>>> = listOf(
            Triple("ready", PhysicalContext.Ready,
                listOf(PhysicalAction.Kickoff, PhysicalAction.None, PhysicalAction.None)),
            Triple("period", match(MatchDisplay.Screen.PERIOD),
                listOf(PhysicalAction.Pause, PhysicalAction.OpenGoal, PhysicalAction.Undo)),
            Triple("stoppage", match(MatchDisplay.Screen.PERIOD, stoppage = true),
                listOf(PhysicalAction.Resume, PhysicalAction.EndPeriod, PhysicalAction.Undo)),
            Triple("goalOpen", match(MatchDisplay.Screen.PERIOD, goalOpen = true),
                listOf(PhysicalAction.Goal(Team.HOME), PhysicalAction.Goal(Team.AWAY), PhysicalAction.CancelGoal)),
            Triple("interval", match(MatchDisplay.Screen.INTERVAL),
                listOf(PhysicalAction.StartPeriod, PhysicalAction.None, PhysicalAction.Undo)),
            Triple("preExtraTime", match(MatchDisplay.Screen.PRE_EXTRA_TIME),
                listOf(PhysicalAction.StartPeriod, PhysicalAction.None, PhysicalAction.Undo)),
            // 2026-11-07 연장 전 동전 던지기(IFAB Law 8): 선축이 없으면 한 번=홈·두 번=원정
            Triple("preExtraTimeNoKickoff", match(MatchDisplay.Screen.PRE_EXTRA_TIME, needsKickoffTeam = true),
                listOf(
                    PhysicalAction.SetKickoffTeam(Team.HOME), PhysicalAction.SetKickoffTeam(Team.AWAY),
                    PhysicalAction.Undo,
                )),
            Triple("intervalIgnoresKickoff", match(MatchDisplay.Screen.INTERVAL, needsKickoffTeam = true),
                listOf(PhysicalAction.StartPeriod, PhysicalAction.None, PhysicalAction.Undo)),
            Triple("firstTeam", match(MatchDisplay.Screen.PENALTIES, shootout = shootout(needsFirstTeam = true)),
                listOf(
                    PhysicalAction.ShootoutFirstTeam(Team.HOME), PhysicalAction.ShootoutFirstTeam(Team.AWAY),
                    PhysicalAction.Undo,
                )),
            Triple("kick", match(MatchDisplay.Screen.PENALTIES, shootout = shootout(next = Team.HOME)),
                listOf(PhysicalAction.ShootoutKick(true), PhysicalAction.ShootoutKick(false), PhysicalAction.Undo)),
            Triple("decided", match(MatchDisplay.Screen.PENALTIES, shootout = shootout()),
                listOf(PhysicalAction.None, PhysicalAction.None, PhysicalAction.Undo)),
            Triple("notStarted", match(MatchDisplay.Screen.NOT_STARTED),
                listOf(PhysicalAction.None, PhysicalAction.None, PhysicalAction.None)),
            Triple("finished", match(MatchDisplay.Screen.FINISHED),
                listOf(PhysicalAction.None, PhysicalAction.None, PhysicalAction.Undo)),
        )
        val inputs = listOf(PhysicalInput.PRIMARY, PhysicalInput.SECONDARY, PhysicalInput.UNDO)
        for ((name, context, expected) in rows) {
            inputs.zip(expected).forEach { (input, action) ->
                assertEquals(action, physicalAction(input, context), "$name $input")
            }
        }
    }

    @Test fun goalOpenOutsidePeriodIsIgnored() {
        val context = match(MatchDisplay.Screen.INTERVAL, goalOpen = true)
        assertEquals(PhysicalAction.StartPeriod, physicalAction(PhysicalInput.PRIMARY, context))
    }

    @Test fun contextFromDisplay() {
        val state = makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.ToggleStoppage to 100.0))
        val display = MatchDisplay(state, 130.0, ClockDisplay.ELAPSED)
        assertEquals(
            PhysicalContext.Match(MatchDisplay.Screen.PERIOD, true, null, false, false),
            PhysicalContext(display, goalOpen = false),
        )
    }

    @Test fun contextFromPreExtraTimeDisplay() {
        val regular = listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
        )
        val waiting = MatchDisplay(makeState(RuleSet(extraTime = true), regular), 6390.0, ClockDisplay.ELAPSED)
        assertEquals(
            match(MatchDisplay.Screen.PRE_EXTRA_TIME, needsKickoffTeam = true),
            PhysicalContext(waiting, goalOpen = false),
        )
        val picked = MatchDisplay(
            makeState(RuleSet(extraTime = true), regular + (CommandKind.SetKickoffTeam(Team.AWAY) to 6400.0)),
            6410.0, ClockDisplay.ELAPSED,
        )
        assertEquals(match(MatchDisplay.Screen.PRE_EXTRA_TIME), PhysicalContext(picked, goalOpen = false))
    }

    @Test fun pressCount() {
        assertEquals(PhysicalInput.PRIMARY, PhysicalInput.fromPressCount(1))
        assertEquals(PhysicalInput.SECONDARY, PhysicalInput.fromPressCount(2))
        assertEquals(PhysicalInput.UNDO, PhysicalInput.fromPressCount(3))
        assertNull(PhysicalInput.fromPressCount(0))
        assertNull(PhysicalInput.fromPressCount(4))
    }

    private val allActions: List<PhysicalAction> = listOf(
        PhysicalAction.Kickoff, PhysicalAction.StartPeriod, PhysicalAction.Pause, PhysicalAction.Resume,
        PhysicalAction.OpenGoal, PhysicalAction.Goal(Team.HOME), PhysicalAction.Goal(Team.AWAY),
        PhysicalAction.ShootoutFirstTeam(Team.HOME), PhysicalAction.ShootoutFirstTeam(Team.AWAY),
        PhysicalAction.ShootoutKick(true), PhysicalAction.ShootoutKick(false),
        PhysicalAction.EndPeriod, PhysicalAction.CancelGoal, PhysicalAction.Undo,
        PhysicalAction.SetKickoffTeam(Team.HOME), PhysicalAction.SetKickoffTeam(Team.AWAY),
    )

    @Test fun hapticTable() {
        fun p(tone: HapticTone, repeats: Int, interval: Double) = HapticPattern(tone, repeats, interval)
        val expected: List<Pair<PhysicalAction, HapticPattern>> = listOf(
            PhysicalAction.Kickoff to p(HapticTone.START, 1, 0.0),
            PhysicalAction.StartPeriod to p(HapticTone.START, 1, 0.0),
            PhysicalAction.Pause to p(HapticTone.STOP, 2, 0.3),
            PhysicalAction.Resume to p(HapticTone.START, 2, 0.3),
            PhysicalAction.OpenGoal to p(HapticTone.NOTIFICATION, 1, 0.0),
            PhysicalAction.Goal(Team.HOME) to p(HapticTone.SUCCESS, 1, 0.0),
            PhysicalAction.ShootoutFirstTeam(Team.HOME) to p(HapticTone.SUCCESS, 1, 0.0),
            PhysicalAction.ShootoutKick(true) to p(HapticTone.SUCCESS, 1, 0.0),
            PhysicalAction.Goal(Team.AWAY) to p(HapticTone.SUCCESS, 2, 0.3),
            PhysicalAction.ShootoutFirstTeam(Team.AWAY) to p(HapticTone.SUCCESS, 2, 0.3),
            PhysicalAction.ShootoutKick(false) to p(HapticTone.STOP, 2, 0.3),
            PhysicalAction.EndPeriod to p(HapticTone.STOP, 3, 0.3),
            PhysicalAction.CancelGoal to p(HapticTone.RETRY, 3, 0.3),
            PhysicalAction.Undo to p(HapticTone.RETRY, 3, 0.3),
            PhysicalAction.SetKickoffTeam(Team.HOME) to p(HapticTone.SUCCESS, 1, 0.0),
            PhysicalAction.SetKickoffTeam(Team.AWAY) to p(HapticTone.SUCCESS, 2, 0.3),
        )
        for ((action, pattern) in expected) assertEquals(pattern, action.hapticPattern, "$action")
        assertNull(PhysicalAction.None.hapticPattern)
    }

    @Test fun hapticsDoNotCollideWithAlerts() {
        val taken = AlertKind.entries.map { it.hapticPattern }.toSet() + HapticPattern(HapticTone.STOP, 1, 0.0)
        for (action in allActions) assertFalse(action.hapticPattern in taken, "$action")
    }

    @Test fun hapticsDistinctWithinState() {
        val contexts: List<PhysicalContext> = listOf(
            PhysicalContext.Ready,
            match(MatchDisplay.Screen.PERIOD),
            match(MatchDisplay.Screen.PERIOD, stoppage = true),
            match(MatchDisplay.Screen.PERIOD, goalOpen = true),
            match(MatchDisplay.Screen.INTERVAL),
            match(MatchDisplay.Screen.PRE_EXTRA_TIME, needsKickoffTeam = true),
            match(MatchDisplay.Screen.PENALTIES, shootout = shootout(needsFirstTeam = true)),
            match(MatchDisplay.Screen.PENALTIES, shootout = shootout(next = Team.AWAY)),
            match(MatchDisplay.Screen.FINISHED),
        )
        for (context in contexts) {
            val patterns = PhysicalInput.entries.mapNotNull { physicalAction(it, context).hapticPattern }
            assertEquals(patterns.size, patterns.toSet().size, "$context")
        }
    }
}
