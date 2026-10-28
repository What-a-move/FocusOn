# @focuson/shared-types

Desktop과 Chrome Extension이 함께 쓰는 **서버 계약 타입**을 모아 둔 패키지다. 기준은 Notion AI 분석 계약·API 명세·세션 상태 전이 정책이다(2026-10-26, Issue #69).

## 사용 규칙

- **타입만 둔다.** 이 패키지는 빌드 없이 TS 원본을 내보낸다. 값(배열·함수·enum)을 넣으면 Desktop Main(`.cjs`)과 Extension `node --test`에서 불러오지 못한다.
- 앱에서는 반드시 `import type`으로 가져온다. 컴파일 때 사라져 실행 코드에 남지 않는다.
- 확인(parse) 함수와 허용 값 목록은 각 앱에 둔다.
- 계약이 바뀌면 Notion → 이 패키지 → 두 앱의 확인 함수 순서로 함께 고친다.

```ts
import type { AnalysisRunRequest, Relation } from '@focuson/shared-types'
```

## 현재 타입

| 타입 | 내용 |
| --- | --- |
| `Relation` | 공개 분석 관계 5개. AI는 `RELATED`·`UNRELATED`·`UNCERTAIN`만 내고, `EXCLUDED`·`PRIVACY_BLOCKED`는 클라이언트가 AI 호출 전에 정한다. AI 내부 `SUPPORTING`은 `RELATED`로 매핑한다. |
| `ContentSource` | `APP_META`, `TAB_META`, `CHROME_DOM`, `CHROME_VIEWPORT_OCR`(P1), `DESKTOP_APP_OCR` |
| `GuardStatus` | `EXCLUDED`, `PRIVACY_BLOCKED` |
| `ReasonCode` | AI 판정 근거 코드 허용 목록 |
| `SessionStatus` | 서버 세션 상태 7개 |
| `AnalysisMetadata`, `AnalysisRunRequest`, `AnalysisRunResponse` | `POST /api/v1/sessions/{sessionId}/analysis-runs` 요청·응답 |

## 넣지 않는 것

API 호출, Chrome·Electron API, 런타임 입력 검증, 특정 화면에만 쓰는 상태. Server는 자체 DTO 검증을 따로 둔다.
