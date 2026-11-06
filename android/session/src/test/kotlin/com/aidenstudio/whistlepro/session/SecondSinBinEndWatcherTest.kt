package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 워치 끝 안내 감시 (Swift SinBinIFABSessionTests.secondSinBinEndedAppearsOnceThenClears와 같은 케이스) */
class SecondSinBinEndWatcherTest {
    private val rules = RuleSet(sinBin = true, sinBinLength = 600)

    private fun sinBin(at: Double) = CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 7, CardKind.SIN_BIN)) to at

    /** 홈 7번 두 번째 일시퇴장 (100·800, 1400에 끝남) 뒤 추가 명령 */
    private fun state(vararg extra: Pair<CommandKind, Double>) =
        makeState(rules, listOf(CommandKind.StartPeriod to 0.0, sinBin(100.0), sinBin(800.0)) + extra)

    /** 끝난 틱에 한 번만, 다음 틱엔 없음 */
    @Test fun oncePerPlayer() {
        val id = UUID.randomUUID()
        val s = state()
        val watcher = SecondSinBinEndWatcher()
        assertNull(watcher.update(s, id, 1390.0))
        assertNull(watcher.update(s, id, 1399.0))
        val ended = watcher.update(s, id, 1401.0)
        assertEquals(Team.HOME, ended?.team)
        assertEquals(7L, ended?.number)
        assertEquals(false, ended?.filled)
        assertNull(watcher.update(s, id, 1402.0))
        assertNull(watcher.update(s, id, 1500.0))
    }

    /** 앱을 다시 실행하면(새 감시) 이미 끝난 것은 알리지 않는다 */
    @Test fun restartBaselineNoNotice() {
        val id = UUID.randomUUID()
        val s = state()
        val restarted = SecondSinBinEndWatcher()
        assertNull(restarted.update(s, id, 1500.0))
        assertNull(restarted.update(s, id, 1501.0))
    }

    /** 끝나기 전에 교체로 채운 출전 금지는 끝나도 알리지 않는다 */
    @Test fun filledPlayerNoNotice() {
        val id = UUID.randomUUID()
        val filled = state(CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 12)) to 1000.0)
        val watcher = SecondSinBinEndWatcher()
        assertNull(watcher.update(filled, id, 1390.0))
        assertNull(watcher.update(filled, id, 1401.0))
    }
}
