package com.aidenstudio.whistlepro.session

/**
 * 폰 → 워치로 보내는 마지막 내용(프리셋·구독)과 전달 여부. (Swift ContextDelivery와 같음)
 * 워치 앱이 없거나 연결 전이면 기억해 두었다가 보내고, 워치 앱이 새로 설치되면(재설치 포함) 다시 보낸다.
 * 새로 설치된 워치 앱은 이전 내용을 갖고 있지 않기 때문이다.
 * 보낸 시각(`sentAt`)은 동기화 내용(SyncPayload.context)에 싣는다 — M6-3에서 옮긴다.
 */
class ContextDelivery<Payload : Any> {
    private var last: Payload? = null
    private var undelivered = false
    private var installed = false

    /** 보낼 내용을 기억한다. 지금 보낼 수 있으면 그 내용을, 아니면 null을 돌려준다. */
    fun send(payload: Payload, canDeliver: Boolean): Payload? {
        last = payload
        undelivered = !canDeliver
        return if (canDeliver) payload else null
    }

    /** 워치 상태가 바뀌었을 때 지금 보내야 할 내용 (없으면 null) */
    fun watchStateChanged(installed: Boolean, canDeliver: Boolean): Payload? {
        val newlyInstalled = installed && !this.installed
        this.installed = installed
        val payload = last
        if (!canDeliver || payload == null || !(undelivered || newlyInstalled)) return null
        undelivered = false
        return payload
    }
}
