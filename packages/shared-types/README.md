# @focuson/shared-types

Desktop, Chrome Extension, Server 사이에서 함께 사용하는 TypeScript 데이터 계약 패키지다.

## 역할

이 패키지는 여러 프로그램이 주고받는 데이터의 이름과 형태를 하나로 맞춘다.

```text
Extension가 보낸 PageInfo
        ↓
Server가 받는 PageInfo
        ↓
AI 분석에 전달할 입력
        ↓
Desktop·Extension에 반환하는 AnalysisResult
```

예를 들어 Extension과 Server가 서로 다른 필드명을 사용하면 연결 과정에서 오류가 생긴다.

```ts
import type { PageInfo } from "@focuson/shared-types";

const page: PageInfo = {
  title: "React 공식 문서",
  url: "https://react.dev",
  text: "React 학습 자료",
};
```

## 현재 타입

- `FocusState`: 분석 결과 상태(`RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`)
- `AnalysisReasonCode`: 분석 판단 근거 코드
- `PageInfo`: 제목·주소·본문 일부 등 페이지 정보
- `AnalysisResult`: 상태·관련성 점수·신뢰도·판단 근거·설명

## 앞으로 추가할 타입

여러 영역에서 함께 사용하는 것이 확정된 경우에만 다음 타입을 추가한다.

- `StudySessionStatus`: `IDLE`, `RUNNING`, `PAUSED`, `COMPLETED`
- `StudySessionSnapshot`: 현재 세션 상태와 기준 시간
- `StudySessionCommand`: 시작·일시정지·재개·종료 명령
- `AnalysisActivityState`: 분석 작동 상태(`RUNNING`, `PAUSED`, `EXCLUDED`). 실제 공유 Type 추가는 세션 API 계약 구현 시 진행한다.
- `DevicePairing`: Desktop과 Extension 연결 상태

## 넣지 않는 것

다음은 이 패키지의 책임이 아니다.

- API 호출
- 데이터베이스 접근
- Google 로그인 처리
- Chrome API 호출
- AI 분석 로직
- Spring Boot의 런타임 입력 검증

TypeScript 타입은 빌드 후 실행 중에는 사라질 수 있으므로, Server는 DTO 검증을 별도로 구현해야 한다.

## 사용 규칙

- 여러 앱이나 서버가 함께 사용하는 타입만 추가한다.
- 특정 화면에만 필요한 Props와 상태는 해당 앱 안에 둔다.
- 타입을 변경하면 Desktop·Extension·Server 소비자를 함께 확인한다.
- API 변경은 `docs/API_CONTRACT.md`에도 반영한다.
