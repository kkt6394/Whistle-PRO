# 실기기 점검 — 워치↔폰 동기화 항목 발췌

원본은 `android-device-checklist.md`(갤럭시 노트10 + 갤럭시 워치)의 "묶음 2 — 폰↔워치 연동" 표에서 전송·재시도·확인 항목만 옮겼다. 결과 칸의 `JVM (...)`은 같은 동작을 고정한 단위 테스트 이름이다(`android/session`·앱 모듈).

| # | 항목 | 방법 | 기대 결과 | 결과 |
|---|---|---|---|---|
| 20 | 경기 전송 | 폰 앱을 연 채 워치에서 경기 종료 → [새 경기] | 1분 안에 폰 목록에 경기가 나타나고 워치 홈의 "휴대폰으로 보내지 않은 경기"가 사라짐 | **실기기 통과** (2026-10-23, 노트10 + 갤럭시 워치 SM-R965N 226dp, 디버그 빌드). JVM (`sendsEveryArchivedMatchOnce`, `ackDeletesArchivedAndDataItem`, `storedBundleAcked`) |
| 21 | 오프라인 전송 | 폰 비행기 모드(블루투스도 끔)에서 경기 2개 종료 → 비행기 모드 해제 | 두 경기 모두 폰에 도착, 중복 없음. 워치 안내가 앱을 다시 열지 않아도 사라짐 | **실기기 통과** (2026-10-23, 노트10 + 갤럭시 워치 SM-R965N 226dp, 디버그 빌드). JVM (`skipsMatchesAlreadyInFlight`, `sameMatchNotResentWithinTenMinutes`, `listenerStoresBundleAndAcks`의 재수신 하나만) |
| 22 | 프리셋 전송 | 폰에서 프리셋 추가·수정·삭제 | 워치 설정 맨 위 [규칙] 목록에 바로 반영(워치 앱 재실행 불필요). 고른 프리셋을 지우면 FIFA 표준 | **실기기 통과** (2026-10-23, 노트10 + 갤럭시 워치 SM-R965N 226dp, 디버그 빌드). JVM (`presetSaveSendsContext`, `deletedPresetFallsBackToFifa`), 에뮬레이터 (`setupShowsPresetRowAndAppliesTeams`, 가짜 링크) |
| 34 | 전송 재시도 (Review Focus 1) | 폰 앱을 (a) 밀어 닫기 / (b) 강제 중지(준비 6)한 채, 폰 블루투스·Wi-Fi를 끄고 워치에서 경기 종료 → 1분 뒤 켜기 | (a) 폰·워치 앱을 열지 않아도 경기가 폰에 저장되고(리스너 서비스) 워치 "휴대폰으로 보내지 않은 경기"가 사라짐, 한 번만. (b) 폰 앱을 열 때까지는 안 오고, 열면 남은 경기 DataItem을 거둬 저장·확인 | JVM (`listenerStoresBundleAndAcks`: 모델 없이 서비스 입구만으로 저장 + 확인, `activationProcessesStoredItems`, `failedTransferResendsImmediately`) |
| 40 | 워치 앱 재설치 후 구독 (Review Focus 3) | 폰을 `pro`로 켜 보낸 뒤 폰 앱을 밀어 닫음 → 워치 앱 지우고 다시 설치 (폰 앱은 열지 않음) | 워치 [규칙]에 프리셋이 돌아오고 홈 [지난 경기]에 자물쇠가 없으며 팀 칸 이벤트 입력이 열림. 폰 설정 "워치 앱"이 설치 안 됨 → 설치됨으로 바뀜 | **실기기 통과** (2026-10-23, 노트10 + SM-R965N) — 워치 앱 지웠다 깔기 → 프리셋·구독이 다시 옴. 단 디버그 `pro`는 폰을 extra로 다시 띄운 뒤(아래 준비 5 주의). JVM (`newInstallRequestsContextOnce`, `contextRequestRepublishes`, `closedAppContextRequestKeepsLastSentEntitlement`: 꺼진 앱이 마지막으로 보낸 구독으로 답함, `reinstallResendsLastContext`) |
| 44 | 프리셋 전송 크기 한도 (Review Focus 4) | 폰에서 팀당 40명(긴 이름) 명단을 붙여넣기로 채운 프리셋 10개 이상을 만들어 보냄 (`CONTEXT_LIMIT` 64KB, DataItem 한도 100KB 안) | 워치가 구독 상태와 들어가는 만큼의 앞쪽 프리셋만 받고 목록에 그것만 보임. 폰 프리셋 목록에 빨간 "뒤쪽 프리셋은 워치에 보내지 않습니다" 안내 | JVM (`oversizedPresetsKeepEntitlement`), 에뮬레이터 (`PresetEditorUITests` 안내 문구) |
| 51 | 받은 경기 확인 정리 (Review Focus 2) | 폰 앱을 밀어 닫은 채 워치에서 경기를 끝냄 → 폰은 받았는데 확인이 안 가게(받은 직후 워치 블루투스 끔) → 몇 분 뒤 켜고 워치 앱을 다시 열기. 폰에서 지운 경기도 한 번 | 워치 로그(`WearSync`)에 받았는지 묻기 → 답, 워치의 보내지 않은 경기가 사라짐. 폰 목록에 같은 경기가 두 번 생기지 않음. 폰에서 지운 경기는 다시 저장되지 않고 확인만 감. 묻기는 60초에 한 번 | JVM (`askReceiptsBeforeResend`, `receiptQueryAnswersStoredAndDeleted`, `deletedMatchStillAcked`, `receiptQueryRepliesWithRequestID`) |
