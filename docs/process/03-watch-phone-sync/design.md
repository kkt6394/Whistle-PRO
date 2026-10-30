## 1. 의도와 이유

안드로이드 폰 앱을 만들어 안드로이드 사용자(갤럭시 폰 + 갤럭시 워치)가 아이폰 사용자와 같은 것을 얻게 한다. Wear OS 앱(M6-2b, main 병합 완료)은 폰 없이 경기를 돌리지만 결제가 없어 무료 범위에 묶여 있고, 명단·프리셋을 받을 곳이 없으며, 끝난 경기를 볼 곳이 워치뿐이다. 폰 앱이 있어야 안드로이드 사용자에게 같은 경험을 줄 수 있다.

사용자 결정(2026-10-19):
- **기능은 아이폰 앱과 동일하게.** 빼는 기능 없음.
- **생김새는 안드로이드 관습을 따른다.** 픽셀 복제가 아니다.
- 시험 기기: 갤럭시 노트10(Android 12) + 갤럭시 워치(Wear OS 6, 모델은 점검표 참고).

## 2. 목표 · 비목표 · 하면 안 되는 것

목표
- 아이폰 앱의 모든 기능이 안드로이드 폰에 있다: 경기 목록·상세(기록·운동·지도)·보고서 PDF·SNS 카드·시즌 통계, 프리셋·명단 편집, 설정(심판 이름·사용 방법·시작하기 카드·구독), 구독(Whistle PRO 월간·연간).
- 워치 연동이 아이폰↔애플 워치와 같은 규칙으로 동작한다. 폰→워치: 프리셋·구독 상태. 워치→폰: 끝난 경기·운동 기록, 폰의 확인, 받았는지 묻기, 지원 안 함 알림, 컨텍스트 요청.
- Wear 앱에 미뤄 둔 것을 붙인다: 명단 선택 화면, 홈의 "보내지 않은 경기 N개" 안내, 컨텍스트 요청, Health Connect 운동 저장, 폰이 보낸 구독 상태 반영.
- 경기 기록 JSON은 Swift와 키·값이 같다(이미 그렇다). 아이폰에서 만든 기록 파일을 안드로이드 폰에 넣어도 읽힌다.
- 5개 언어(한·영·태·베·인니), 번역 파일은 `apple/i18n/*.json` 공용.

비목표
- 안드로이드 폰에서 경기를 직접 돌리는 것(워치 전용, 상위 스펙과 같다).
- 아이폰과 픽셀 단위로 같은 화면.
- Wear 단독 결제(워치에서 Play 결제). 결제는 폰에서만 하고 워치는 폰이 보낸 구독 상태를 쓴다.
- 폰↔폰·클라우드 동기화, 계정.
- Play 스토어 등록 자료(스크린샷·설명)와 실제 출시. 이 설계는 앱 자체까지다.
- Google Cloud 프로젝트·Maps API 키 발급, Play Console 상품 등록(사용자 작업).

하면 안 되는 것(실패 조건)
- 워치 쪽 경기 중 동작(타이머·진동·운동 세션)이 바뀌는 것. 워치 연동은 경기 밖의 코드만 건드린다.
- 아이폰 앱과 다른 동작. 같은 입력에 같은 결과여야 한다(예: 확인 누락 복구, 프리셋 한도, 구독 만료 시각 잠금).
- 이전 기록 파일을 못 읽는 것.

### 4.3 폰↔워치 동기화 (Wear Data Layer)

WatchConnectivity의 네 통로를 Data Layer로 이렇게 대응한다. 메시지 내용(키 이름·값)은 `SyncPayload`를 그대로 옮긴다.

| 아이폰 | 역할 | 안드로이드 |
|---|---|---|
| `updateApplicationContext` | 폰→워치 프리셋·구독 상태(통째로 바뀜, 마지막 값만 남음) | `DataClient` DataItem `/context` 하나(`sentAt` 포함, 64KB 한도 검사 `presetsFittingContext`는 그대로) |
| `transferFile` + 메타데이터 | 워치→폰 경기 파일(outbox, 전송마다 다른 이름) | `DataClient` DataItem `/match/<id>/<전송 이름>` + `Asset`(파일). 시스템이 보관해 두 앱이 꺼져 있어도 연결되면 전달되므로 WatchConnectivity와 의미가 같다(`ChannelClient`는 양쪽 앱이 살아 있어야 해서 쓰지 않는다 — 빌더 검토 반영). 폰이 확인(ack)을 보내면 워치가 DataItem을 지운다. 완료 알림은 없으므로 `transferFinished`는 "DataItem 쓰기 성공/실패"로 옮기고, 실패 60초 뒤 재시도·`MatchSendThrottle`은 그대로 |
| `transferUserInfo` | 폰→워치 확인(ack)·지원 안 함, 워치→폰 컨텍스트 요청 대기열 | `MessageClient`로 보내고 닿지 않으면 DataItem `/ack/<id>`·`/context-request`에 남긴다(폰·워치가 켜질 때 읽고 지운다). 아이폰의 "대기열에 하나만" 규칙 유지 |
| `sendMessage`(답 있음) | 워치→폰 받았는지 묻기(`receiptQuery`/`receiptReply`), 컨텍스트 즉시 요청 | `MessageClient` 요청 + 답 메시지(요청 ID로 짝지음, 10초 timeout) |
| `isReachable`·`isWatchAppInstalled` | 닿음·설치 여부 | `NodeClient.connectedNodes` + `CapabilityClient`(워치 앱 capability `whistlepro_wear`, 폰 앱 `whistlepro_mobile`) |

- 워치 쪽 `WearSync`는 애플 `WatchSync`의 동작을 같은 이름 함수로 옮긴다: `sendPending`, `resendOnActivation`(30초 1회), `askReceiptsThenSend`, `requestContext`/`queueContextRequest`, `received(presets, entitlement)`, `acknowledged`, `rejectedByPhone`, `transferFinished`, 출처별 구독 상태 합치기(`saveSources`: 폰 전달값만, 워치 자체 StoreKit 확인은 없으므로 폰 값 하나). 폰 쪽 `MobileSync`는 `PhoneSync`를 옮긴다.
- 폰 앱이 꺼져 있을 때 받은 파일·메시지는 `WearableListenerService`가 받아 보관함에 넣고 확인을 보낸다(아이폰의 백그라운드 수신과 같다).
- 워치 홈에 "폰으로 보내지 않은 경기 N개"(아이폰 워치 홈과 같은 문구·위치), 폰이 '지원 안 함'을 알리면 "폰 앱 업데이트 필요".
- 워치 명단 선택 화면: 애플 워치 `RosterPickerScreen`을 옮긴다(`Roster`·`EventEntry`·`onPitchNumbers` 묶음 규칙은 Kotlin에 있음). 명단이 없는 팀은 지금의 숫자 패드.
- 워치 Health Connect: 경기 종료 뒤(`finishedAt`) 운동 세션을 "축구"로 저장(거리·심박·경로). 종료 후 5분 안 실행 취소는 기록을 하나만 남긴다(점검표 9·33). Health Connect 권한은 운동 권한과 함께 묻는다.
- 폰이 보낸 구독 상태는 워치 `Entitlement`로 저장하고 `activeUntil` 잠금 예약은 지금 코드를 그대로 쓴다. 디버그 `pro` extra는 남긴다.

### 4.5 테스트와 점검

- JVM: `:session` 옮긴 테스트(Swift `RefereeSessionTests`와 같은 이름), `:mobile` 모델·동기화 로직 테스트(Data Layer는 인터페이스 뒤에 두고 가짜로).
- 폰 계측(Compose UI 테스트, 에뮬레이터 Pixel 급 1대): 아이폰 `PhoneUITests`(흐름·프리셋 편집·명단 편집·보고서 공유·스토어 데모)를 옮긴다.
- 워치 계측: 명단 선택 화면·홈 전송 안내·컨텍스트 반영을 기존 `wear_small`·`wear_large`에서.
- 실기기 점검표 `docs/qa/android-device-checklist.md`: 애플 `device-checklist.md`의 폰·동기화·결제·보고서 항목(20~31, 34, 40~41, 43~44, 49, 51)을 옮기고, Wear 점검표에서 M6-3으로 미룬 9·33·42·52를 다시 연다. 노트10 + 갤럭시 워치.
- 완료 기준: 위 테스트 전부 통과, 점검표의 에뮬레이터 항목 통과, 실기기 항목은 사용자 확인. Play 구매 항목은 계정이 열릴 때까지 "대기"로 표시.

