# Desktop 활성 창·로컬 OCR 구현 결과

## 기본 정보

- 기능 ID: screen-acquisition
- 담당 영역: Desktop, 작성일: 2026-10-19
- 기획서: [PLAN](screen-acquisition-PLAN.md)
- Branch: `feat/screen-acquisition`

## 구현 결과

- foreground 앱·focused window 하나를 선택하고, FocusOn·Chrome·제외·민감 대상은 Desktop OCR로 처리하지 않는다.
- APP_META 후 내용 부족 UNCERTAIN만 같은 창을 Apple Vision OCR로 읽는다. ScreenCaptureKit 단일 창 필터로 다른 창·Floating Poki를 제외한다.
- 최신 세션·정책·대상을 전후에 검사하고 정책 철회·만료 시 native subprocess를 취소한다. 원본·본문 파일과 로그·Renderer 원문 API를 만들지 않았다.
- `observedAt`은 반환 완료 시각이 아니라 해당 수집 시작 시각을 유지한다. source별 sequence와 새 clientRunId를 사용한다.

## 변경 파일

| 파일 | 역할 |
| --- | --- |
| `electron/main/screen-acquisition.ts` | Main 수집 단계·정책·native 호출 |
| `electron/shared/screen-acquisition.ts` | 내부 타입, 공개 API 필드 추가 아님 |
| `electron/native/ScreenCapture.m` | AppKit·AX·ScreenCaptureKit·Vision |
| `scripts/build-screen-capture.mjs`, `tsconfig.screen-acquisition.json` | native/TS 빌드 |
| `tests/screen-acquisition.test.mjs` | 합성 단계·제외·변경·취소 검사 |

## 검증 결과

- Node 22.14.0·pnpm 11.19.0·Apple Silicon에서 합성 native 어댑터 테스트 **8개 통과**, TS 검사·Lint·Desktop Next production build 통과.
- Objective-C native helper 컴파일 통과. 실제 화면 캡처·권한 승인·학교 모델 호출은 실행하지 않았다.
- [SDK 오류](screen-acquisition-sdk-ERROR.md), [입출력 API 오류](screen-acquisition-filehandle-ERROR.md)는 해결했다.

## 제한사항과 다음 작업

- Electron Main 부팅·세션/정책 공급·Server/AI 왕복은 후속 3번이다. API 기준을 연결하기 전에는 앱에서 자동 수집이 시작되지 않는다. Renderer에 원문을 돌려주는 우회 연결을 만들지 않는다.
- 앱/브라우저 재시작 때 출처별 sequence를 Server 기준과 복구해야 한다. 현재 Desktop 카운터는 process 메모리에 있다.
- 화면 밖·여러 디스플레이에 걸친 창, 대상/권한 불명은 현재 보류한다. 민감 화면 탐지는 후보 규칙이며 모든 개인 메시지·비밀 정보를 완벽히 식별한다는 보장은 없다.
- 실제 허용·거부·화면 변경과 정확도·속도 평가는 사용자가 보류한 4번이다.
- CONTEXT·NEXT_TASK·DECISION_RECORD를 갱신했다.
