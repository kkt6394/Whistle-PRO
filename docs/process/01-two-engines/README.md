# 01. 두 플랫폼 엔진 맞추기

**무엇을:** Apple Watch용으로 먼저 만든 Swift 경기 엔진을 Wear OS용 Kotlin으로 옮겼다.

**왜 이렇게:** 엔진 코드를 공유하지 않고(Kotlin Multiplatform을 쓰지 않고) 플랫폼마다 따로 둔 대신, 두 엔진이 같은 판정을 내리는지 **공용 JSON 시나리오**로 검사한다. 시나리오 하나에 "이 순서로 명령을 넣으면 이 시각에 상태가 이렇다"를 적고, Swift 테스트와 Kotlin 테스트가 같은 파일을 읽는다. Swift가 저장한 경기 파일을 Kotlin이 그대로 읽는지도 픽스처로 확인한다.

**순서:**
1. [design.md](design.md) — 상위 설계의 아키텍처·경기 엔진 부분. 엔진은 상태가 아니라 "일어난 일"을 저장하고 상태는 계산한다는 원칙, 규칙 설정, 공용 시나리오 형식.
2. [plan.md](plan.md) — Kotlin 엔진 작업 계획. 작업마다 옮길 타입과 통과시킬 시나리오가 정해져 있다.
3. 점검 — [`shared/engine-scenarios/`](../../../shared/engine-scenarios/)의 시나리오를 두 플랫폼 CI가 매번 돌린다.
