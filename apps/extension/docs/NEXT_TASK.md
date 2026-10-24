# Extension 다음 작업

> Frontend(`apps`) 담당자는 Extension 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: 온보딩용 일회용 연결 코드 pairing 구현
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 우선순위: 높음
- 상태: 코드 입력·형식 검증, `POST /api/v1/extension-pairings` 제출, 오류 안내, token 저장(결정 011), 연결 완료 화면을 구현했다(2026-10-22). Chromium에 실제로 불러와 테스트용 가짜 서버로 왕복을 확인했다. 남은 일은 실제 Backend로 Desktop 발급 → Extension 입력 → Desktop 연결 반영 QA다. Desktop 온보딩 [Issue #36](https://github.com/What-a-move/FocusOn/issues/36)의 필수 단계다.

## 작업 목적

Desktop에서 발급한 연결 코드를 Extension에 입력해 같은 계정에 연결하고, 연결 성공 결과를 Popup에 표시한다. 연결 코드는 6자리 대문자·숫자, 5분 유효, 최대 3회 실패 정책을 따른다. Extension 연결은 Desktop 온보딩의 필수 단계다.

## 선행 조건

- Extension 기본 화면과 Manifest V3 구조가 실행되어야 한다.
- `POST /api/v1/extension-pairings`의 입력·오류 응답과 Extension token 저장 계약을 확인해야 한다.
- Desktop과 Extension이 refresh token을 공유하지 않는 인증 경계를 지켜야 한다.
- 기능 기획서를 `features/`에 작성해야 한다.

## 예상 작업 순서

1. 완료: 연결 코드 입력 화면과 `ABC-123` 형식 검증 UI를 구현했다.
2. 완료: `POST /api/v1/extension-pairings`에 코드와 Extension installation ID를 전송한다.
3. 완료: 만료·사용 완료·시도 초과·취소·429·네트워크 오류를 표시한다. 틀린 코드에는 `LINK_006_CODE_MISMATCH`와 숫자 `details.remainingAttempts`를 읽어 남은 횟수를 표시한다. 서버의 실패 횟수 귀속 방식은 공통 대조 기록에서 추적한다.
4. 완료: 성공 시 Extension 전용 인증 정보를 결정 011에 따라 저장하고 연결 상태를 표시한다. Extension의 Access Token 갱신(refresh)은 이후 API를 붙일 때 구현한다.
5. Backend API 병합 뒤 Desktop 발급 → Extension 입력 → Desktop 연결 완료 표시까지 실제 왕복을 확인한다.

## 이후 작업 후보

- 현재 Tab 자동 추적과 Service Worker 전달 구조 구현
- 15초 체류 후 페이지 재분석
- 현재 페이지 관련성 분석 요청
- 분석 제외 도메인 설정 UI
- 일회용 연결 코드 기반 Extension pairing (Desktop 온보딩의 필수 연결 단계)
- Desktop 학습 세션 조회 및 타이머 표시
- Desktop 세션 일시정지·재개·종료 명령
- Desktop 연결 상태 표시
- Service Worker 재시작 후 상태 복구

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] 필요한 권한만 Manifest에 선언했다.
- [ ] 정상 탭 변경 흐름을 확인했다.
- [ ] 제외 도메인 흐름을 확인했다.
- [ ] Desktop 연결 실패를 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `EXTENSION_ARCHITECTURE.md`를 확인했다.
- [ ] `MESSAGE_CONTRACT.md`에서 이번 기능에 필요한 Message와 Payload를 확인했다.
- [ ] `MANIFEST_PERMISSION_POLICY.md`의 Host 권한 제안을 사용자와 확정했다.
- [ ] `SERVICE_WORKER_LIFECYCLE.md`의 15초 체류·저장 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 현재 Branch가 Issue와 연결되어 있다.
