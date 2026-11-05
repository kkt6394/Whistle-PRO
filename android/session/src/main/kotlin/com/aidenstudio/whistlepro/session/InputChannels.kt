package com.aidenstudio.whistlepro.session

// 2026-11-06 워치 물리 입력 통로(뒤로 키·회전·스템 버튼)의 상태·경기 전 선택·세 동작 확인 진행 판단.
// 순수 코틀린 — 기기 읽기·화면은 다른 모듈이 이 타입들을 채워 쓴다.

/** 물리 입력이 들어올 수 있는 통로 */
enum class InputChannel { BACK_KEY, ROTARY, STEM }

/** 사용자가 경기 전에 고르는 값. OFF는 물리 입력 끔. */
enum class PhysicalInputChoice { OFF, BACK_KEY, ROTARY, STEM }

fun InputChannel.asChoice(): PhysicalInputChoice = when (this) {
    InputChannel.BACK_KEY -> PhysicalInputChoice.BACK_KEY
    InputChannel.ROTARY -> PhysicalInputChoice.ROTARY
    InputChannel.STEM -> PhysicalInputChoice.STEM
}

/** 확인을 마쳤어도 지금은 쓸 수 없는 이유 */
enum class UnavailableReason { BACK_KEY_RECENTS, TOUCH_BEZEL_OFF }

sealed interface ChannelStatus {
    data object Confirmed : ChannelStatus
    data object Unconfirmed : ChannelStatus
    data class UnavailableNow(val reason: UnavailableReason) : ChannelStatus
}

/**
 * 기기에서 읽은 사실. 설정값은 읽기 실패면 null.
 * [isSamsung]은 뒤로가기 버튼 통로용(디버그 빌드는 늘 참), [samsungMaker]는 실제 제조사가 삼성인지(디버그 강제 없음, 회전 이름용),
 * [rotaryDeviceName]은 회전 장치 이름(물리 베젤이 있으면 그것, 없으면 null) — 확인 전에도 통로 이름을 정하려고 (2026-11-06 Task 5 검토)
 */
data class DeviceInputFacts(
    val isSamsung: Boolean,
    val backKeyShortPress: Int?,
    val touchBezelOn: Int?,
    val hasRotaryDevice: Boolean,
    val hasStemKey: Boolean,
    val rotaryDeviceName: String? = null,
    val samsungMaker: Boolean = false,
)

/** 사용자가 세 동작 확인을 끝낸 통로들 */
data class ConfirmedChannels(val channels: Set<InputChannel>, val rotaryDeviceName: String?)

/** 이 기기에서 감지되는 통로. 순서 BACK_KEY, ROTARY, STEM. */
fun detectableChannels(facts: DeviceInputFacts): List<InputChannel> = buildList {
    if (facts.isSamsung) add(InputChannel.BACK_KEY)
    if (facts.hasRotaryDevice) add(InputChannel.ROTARY)
    if (facts.hasStemKey) add(InputChannel.STEM)
}

/** 진짜 물리 베젤(딸깍 베젤)의 입력 장치 이름 */
fun isPhysicalBezel(deviceName: String?): Boolean = deviceName == "detent_bezel"

/**
 * 회전 통로를 "베젤"로 부를지(아니면 "크라운"): 물리 베젤 장치이거나, 실제 제조사가 삼성(갤럭시 비클래식의 터치 베젤)이면 베젤.
 * 확인 화면 통로 이름·대기 화면 안내("베젤을 돌려 킥오프"/"크라운을 돌려 킥오프")가 함께 쓴다
 */
fun rotaryIsBezel(facts: DeviceInputFacts): Boolean = isPhysicalBezel(facts.rotaryDeviceName) || facts.samsungMaker

fun channelStatus(
    channel: InputChannel,
    facts: DeviceInputFacts,
    confirmed: ConfirmedChannels,
): ChannelStatus {
    if (channel !in detectableChannels(facts)) return ChannelStatus.Unconfirmed
    // 막힘은 확인 여부보다 먼저 본다.
    if (channel == InputChannel.BACK_KEY && facts.backKeyShortPress == 0) {
        return ChannelStatus.UnavailableNow(UnavailableReason.BACK_KEY_RECENTS)
    }
    if (channel == InputChannel.ROTARY && facts.isSamsung && facts.touchBezelOn == 0 &&
        !isPhysicalBezel(confirmed.rotaryDeviceName) && channel in confirmed.channels
    ) {
        return ChannelStatus.UnavailableNow(UnavailableReason.TOUCH_BEZEL_OFF)
    }
    return if (channel in confirmed.channels) ChannelStatus.Confirmed else ChannelStatus.Unconfirmed
}

/** 경기 전 선택. userPicked가 참이면 사용자가 직접 고른 것이라 자동으로 바꾸지 않는다. */
data class ChoiceState(val choice: PhysicalInputChoice, val userPicked: Boolean)

/** 통로 하나가 새로 확인되면, 사용자가 안 골랐고 아직 꺼져 있을 때만 그 통로를 자동 선택 */
fun ChoiceState.afterConfirmed(channel: InputChannel): ChoiceState =
    if (!userPicked && choice == PhysicalInputChoice.OFF) copy(choice = channel.asChoice()) else this

/** 고른 통로가 Confirmed일 때만 그 통로, 아니면 null */
fun activeChannel(
    state: ChoiceState,
    facts: DeviceInputFacts,
    confirmed: ConfirmedChannels,
): InputChannel? {
    val channel = InputChannel.entries.firstOrNull { it.asChoice() == state.choice } ?: return null
    return channel.takeIf { channelStatus(it, facts, confirmed) == ChannelStatus.Confirmed }
}

/** 세 동작(PRIMARY → SECONDARY → UNDO)을 순서대로 맞게 눌렀는지 */
class ConfirmationProgress(val channel: InputChannel) {
    var step: Int = 0
        private set
    val done: Boolean get() = step == ORDER.size

    /** 다음 차례 입력이면 앞으로 가고 true. 틀리거나 이미 끝났으면 false, 제자리. */
    fun accept(input: PhysicalInput): Boolean {
        if (done || input != ORDER[step]) return false
        step++
        return true
    }

    fun reset() {
        step = 0
    }

    private companion object {
        val ORDER = listOf(PhysicalInput.PRIMARY, PhysicalInput.SECONDARY, PhysicalInput.UNDO)
    }
}
