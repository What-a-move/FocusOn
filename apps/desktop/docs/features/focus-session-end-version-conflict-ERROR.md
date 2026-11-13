# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: -
- 오류 식별명: focus-session-end-version-conflict
- 기능명: 집중 세션 끝내기(목표 시간 도달 뒤)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-13
- 작성자: jae1
- 심각도: 보통 (시연 모드에서는 높음: 새 집중 시작 불가)
- 상태: 해결
- 관련 Issue·PR: #256

## 오류 제목

- 목표 시간이 끝난 뒤 첫 `끝내기`가 `SESSION_VERSION_CONFLICT`(409)로 실패하고, 시연 모드에서는 서버에 세션이 남아 새 집중을 시작하지 못함

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple Silicon
- Electron 버전: ^44.1.0
- 서버: 로컬 Docker, dev(`53a4f79`, 활성 세션 `accumulatedAt` #251 포함)
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. 집중을 시작하고 목표 시간(예: 30분)이 끝날 때까지 둔다.
2. "목표한 시간이 지났어요"에서 `세션 종료`(끝내기)를 누른다.
3. (시연 모드 `FOCUSON_DEMO=1`) 홈에서 새 목표로 `집중 시작하기`를 누른다.

## 기대 결과

- 첫 끝내기로 세션이 끝나고 집중 리포트로 넘어간다. 새 집중을 바로 시작할 수 있다.

## 실제 결과

- 일반 실행: 첫 끝내기가 버전 충돌로 실패해 한 번 더 눌러야 끝났다.
- 시연 모드: 실패한 끝내기를 앱 안에서만 끝난 것으로 처리해 리포트로 넘어갔지만, 서버 세션 13은 `AWAITING_END_CONFIRMATION`으로 남았다. 시연 모드가 그 세션을 활성 세션 조회에서 숨겨 집중 화면은 "진행 중인 집중이 없어요", 새 시작은 "진행 중인 집중이 있어요"로 막혔다.

## 오류 메시지

```text
POST /api/v1/sessions/13/end → 409 SESSION_VERSION_CONFLICT
GET  /api/v1/sessions/active → 200 (세션 13, AWAITING_END_CONFIRMATION) → 시연 모드가 null로 바꿈
POST /api/v1/sessions       → 409 SESSION_ACTIVE_EXISTS (2회)
```

## 원인 및 해결

- 추정 원인: 서버 재빌드(#251) 뒤 활성 세션 응답 형식이 바뀌어 Desktop이 세션을 못 읽는다.
- 실제 원인:
  - 서버는 목표 시간에 닿은 heartbeat를 받을 때 상태를 `AWAITING_END_CONFIRMATION`으로 바꾸며 `version`을 1 올린다(`LearningSessionRepository.recordHeartbeat`). heartbeat 응답에는 version이 없어 Desktop은 이전 version으로 `If-Match-Version`을 보내 409가 났다. 터미널 로그의 heartbeat 200 → end 409 순서로 확인했다.
  - 시연 모드(`demo-mode.cts`)는 서버에 종료 API가 없던 때(#164 전)를 위해 종료 실패를 모두 "앱 안에서 끝냄"으로 바꿨다. 409 같은 실제 거절도 끝난 것으로 기록하고, 이후 그 세션을 활성 세션 조회에서 숨겼다.
- 해결 방법:
  - heartbeat 응답이 `AWAITING_END_CONFIRMATION`이면 세션을 한 번 다시 조회해 version을 맞춘다. 실패하면 다음 heartbeat에서 다시 맞춘다. 연장해 다시 세면 표시를 지운다.
  - 시연 모드는 서버 종료 API가 없을 때(404)만 앱 안에서 끝낸다. 그 밖의 실패는 그대로 돌려준다.
- 변경 파일: `electron/main/session-runtime.cts`, `electron/main/demo-mode.cts`, `tests/electron/session-runtime.test.cjs`, `tests/electron/demo-mode.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (실제 서버 5분 세션 16: 목표 도달 heartbeat 뒤 `GET /sessions/16` 다시 조회 1번 → 첫 `더 공부하기`(extend, 같은 `If-Match-Version` 규칙) 200. 수정 전에는 같은 자리에서 끝내기가 409(세션 13))
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (Electron 테스트 192개 통과, 새 테스트 2개는 수정 전 코드에서 실패)

## 재발 방지 및 남은 작업

- 서버가 응답 없이 version을 올리는 다른 경로가 생기면 같은 방식으로 다시 조회해야 한다. 서버 heartbeat 응답에 `version`을 넣으면 다시 조회가 필요 없다(서버팀 제안 후보).
