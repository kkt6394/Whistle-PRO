package com.aidenstudio.whistlepro.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputChannelsTests {
    private fun facts(
        isSamsung: Boolean = true,
        backKeyShortPress: Int? = 1,
        touchBezelOn: Int? = 1,
        hasRotaryDevice: Boolean = true,
        hasStemKey: Boolean = false,
    ) = DeviceInputFacts(isSamsung, backKeyShortPress, touchBezelOn, hasRotaryDevice, hasStemKey)

    private fun confirmed(vararg c: InputChannel, name: String? = null) =
        ConfirmedChannels(c.toSet(), name)

    @Test
    fun backKeyRecentsIsUnavailableEvenIfConfirmed() {
        assertEquals(
            ChannelStatus.UnavailableNow(UnavailableReason.BACK_KEY_RECENTS),
            channelStatus(InputChannel.BACK_KEY, facts(backKeyShortPress = 0), confirmed(InputChannel.BACK_KEY)),
        )
    }

    @Test
    fun backKeyDefaultConfirmed() {
        assertEquals(ChannelStatus.Confirmed,
            channelStatus(InputChannel.BACK_KEY, facts(backKeyShortPress = 1), confirmed(InputChannel.BACK_KEY)))
        assertEquals(ChannelStatus.Unconfirmed,
            channelStatus(InputChannel.BACK_KEY, facts(backKeyShortPress = 1), confirmed()))
    }

    @Test
    fun backKeyReadFailureIsNotBlockedButNotConfirmed() {
        assertEquals(ChannelStatus.Unconfirmed,
            channelStatus(InputChannel.BACK_KEY, facts(backKeyShortPress = null), confirmed()))
        assertEquals(ChannelStatus.Confirmed,
            channelStatus(InputChannel.BACK_KEY, facts(backKeyShortPress = null), confirmed(InputChannel.BACK_KEY)))
    }

    @Test
    fun touchBezelOffBlocksRotaryConfirmedOnNonPhysicalBezel() {
        val f = facts(touchBezelOn = 0)
        assertEquals(ChannelStatus.UnavailableNow(UnavailableReason.TOUCH_BEZEL_OFF),
            channelStatus(InputChannel.ROTARY, f, confirmed(InputChannel.ROTARY, name = "sec_touchscreen")))
        assertEquals(ChannelStatus.Confirmed,
            channelStatus(InputChannel.ROTARY, f, confirmed(InputChannel.ROTARY, name = "detent_bezel")))
        assertEquals(ChannelStatus.Confirmed,
            channelStatus(InputChannel.ROTARY, facts(isSamsung = false, touchBezelOn = 0),
                confirmed(InputChannel.ROTARY, name = "sec_touchscreen")))
    }

    @Test
    fun nonSamsungHasNoBackKeyChannel() {
        assertEquals(listOf(InputChannel.ROTARY),
            detectableChannels(facts(isSamsung = false, hasRotaryDevice = true)))
        assertEquals(listOf(InputChannel.BACK_KEY, InputChannel.ROTARY, InputChannel.STEM),
            detectableChannels(facts(hasStemKey = true)))
        assertEquals(ChannelStatus.Unconfirmed,
            channelStatus(InputChannel.BACK_KEY, facts(isSamsung = false), confirmed(InputChannel.BACK_KEY)))
    }

    @Test
    fun physicalBezelName() {
        assertTrue(isPhysicalBezel("detent_bezel"))
        assertFalse(isPhysicalBezel("sec_touchscreen"))
        assertFalse(isPhysicalBezel(null))
    }

    /** 회전 통로 이름: 물리 베젤 장치이거나 실제 제조사가 삼성(터치 베젤)이면 베젤, 그 밖은 크라운 (2026-11-06 Task 5 검토) */
    @Test
    fun rotaryIsBezelByDeviceOrMaker() {
        val base = DeviceInputFacts(isSamsung = true, backKeyShortPress = 1, touchBezelOn = null, hasRotaryDevice = true, hasStemKey = false)
        assertTrue(rotaryIsBezel(base.copy(rotaryDeviceName = "detent_bezel")))
        assertTrue(rotaryIsBezel(base.copy(rotaryDeviceName = "sec_touch_bezel", samsungMaker = true)))
        // 디버그 빌드의 isSamsung(늘 참)은 보지 않는다: 에뮬레이터 회전 장치는 크라운
        assertFalse(rotaryIsBezel(base.copy(rotaryDeviceName = "virtio_input_rotary")))
        assertFalse(rotaryIsBezel(base.copy(isSamsung = false, rotaryDeviceName = "pixel_crown")))
    }

    @Test
    fun firstConfirmedIsAutoSelectedUntilUserPicks() {
        assertEquals(ChoiceState(PhysicalInputChoice.ROTARY, false),
            ChoiceState(PhysicalInputChoice.OFF, false).afterConfirmed(InputChannel.ROTARY, emptySet()))
        assertEquals(ChoiceState(PhysicalInputChoice.OFF, true),
            ChoiceState(PhysicalInputChoice.OFF, true).afterConfirmed(InputChannel.ROTARY, emptySet()))
        // 이미 확인된 회전이 골라져 있으면 나중에 확인한 버튼으로 바꾸지 않는다
        assertEquals(ChoiceState(PhysicalInputChoice.ROTARY, false),
            ChoiceState(PhysicalInputChoice.ROTARY, false).afterConfirmed(InputChannel.BACK_KEY, setOf(InputChannel.ROTARY)))
    }

    @Test
    fun unconfirmedLeftoverChoiceIsReplacedByFirstConfirmed() {
        // 2026-11-06 재검토 N-1: 안 고른 채 미확인 통로(1.4.0의 갤럭시 자동 뒤로가기 등)가 남아 있으면,
        // 다른 통로를 확인했을 때 그 통로로 바뀌어야 한다 — 아니면 확인을 마쳐도 물리 입력이 꺼진 채 안내가 다시 뜬다
        assertEquals(ChoiceState(PhysicalInputChoice.STEM, false),
            ChoiceState(PhysicalInputChoice.BACK_KEY, false).afterConfirmed(InputChannel.STEM, emptySet()))
        // 직접 고른 값은 미확인이어도 건드리지 않는다
        assertEquals(ChoiceState(PhysicalInputChoice.BACK_KEY, true),
            ChoiceState(PhysicalInputChoice.BACK_KEY, true).afterConfirmed(InputChannel.STEM, emptySet()))
    }

    @Test
    fun activeChannelOnlyWhenChosenAndConfirmed() {
        val rot = ChoiceState(PhysicalInputChoice.ROTARY, true)
        assertNull(activeChannel(rot, facts(), confirmed()))
        assertEquals(InputChannel.ROTARY, activeChannel(rot, facts(), confirmed(InputChannel.ROTARY)))
        assertNull(activeChannel(ChoiceState(PhysicalInputChoice.BACK_KEY, true), facts(backKeyShortPress = 0),
            confirmed(InputChannel.BACK_KEY)))
        assertNull(activeChannel(ChoiceState(PhysicalInputChoice.OFF, true), facts(), confirmed(InputChannel.ROTARY)))
    }

    @Test
    fun confirmationNeedsAllThreeInOrder() {
        val p = ConfirmationProgress(InputChannel.ROTARY)
        assertFalse(p.accept(PhysicalInput.SECONDARY))
        assertEquals(0, p.step)
        assertTrue(p.accept(PhysicalInput.PRIMARY))
        assertFalse(p.accept(PhysicalInput.UNDO))
        assertEquals(1, p.step)
        assertTrue(p.accept(PhysicalInput.SECONDARY))
        assertFalse(p.done)
        assertTrue(p.accept(PhysicalInput.UNDO))
        assertTrue(p.done)
        assertFalse(p.accept(PhysicalInput.PRIMARY))
        p.reset()
        assertEquals(0, p.step)
        assertFalse(p.done)
    }

    /** 최종 검토 I-3: 갤럭시 비클래식에서 터치 베젤이 꺼져 있으면 확인 전에도 안내. 물리 베젤·삼성 아님·못 읽음은 안내 없음 */
    @Test
    fun touchBezelOffHintOnlyOnGalaxyWithoutPhysicalBezel() {
        val galaxy = DeviceInputFacts(true, 1, 0, true, false, rotaryDeviceName = null, samsungMaker = true)
        // 장치 이름을 아직 모르는 첫 확인: 물리 베젤이 목록에 없으면 안내
        assertTrue(touchBezelOffHint(galaxy, confirmed()))
        assertTrue(touchBezelOffHint(galaxy.copy(rotaryDeviceName = "sec_touch_bezel"), confirmed()))
        // 터치 베젤이 켜지면 사라진다, 못 읽으면 보이지 않는다
        assertFalse(touchBezelOffHint(galaxy.copy(touchBezelOn = 1), confirmed()))
        assertFalse(touchBezelOffHint(galaxy.copy(touchBezelOn = null), confirmed()))
        // 물리 베젤(클래식): 지금 읽은 장치든 예전 확인 장치든 절대 안 보인다
        assertFalse(touchBezelOffHint(galaxy.copy(rotaryDeviceName = "detent_bezel"), confirmed()))
        assertFalse(touchBezelOffHint(galaxy, confirmed(InputChannel.ROTARY, name = "detent_bezel")))
        // 실제 삼성이 아니면(디버그 강제 isSamsung만 참) 안 보인다, 회전 장치가 없어도 안 보인다
        assertFalse(touchBezelOffHint(galaxy.copy(samsungMaker = false), confirmed()))
        assertFalse(touchBezelOffHint(galaxy.copy(hasRotaryDevice = false), confirmed()))
    }

    /** 최종 검토 I-1: 대기 화면 [물리 입력 확인] 안내 조건 (설계 5-2 "처음 값") */
    @Test
    fun inputCheckPromptRules() {
        val all = listOf(InputChannel.BACK_KEY, InputChannel.ROTARY)
        val fresh = ChoiceState(PhysicalInputChoice.OFF, userPicked = false)
        val pickedOff = ChoiceState(PhysicalInputChoice.OFF, userPicked = true)
        // 확인한 통로가 없다: 고른 적이 없든, 끄기를 골랐든 안내 (확인해야 켤 수 있다)
        assertTrue(showsInputCheckPrompt(fresh, all, emptySet(), active = null, blocked = false))
        assertTrue(showsInputCheckPrompt(pickedOff, all, emptySet(), active = null, blocked = false))
        // 확인됐지만 잡히지 않는 통로뿐이면 확인한 것이 없는 것
        assertTrue(showsInputCheckPrompt(pickedOff, all, setOf(InputChannel.STEM), active = null, blocked = false))
        // 확인한 통로가 있는데 일부러 끄기를 골랐으면 안내하지 않는다
        assertFalse(showsInputCheckPrompt(pickedOff, all, setOf(InputChannel.ROTARY), active = null, blocked = false))
        // 확인했고 아직 고른 적이 없는데 꺼져 있으면 안내 (확인하면 자동으로 골라진다)
        assertTrue(showsInputCheckPrompt(fresh, all, setOf(InputChannel.ROTARY), active = null, blocked = false))
        // 켜져 있거나, 막힘 노란 줄을 보이는 중이거나, 잡히는 통로가 없으면 안내하지 않는다
        assertFalse(showsInputCheckPrompt(fresh, all, setOf(InputChannel.ROTARY), active = InputChannel.ROTARY, blocked = false))
        assertFalse(showsInputCheckPrompt(fresh, all, setOf(InputChannel.BACK_KEY), active = null, blocked = true))
        assertFalse(showsInputCheckPrompt(fresh, emptyList(), emptySet(), active = null, blocked = false))
    }
}
