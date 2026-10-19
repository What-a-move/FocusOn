# Desktop 다음 작업

## 2026-10-19 화면 수집 모듈 후속 연결

- foreground/focused window·단일 창 캡처·Apple Vision OCR 모듈은 구현했다. `pnpm --filter @focuson/desktop build:screen`으로 native helper를 빌드한다.
- 실제 Main 진입점에서 `DesktopScreenAcquisition`을 생성하고 Server 검증 세션·제외 정책을 `setContext`로 공급한다. Renderer에서 수집 정책이나 원문을 직접 다루지 않는다.
- 유효 정책은 최대 60초의 로컬 lease로 제한한다. 만료·중단·제외·개인정보 상태 변경은 진행 중인 native 작업도 취소한다. 이 lease는 로컬 안전장치이며 새 공개 API 컬럼이 아니다.
- 메타데이터와 추출 텍스트를 공통 Server→AI 계약으로 매핑하고 결과의 식별자를 확인한 뒤 `collectNext`를 호출하는 것이 사용자 요청의 후속 3번이다. App 재시작/세션 복구 때 출처별 sequence 기준도 Server와 동기화한다.
- 실제 화면·학교 모델의 정확도·속도 검증은 사용자가 보류한 4번으로 남긴다.

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 현재 우선 작업

- 작업명: 화면 분석 수집기를 Server 세션 정책과 연결
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 우선순위: 높음
- 상태: 예정

## 작업 목적

구현된 수집 모듈을 실제 Desktop Main 진입점에 연결하고, Server가 확인한 RUNNING 세션과 제외 정책이 유효할 때만 화면을 분석한다.

## 예상 작업 순서

1. Electron Main 진입점과 권한 흐름에서 `DesktopScreenAcquisition`을 생성한다.
2. Server의 세션·제외 정책을 `setContext`에 전달하고 만료·중단 시 수집을 취소한다.
3. 결과를 공통 Server→AI 계약으로 전달하고 결과 식별자를 검증한다.
4. macOS 권한 허용·거부, 창 전환, 중단·제외 정책 흐름을 실제 앱에서 확인한다.

## 이후 작업 후보

- 학습 세션 타이머
- 화면 부유형 두더지 마스코트 피드백 및 표시 모드 설정
- 일회용 연결 코드 기반 Chrome Extension pairing
- Desktop·Extension 학습 세션 상태 동기화
- macOS 활성 앱과 Bundle Identifier 확인
- 분석 제외 앱 설정
- 화면 캡처 권한과 캡처 중지
- MediaPipe 카메라 권한 및 상태 연결

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] Electron 영역별 책임을 문서화했다.
- [ ] Server 세션 정책이 유효할 때만 수집한다.
- [ ] 수집 취소와 macOS 권한 거부를 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `ELECTRON_ARCHITECTURE.md`를 확인했다.
- [ ] `IPC_CONTRACT.md`에서 이번 기능에 필요한 Channel과 Payload를 확인했다.
- [ ] `MACOS_PERMISSION_FLOW.md`와 `WINDOW_LIFECYCLE.md`의 확정 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 관련 Issue와 Branch가 있다.
