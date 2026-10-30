# M6-3b: 안드로이드 폰↔워치 연동 (묶음 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 아이폰↔애플 워치와 같은 규칙으로 안드로이드 폰(`:mobile`)과 Wear 앱(`:wear`)을 잇는다 — 폰→워치 프리셋·구독 상태, 워치→폰 끝난 경기·운동 기록과 확인, 받았는지 묻기, 지원 안 함, 컨텍스트 요청. 워치에 미뤄 둔 프리셋 고르기·명단 선택·전송 안내를 붙이고, 폰이 받은 경기를 Health Connect에 저장한다.

**Architecture:** 메시지 규칙(무엇을 언제 보내고 다시 보내는가)은 `:session`의 순수 로직(Swift `WatchSync`·`PhoneSync`·`DeliveryRetry`를 옮긴 것)에 두고, Wear Data Layer 호출은 각 앱의 얇은 어댑터(`PhoneLink`·`WatchLink` 인터페이스 구현)가 맡는다. 로직은 가짜 링크로 JVM 테스트하고, 실제 전송은 노트10 + 갤럭시 워치 실기기 점검표로 확인한다.

**Tech Stack:** Kotlin 2.4, `com.google.android.gms:play-services-wearable`(Data Layer: `DataClient`·`MessageClient`·`CapabilityClient`·`NodeClient`), `kotlinx-coroutines-play-services`(`await()`), `androidx.health.connect:connect-client`(폰), Compose(폰 M3·워치 Wear M3), JUnit 6.

**Spec:** `docs/superpowers/specs/2026-10-19-android-phone-app-design.md` §4.3(동기화 표·워치 쪽 항목), §4.4 중 구독 상태 전달, §4.5. 기준 구현: `apple/RefereeWatch/WatchSync.swift`, `apple/RefereeiOS/PhoneSync.swift`, `apple/RefereeCore/Sources/RefereeSession/{Sync,DeliveryRetry,ContextDelivery}.swift`, `apple/RefereeWatch/AppModel.swift`(`applyPreset`·`presetsChanged`), `apple/RefereeWatch/Views/{SetupView,HomeView,RosterPickerView,EventEntryView}.swift`.

## Global Constraints

- **동작 기준은 아이폰↔애플 워치(main 2026-10-20).** 재전송 간격·묶음 규칙·확인 규칙은 Swift 값 그대로: 화면 복귀 재전송 30초에 한 번(`ResendThrottle(30)`), 같은 경기 10분 안 재전송 금지(`MatchSendThrottle(600)`, 실패하면 바로 다시), 받았는지 묻기 60초 간격·한 번에 500개(`ReceiptCheck(60)`, `MAX_IDS = 500`), 전송 실패 60초 뒤 재시도, 컨텍스트 요청은 대기열에 하나만, 지원 안 함 경기는 이번 실행 동안 보내지 않음.
- **경기 중 동작은 바꾸지 않는다.** 워치 동기화는 경기 밖(홈·설정·종료 후)에서만 무언가를 바꾼다. 프리셋이 바뀌어도 진행 중 경기의 설정은 그대로.
- 메시지 내용(키 이름·값)은 Task 1 이전에 옮긴 `SyncPayload`를 그대로 쓴다. Data Layer에는 JSON 바이트로 싣는다.
- 두 앱 `applicationId "com.aidenstudio.whistlepro"`와 같은 서명(디버그 키스토어 공용)이어야 Data Layer가 닿는다. 바꾸지 않는다.
- Data Layer 경로(정확히 이 문자열): 폰→워치 컨텍스트 DataItem `/whistlepro/context`, 워치→폰 경기 DataItem `/whistlepro/match/<경기 id>/<전송 이름>`(Asset 키 `bundle`, 메타 키 `matchID`), 확인·지원 안 함 메시지 `/whistlepro/ack`, 받았는지 묻기 `/whistlepro/receipt-query`(답 `/whistlepro/receipt-reply`), 컨텍스트 요청 메시지 `/whistlepro/context-request`(대기 DataItem 같은 경로). capability: 워치 `whistlepro_wear`, 폰 `whistlepro_mobile`(각 앱 `res/values/wear.xml`의 `android_wear_capabilities`).
- 문구는 한국어 원문 키 `L("…")`, 번역 `apple/i18n/{en,th,vi,id}.json`(애플 워치 문구는 이미 있다: "폰으로 보내지 않은 경기 %lld개", "폰 앱 업데이트가 필요한 경기 %lld개", "번호 입력", "건너뛰기", "뛰는 선수", "벤치", "퇴장", "명단", "규칙", "FIFA 표준"). 새 문구는 네 파일에 넣고 `gen_wear_strings.py --module wear|mobile`로 생성.
- 커밋 메시지에 `Co-Authored-By`를 넣지 않는다. git은 `/usr/bin/git`(worktree 훅이 `git` 래퍼를 막는다).
- 작업은 worktree(가지 `m6-3b-sync`)에서 하고 끝나면 main에 병합(병합·푸시는 사용자 확인).

### 플랫폼 차이 (WatchConnectivity → Data Layer) — 스펙 §4.3 표에 더해 이 계획에서 정하는 것

| 애플 | 안드로이드 |
|---|---|
| `updateApplicationContext` | `DataClient.putDataItem(PutDataMapRequest("/whistlepro/context"))`에 `presets`·`entitlement`(JSON 바이트)·`sentAt`(Double). `setUrgent()` |
| `transferFile` + `outstandingFileTransfers` | 경기마다 DataItem + Asset. "전송 중" = 그 경기의 DataItem이 아직 있음(`DataClient.getDataItems(uri prefix)`). 확인(ack)을 받으면 워치가 그 DataItem을 지운다 |
| `didFinish fileTransfer` 실패 | `putDataItem` 실패(`Task` 예외) = 실패 → `MatchSendThrottle.failed` + 60초 뒤 재시도 |
| `sendMessage(replyHandler)` | `MessageClient.sendMessage(node, path, bytes)` + 답 메시지 경로로 받음, 요청 ID(UUID 문자열)로 짝짓기, 10초 timeout |
| `transferUserInfo` (확인·요청 대기열) | 폰→워치 확인은 `MessageClient`(워치 노드 연결 시) 실패하면 DataItem `/whistlepro/ack/<경기 id>`; 워치는 받으면 처리 후 그 DataItem을 지운다. 워치→폰 컨텍스트 요청 대기는 DataItem `/whistlepro/context-request`(있으면 다시 만들지 않음) |
| `isReachable` | `NodeClient.connectedNodes`에 `whistlepro_mobile`/`whistlepro_wear` capability 노드가 있음 |
| `isWatchAppInstalled` | 폰: `CapabilityClient.getCapability("whistlepro_wear", FILTER_ALL)` 노드 있음 + 변경 리스너 |
| 워치 자체 StoreKit 확인 | 없음. 워치 구독 상태 = 폰이 보낸 값(`Entitlement.combine(phone, null)`) |
| 앱이 꺼져 있을 때 수신 | 양쪽 `WearableListenerService`(`onDataChanged`·`onMessageReceived`) |
| HealthKit 운동 저장(워치) | **폰이 경기를 받을 때 Health Connect에 저장** (아래 Ruling) |

**Ruling (스펙 §4.3 "워치 Health Connect" 변경):** Health Connect SDK는 폰 쪽 저장소이고 Wear OS 워치 앱이 직접 쓸 수 있다는 문서가 없다. 그래서 폰이 경기 묶음을 받을 때(`MatchLibrary.upsert` 성공 뒤) 운동이 있으면 `ExerciseSessionRecord`(EXERCISE_TYPE_SOCCER, 시작 = 첫 킥오프, 끝 = `finishedAt`, clientRecordId = 경기 id라 같은 경기가 다시 와도 하나만)와 `HeartRateRecord`·`DistanceRecord`·`ExerciseRoute`를 쓴다. 권한은 폰 설정의 "Health Connect에 운동 저장" 줄에서 묻고, 허용 전에는 쓰지 않는다. 틀리면(워치 직접 저장이 가능·필요하면) 나중에 워치 쪽 저장을 더한다.

## Review Focus

1. **폰 앱이 꺼진 동안 끝난 경기**(노트10에서 앱 강제 종료) → 워치에서 끝내면 `WearableListenerService`가 받아 저장하고 확인을 보내, 워치 홈의 "보내지 않은 경기"가 사라진다. → Task 5 `listenerStoresBundleAndAcks`(서비스 핸들러를 가짜 링크로), 점검표 34
2. **확인을 놓친 경기**(폰은 저장했는데 ack가 안 옴) → 워치가 다시 열릴 때 받았는지 물어 지우고, 같은 경기를 폰에 두 번 저장하지 않는다. → Task 2 `askReceiptsBeforeResend`, `receiptQueryAnswersStoredAndDeleted`
3. **워치 앱 재설치**(폰 앱은 안 열림) → 워치가 컨텍스트를 요청하고 폰 리스너가 답해 프리셋·구독이 돌아온다. → Task 2 `newInstallRequestsContextOnce`, Task 5 `contextRequestRepublishes`, 점검표 40
4. **프리셋 크기 한도**(명단 40명 프리셋 12개) → 앞쪽만 가고 구독 상태는 늘 간다. 워치는 받은 만큼만 보여 준다. → Task 2 `oversizedPresetsKeepEntitlement`(기존 `presetsFittingContext` 사용 확인)
5. **경기 중 프리셋 변경**(폰에서 프리셋 수정) → 진행 중 경기는 그대로, 경기 뒤 설정 화면은 새 내용(워치에서 바꾼 시간은 같은 프리셋 내용이면 유지). → Task 3 `presetsChangedDuringMatchKeepsSession`, `presetsChangedKeepsLocalEditsWhenSame`

---

## File Structure

```
android/
  gradle/libs.versions.toml           play-services-wearable, kotlinx-coroutines-play-services, health-connect-client
  session/src/main/kotlin/.../session/
    DeliveryRetry.kt     ResendThrottle, MatchSendThrottle, ReceiptCheck (Swift DeliveryRetry.swift)
    SyncWire.kt          SyncPaths(경로 상수), 메시지 Map ↔ JSON ByteArray, 요청 ID 짝짓기 도우미
    WatchSyncLogic.kt    워치 쪽 규칙 (Swift WatchSync를 링크 인터페이스 위로): WatchLink 인터페이스 + WatchSyncLogic
    PhoneSyncLogic.kt    폰 쪽 규칙 (Swift PhoneSync): PhoneLink 인터페이스 + PhoneSyncLogic
  wear/src/main/
    res/values/wear.xml                     capability whistlepro_wear
    kotlin/.../wear/sync/DataLayerWatchLink.kt   WatchLink 구현 (Data Layer)
    kotlin/.../wear/sync/WearSync.kt             WatchSyncLogic + 링크 + 저장(Prefs) + StateFlow(pendingCount, heldCount, presets)
    kotlin/.../wear/sync/WatchListenerService.kt WearableListenerService
    kotlin/.../wear/Prefs.kt                     presets·selectedPresetID·entitlement(폰 값) 저장
    kotlin/.../wear/AppModel.kt                  draft를 프리셋 기반으로, presetsChanged
    kotlin/.../wear/ui/SetupScreen.kt            [규칙] 줄
    kotlin/.../wear/ui/HomeScreen.kt             보내지 않은 경기·업데이트 필요 안내
    kotlin/.../wear/ui/RosterPickerScreen.kt     명단 선택 (Swift RosterPickerView)
    kotlin/.../wear/ui/EventEntryScreen.kt       번호 단계: 명단 있으면 RosterPicker
  mobile/src/main/
    res/values/wear.xml                     capability whistlepro_mobile
    kotlin/.../mobile/sync/DataLayerPhoneLink.kt
    kotlin/.../mobile/sync/MobileSync.kt         PhoneSyncLogic + 링크 + MobileModel 연결, isWatchAppInstalled/isActivated StateFlow
    kotlin/.../mobile/sync/PhoneListenerService.kt
    kotlin/.../mobile/health/HealthConnectWriter.kt
    kotlin/.../mobile/MobileModel.kt             entitlement StateFlow, sync 연결
    kotlin/.../mobile/ui/...                     시작하기 카드·설정 워치 상태, 설정 Health Connect 줄
docs/qa/android-device-checklist.md, docs/qa/wear-device-checklist.md, android/README.md
```

---

### Task 1: `:session` — 재전송 규칙·전송 형식 (Swift `DeliveryRetry.swift` + Data Layer 형식)

**Files:**
- Create: `session/.../DeliveryRetry.kt`, `SyncWire.kt`
- Test: `session/src/test/.../DeliveryRetryTests.kt`, `SyncWireTests.kt`

**Interfaces (Produces):**
- `class ResendThrottle(val interval: Double) { fun allows(now: Double): Boolean }`
- `class MatchSendThrottle(val interval: Double) { fun allows(matchID: UUID, now: Double): Boolean; fun failed(matchID: UUID) }`
- `class ReceiptCheck(val interval: Double) { val inFlight: Boolean; fun canAsk(now): Boolean; fun sendsAfterAnswer(reachable: Boolean, now: Double): Boolean; fun begin(pending: List<UUID>, now: Double): List<UUID>?; fun answered(asked: List<UUID>, received: List<UUID>, now: Double): List<UUID>; fun failed(); companion { const val MAX_IDS = 500; fun toDelete(asked, received): List<UUID> } }`
- `object SyncPaths { CONTEXT = "/whistlepro/context"; MATCH_PREFIX = "/whistlepro/match/"; ACK = "/whistlepro/ack"; ACK_PREFIX = "/whistlepro/ack/"; RECEIPT_QUERY = "/whistlepro/receipt-query"; RECEIPT_REPLY = "/whistlepro/receipt-reply"; CONTEXT_REQUEST = "/whistlepro/context-request"; CAPABILITY_WEAR = "whistlepro_wear"; CAPABILITY_MOBILE = "whistlepro_mobile"; fun match(matchID: UUID, transfer: String): String; fun matchID(path: String): UUID? }`
- `object SyncWire { fun encode(message: Map<String, Any?>): ByteArray; fun decode(bytes: ByteArray): Map<String, Any?>; fun request(id: String, message: Map<String, Any?>): ByteArray; fun requestId(bytes: ByteArray): String? }` — JSON 객체. 값은 String·Boolean·Double·List<String>·ByteArray(base64 문자열로, 키 끝에 붙이지 말고 값 형식 `{"$b64":"…"}`)만. `SyncPayload`의 모든 메시지가 왕복해야 한다.

- [x] **Step 1:** Swift `DeliveryRetryTests` 15개를 같은 이름으로 옮긴다(`waitsUntilConditionHolds`·`waitGivesUpAfterTimeout`는 `waitUntil`이 코루틴 `withTimeoutOrNull`로 대신되므로 옮기지 않고 주석). `SyncWireTests`: `everySyncPayloadMessageRoundTrips`(ack·unsupported·receiptQuery·receiptReply·contextRequest·context(프리셋 2개+entitlement+sentAt) 각각 encode→decode 후 `SyncPayload` 읽기 함수 결과가 같음), `requestIdRoundTrips`, `matchPathCarriesId`, `foreignBytesDecodeEmpty`. Run: `./gradlew :session:test --tests '*DeliveryRetry*' --tests '*SyncWire*'` → 컴파일 실패
- [x] **Step 2:** 구현. **Step 3:** `./gradlew :session:test` 통과. **Step 4:** 커밋 `feat(android): session — 재전송 규칙·Data Layer 전송 형식 (Swift DeliveryRetry 옮김)`

### Task 2: `:session` — 워치·폰 동기화 규칙 (Swift `WatchSync`·`PhoneSync`를 링크 인터페이스 위로)

**Files:**
- Create: `session/.../WatchSyncLogic.kt`, `PhoneSyncLogic.kt`
- Test: `session/src/test/.../WatchSyncLogicTests.kt`, `PhoneSyncLogicTests.kt`, `FakeLinks.kt`

**Interfaces (Produces):**
- `interface WatchLink { val reachable: Boolean; suspend fun putMatch(matchID: UUID, transfer: String, bundle: ByteArray); suspend fun pendingTransfers(): Set<UUID>; suspend fun removeMatch(matchID: UUID); suspend fun ask(path: String, message: Map<String, Any?>): Map<String, Any?>?  /* null = 실패·timeout */; suspend fun queueContextRequest(); suspend fun cancelContextRequest() }`
- `class WatchSyncLogic(store: FileMatchLogStore, link: WatchLink, clock: () -> Double, encodeBundle: (MatchBundle) -> ByteArray)`: `val pendingCount: Int`, `val held: Set<UUID>`, `suspend fun sendPending()`, `suspend fun resendOnActivation()`, `suspend fun askReceiptsThenSend()`, `suspend fun requestContext()`(needsContext일 때만), `fun onActivated(context: Map<String, Any?>?): Received?`, `fun onContext(context: Map<String, Any?>): Received`, `suspend fun onPhoneReply(message: Map<String, Any?>)`(ack → `store.deleteArchived` + `link.removeMatch`, unsupported → held), `suspend fun onTransferFailed(matchID)`(throttle failed; 재시도 예약은 앱 어댑터가 60초). `data class Received(val presets: List<MatchPreset>?, val entitlement: Entitlement?)`. 규칙은 Swift `WatchSync` 그대로(위 Global Constraints 값).
- `interface PhoneLink { val watchInstalled: Boolean; val canDeliver: Boolean; suspend fun putContext(context: Map<String, Any?>); suspend fun reply(message: Map<String, Any?>)  /* 메시지 실패 시 DataItem 대기 */ }`
- `class PhoneSyncLogic(library: MatchLibrary, link: PhoneLink, currentContext: () -> Pair<List<MatchPreset>, Entitlement>?)`: `suspend fun sendContext(presets, entitlement)`(`ContextDelivery` 사용), `suspend fun watchStateChanged(installed: Boolean)`, `suspend fun contextRequested(): Map<String, Any?>`(컨텍스트를 다시 올리고 같은 내용을 돌려줌, 없으면 빈 맵), `fun receiptQuery(message): Map<String, Any?>?`(물음이면 `receiptReply(library.received(among))`), `suspend fun receive(matchID: UUID?, bytes: ByteArray?): ReceiveResult`(읽기 실패 → `unsupported(matchID)` 답, 성공 → `upsert` 뒤 `ack`; `ReceiveResult.Stored(bundle) | Unsupported | Failed`)

- [x] **Step 1:** 실패 테스트(가짜 링크·임시 폴더 `FileMatchLogStore`/`MatchLibrary`). `WatchSyncLogicTests`: `sendsEveryArchivedMatchOnce`, `skipsMatchesAlreadyInFlight`, `sameMatchNotResentWithinTenMinutes`, `failedTransferResendsImmediately`, `ackDeletesArchivedAndDataItem`, `unsupportedHoldsForThisRun`, `askReceiptsBeforeResend`(닿으면 묻고 받은 경기는 지운 뒤 나머지만 보냄 = Review Focus 2), `receiptQueryFailureStillSends`, `unreachableSendsWithoutAsking`, `resendOnActivationThrottled30s`, `newInstallRequestsContextOnce`(빈 컨텍스트 → 닿으면 ask, 실패하면 대기열 1개만, 받으면 대기열 취소 = Review Focus 3), `emptyContextReplyQueuesRequest`. `PhoneSyncLogicTests`: `contextQueuedUntilWatchInstalled`, `reinstallResendsLastContext`, `contextRequestedRepublishesAndReturns`, `receiptQueryAnswersStoredAndDeleted`, `unreadableBundleRepliesUnsupported`, `storedBundleAcked`, `deletedMatchStillAcked`(사용자가 지운 경기도 확인 보냄, 다시 저장 안 함), `oversizedPresetsKeepEntitlement`(Review Focus 4).
- [x] **Step 2:** 구현. **Step 3:** `./gradlew :session:test` 통과. **Step 4:** 커밋 `feat(android): session — 워치·폰 동기화 규칙 (Swift WatchSync·PhoneSync, 링크 인터페이스)`

### Task 3: Wear — Data Layer 링크·리스너·프리셋 고르기·구독 상태·홈 안내

**Files:**
- Create: `wear/.../sync/DataLayerWatchLink.kt`, `WearSync.kt`, `WatchListenerService.kt`, `wear/src/main/res/values/wear.xml`
- Modify: `wear/build.gradle.kts`(play-services-wearable, coroutines-play-services), `AndroidManifest.xml`(서비스: `com.google.android.gms.wearable.DATA_CHANGED`·`MESSAGE_RECEIVED` 인텐트 필터, 경로 접두 `/whistlepro`), `Prefs.kt`, `WhistleApp.kt`, `AppModel.kt`, `ui/SetupScreen.kt`, `ui/HomeScreen.kt`, `ui/AppNavigation.kt`
- Test: `wear/src/test/.../AppModelPresetTests.kt`, `wear/src/androidTest/.../SyncUiTest.kt`

**Interfaces (Produces):**
- `Prefs`: `var presets: List<MatchPreset>`(JSON), `var selectedPresetID: UUID`(기본 `MatchPreset.FIFA_STANDARD.id`), `var phoneEntitlement: Entitlement?`; 기존 `entitlement` 읽기는 `Entitlement.combine(phoneEntitlement, null)`로. 기존 `rules`·`recordsRoute`는 FIFA 표준일 때의 워치 편집값으로 남긴다(이전 설치 호환).
- `WearSync(context, store, prefs, scope)`: `val pendingCount: StateFlow<Int>`, `val heldCount: StateFlow<Int>`, `val presets: StateFlow<List<MatchPreset>>`, `var onPresetsChanged: () -> Unit`, `fun activate()`, `fun onScreenResumed()`(= `resendOnActivation`), `fun sendPending()`(경기 종료 뒤 AppModel이 부름). 60초 재시도는 여기서.
- `AppModel`: 생성자 `draft: () -> MatchSetup` 대신 `selectedPresetID: StateFlow<UUID>`, `draft: StateFlow<MatchSetup>` 소유; `fun applyPreset(id: UUID)`, `fun presetsChanged(presets: List<MatchPreset>)`(Swift 규칙: 경기 중이면 무시, 고른 프리셋 내용이 실제로 바뀌었을 때만 다시 적용, 지워졌으면 FIFA 표준), `fun updateDraft(transform: (MatchSetup) -> MatchSetup)`(설정 화면의 기간 길이·하프타임·GPS 편집). 경기가 끝나면(`newMatch`·종료 보관) `sync.sendPending()`.
- `SetupScreen`: 맨 위 `ValueRow(L("규칙"), 이름, "row-preset")` → 목록(FIFA 표준 + 받은 프리셋 이름) → 고르면 `applyPreset`. 팀 이름·색·명단은 프리셋 것을 쓴다(FIFA 표준이면 지금처럼 `홈`·`원정`).
- `HomeScreen(hasPro, pendingCount, heldCount, …)`: 애플 `HomeView`처럼 [새 경기]와 [지난 경기] 사이에 보조 글자 줄 "휴대폰으로 보내지 않은 경기 %lld개"(pendingCount > 0), "휴대폰 앱 업데이트가 필요한 경기 %lld개"(heldCount > 0) — 애플 키 "폰으로…"는 번역이 iPhone이라 안드로이드 키를 따로 둔다. 원 안 배치는 Swift `homeFill`처럼 다 들어가면 그대로, 안 들어가면 남은 높이에서 스크롤(숨기면 작은 워치에서 안내가 사라진다).

- [x] **Step 1:** 실패 테스트. `AppModelPresetTests`(JVM): `applyPresetSetsDraftFromPreset`, `presetsChangedDuringMatchKeepsSession`(Review Focus 5), `presetsChangedKeepsLocalEditsWhenSame`, `deletedPresetFallsBackToFifa`, `fifaKeepsWatchEditedRules`(기존 Prefs.rules 사용). `SyncUiTest`(wear_small 계측, WearSync를 가짜 값으로): `homeShowsPendingAndHeldLines`, `setupShowsPresetRowAndAppliesTeams`(프리셋 고르면 킥오프 뒤 메인 팀 이름이 프리셋 이름).
- [x] **Step 2:** 구현(DataLayerWatchLink: `Wearable.getDataClient/MessageClient/NodeClient/CapabilityClient`, `await()`; ask는 요청 ID를 실어 `MESSAGE_RECEIVED`로 온 답을 `CompletableDeferred` 맵에서 짝짓고 10초 timeout. 리스너 서비스는 앱 프로세스의 `WearSync`로 넘긴다(프로세스가 없으면 앱 객체에서 만든다)).
- [x] **Step 3:** `./gradlew :wear:testDebugUnitTest :wear:check` 통과, `connectedDebugAndroidTest`(wear_small) 기존 71 + 새 테스트 통과. **Step 4:** 새 문구 번역·생성. 커밋 `feat(android): wear — 폰 연동(Data Layer)·프리셋 고르기·구독 상태·보내지 않은 경기 안내`

### Task 4: Wear — 명단 선택 화면

**Files:**
- Create: `wear/.../ui/RosterPickerScreen.kt`
- Modify: `wear/.../ui/EventEntryScreen.kt`(번호 단계)
- Test: `wear/src/androidTest/.../RosterPickerTest.kt`

**Interfaces (Produces):**
- `enum class RosterGrouping { OUTGOING, INCOMING, ANY }`, `@Composable fun PlayerNumberStep(title, players: List<Player>, sentOff: Set<Long>, onPitch: Set<Long>?, grouping, initial: Long?, onDone: (Long?) -> Unit)` — 명단이 비면 기존 `NumberPad`, 있으면 `RosterPickerScreen`. Swift `PlayerNumberView`·`RosterPickerView` 그대로: 맨 위 [번호 입력][건너뛰기] 한 줄(44dp × 배율), 묶음 뛰는 선수·벤치·퇴장(들어오는 선수 고를 땐 벤치 먼저, 빈 묶음 뺌, 선발 없으면 번호순 한 목록), 행 높이 48dp × 배율, 퇴장 0.4 흐림, 고친 번호 ✓(라임), 고칠 번호가 명단에 없으면 숫자패드부터, 숫자패드에서 [명단]으로 돌아가기. 목록은 `ScalingLazyColumn`(베젤 스크롤). 묶음 계산은 `onPitchNumbers`·`sentOffNumbers`(이미 Kotlin에 있음) 사용.
- testTag: `roster.group.onPitch|bench|sentOff`, `roster.row.<번호>`, `roster.type`, `roster.skip`.

- [x] **Step 1:** 실패 테스트(wear_small, 명단 20명 프리셋으로 데모 경기): `goalPicksFromRoster`, `substitutionOutgoingShowsOnPitchFirstIncomingShowsBenchFirst`, `sentOffDimmedButSelectable`, `typeNumberFallsBackToPad`, `skipRecordsWithoutNumber`, `editOutsideRosterOpensPad`, `noRosterUsesNumberPad`. 데모용으로 Wear `Demo.kt`에 장면 `roster`(명단 있는 프리셋으로 전반 진행 중) 추가.
- [x] **Step 2:** 구현. **Step 3:** wear_small·wear_large 계측 통과. **Step 4:** 커밋 `feat(android): wear — 명단 선택 화면 (선발·벤치·퇴장 묶음, 번호 입력·건너뛰기)`

### Task 5: 폰 — Data Layer 링크·리스너·워치 설치 상태·구독 상태 전달

**Files:**
- Create: `mobile/.../sync/DataLayerPhoneLink.kt`, `MobileSync.kt`, `PhoneListenerService.kt`, `mobile/src/main/res/values/wear.xml`
- Modify: `mobile/build.gradle.kts`, `AndroidManifest.xml`, `MobileModel.kt`, `WhistleMobileApp.kt`, `ui/components/StartGuideCard.kt` 호출부, `ui/settings/SettingsScreen.kt`
- Test: `mobile/src/test/.../MobileSyncTests.kt`, `mobile/src/androidTest/.../WatchStatusUiTest.kt`

**Interfaces (Produces):**
- `MobileModel`: `val entitlement: StateFlow<Entitlement>` — 이 묶음은 `demo.pro`면 `Entitlement(expiresAt = 지금 + 365일, checkedAt = 지금)`, 아니면 `Entitlement.NONE`(묶음 3이 결제로 바꾼다). 프리셋 저장·삭제·되돌리기와 entitlement 변화 때 `sync.sendContext(presets, entitlement)`.
- `MobileSync(context, library, scope, currentContext)`: `val isActivated: StateFlow<Boolean>`(첫 capability 조회 끝), `val isWatchAppInstalled: StateFlow<Boolean>`, `fun activate()`, `fun sendContext(presets, entitlement)`, `var onReceive: () -> Unit`(모델 reload). 리스너 서비스는 앱이 꺼져 있어도 `PhoneSyncLogic.receive`/`receiptQuery`/`contextRequested`를 돌린다.
- 시작하기 카드 `watchInstalled = if (isActivated) isWatchAppInstalled else null`(아이폰과 같음), 설정 "워치 앱" 값 "설치됨"/"설치 안 됨"(활성화 전 "—").

- [x] **Step 1:** 실패 테스트. `MobileSyncTests`(JVM, 가짜 링크): `listenerStoresBundleAndAcks`(Review Focus 1: 앱 모델 없이 서비스 경로만으로 upsert + ack), `presetSaveSendsContext`, `entitlementChangeSendsContext`, `contextRequestRepublishes`(Review Focus 3). `WatchStatusUiTest`(phone 계측, 설치 상태를 가짜로): `startCardShowsInstalled`, `startCardHidesStatusBeforeActivation`, `settingsShowsWatchStatus`.
- [x] **Step 2:** 구현. **Step 3:** `:mobile:testDebugUnitTest :mobile:check`, 폰 계측 전체 통과. **Step 4:** 커밋 `feat(android): mobile — 워치 연동(Data Layer)·백그라운드 수신·워치 설치 상태·구독 상태 전달`

### Task 6: 폰 — Health Connect 운동 저장

**Files:**
- Create: `mobile/.../health/HealthConnectWriter.kt`
- Modify: `mobile/build.gradle.kts`(connect-client), `AndroidManifest.xml`(권한 `android.permission.health.WRITE_EXERCISE`·`WRITE_HEART_RATE`·`WRITE_DISTANCE`·`WRITE_EXERCISE_ROUTE`, 권한 설명 액티비티 `ViewPermissionUsageActivity`/`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`), `MobileSync.kt`(저장 성공 뒤 호출), `ui/settings/SettingsScreen.kt`("Health Connect에 운동 저장" 줄), `ui/settings/HowToScreen.kt`(한 줄)
- Test: `mobile/src/test/.../HealthRecordsTests.kt`

**Interfaces (Produces):**
- `object HealthRecords { fun of(bundle: MatchBundle, zone: ZoneOffset): HealthRecords.Set? }` — 순수 변환(JVM 테스트): 운동이 없으면 null, `ExerciseSessionRecord(start = 첫 킥오프, end = finishedAt ?: 마지막 기간 끝, type = EXERCISE_TYPE_SOCCER, title = "홈 vs 원정", clientRecordId = "match-<id>", clientRecordVersion = 기록 명령 수)`, 심박 샘플 → `HeartRateRecord`(경기 구간, 5초 샘플), 거리 → `DistanceRecord`(구간 합 1개), 경로 → `ExerciseRoute`(정확도 20m 이하 점만).
- `class HealthConnectWriter(context)`: `suspend fun isAvailable(): Boolean`(`HealthConnectClient.getSdkStatus`), `suspend fun hasPermissions(): Boolean`, `suspend fun write(bundle): Boolean`(권한 없으면 false, 같은 clientRecordId는 upsert라 실행 취소 뒤 재전송도 하나), 설정 줄: 사용 불가면 "Health Connect 앱 설치" 링크(Play), 권한 없으면 [허용](PermissionController 계약), 허용됨이면 "저장 중".

- [x] **Step 1:** `HealthRecordsTests`: `noWorkoutNoRecords`, `sessionSpansKickoffToFinishedAt`, `undoReuseKeepsSameClientId`(같은 경기 두 번 → 같은 clientRecordId, 버전 증가), `routeDropsInaccuratePoints`, `heartRateOnlyDuringPeriods`.
- [x] **Step 2:** 구현. **Step 3:** 단위·lint·폰 계측(설정 화면 줄 표시) 통과. **Step 4:** 새 문구 번역·생성. 커밋 `feat(android): mobile — 받은 경기의 운동을 Health Connect에 저장 (축구, 심박·거리·경로)`

### Task 7: 문서·점검표

**Files:** `docs/qa/android-device-checklist.md`(20·21·22·27·28·34·40·44·51 동기화 항목을 "묶음 2"로 열고 기기 노트10 + 갤럭시 워치, Health Connect 9·33 항목), `docs/qa/wear-device-checklist.md`(42 명단 선택, 9·33 → 폰 저장으로 변경 표시), `android/README.md`(동기화 구조·경로·리스너·실기기 짝짓기 방법), `docs/backlog.md`(M6-3으로 넘겼던 동기화 항목 처리 표시), 이 계획 체크박스.

- [x] **Step 1:** 문서 갱신. **Step 2:** 전체 확인: `./gradlew :core:test :session:test :wear:testDebugUnitTest :wear:check :mobile:testDebugUnitTest :mobile:check :mobile:assembleRelease`, 폰 계측 전체(phone AVD), 워치 계측 전체(wear_small). **Step 3:** 커밋 `docs(android): 폰↔워치 연동 마무리 — 점검표·README·백로그`

---

## 결정이 필요한 것 (실행 전)

1. **Health Connect를 폰에서 저장**(위 Ruling) — 스펙은 워치 저장이었다. 폰 저장이면 폰 앱 설정에서 권한을 한 번 허용해야 한다.
2. **실행 방식** — 서브에이전트(추천: Task 7개가 링크 인터페이스로 묶이고 데이터 손실이 걸린 규칙이라 Task마다 검토가 값을 한다), 직접 실행. 지난번처럼 속도를 원하면 Task 3·4와 5·6을 묶어 디스패치·검토 수를 줄일 수 있다.
