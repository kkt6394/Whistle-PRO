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

    @Test
    fun firstConfirmedIsAutoSelectedUntilUserPicks() {
        assertEquals(ChoiceState(PhysicalInputChoice.ROTARY, false),
            ChoiceState(PhysicalInputChoice.OFF, false).afterConfirmed(InputChannel.ROTARY))
        assertEquals(ChoiceState(PhysicalInputChoice.OFF, true),
            ChoiceState(PhysicalInputChoice.OFF, true).afterConfirmed(InputChannel.ROTARY))
        assertEquals(ChoiceState(PhysicalInputChoice.ROTARY, false),
            ChoiceState(PhysicalInputChoice.ROTARY, false).afterConfirmed(InputChannel.BACK_KEY))
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
}
