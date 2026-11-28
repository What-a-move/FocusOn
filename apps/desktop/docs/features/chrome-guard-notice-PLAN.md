# Chrome 제외 사이트·민감 화면 Desktop 안내

- 담당 영역: Frontend (`apps/desktop`, `apps/extension`)
- 우선순위: MVP 기본
- 관련 이슈: [#398](https://github.com/What-a-move/FocusOn/issues/398)
- 브랜치: `codex/398-chrome-guard-notice`
- 디자인: `session-states.html?state=excluded`, `?state=privacy`
- 정책: Chrome Extension 결정 002·014, Desktop 결정 024와 기존 집중 안내

## 동작

- 집중 중 Chrome 전경에서 서버가 확인한 제외 사이트 또는 민감 화면을 기존 포키 말풍선으로 표시한다. 제외 사이트에는 제외 목록 링크를 제공한다.
- 앱·전경 맥락·회차가 바뀌거나 응답이 만료되면 지난 상태를 지운다. 최신 여부를 확인할 수 없는 Chrome 상태에서는 기존 보호 정책의 카메라 보류를 적용한다.
- Extension은 제외·민감 판정을 보내기 직전에도 현재 화면인지 확인한다. 기존 상태 전송에는 URL·제목·본문이 없다.

## 서버 연동 제안

- 기존 `GET /api/v1/sessions/{id}/observation-context`에 `chromeGuard`를 추가하는 안이다. 확정 계약 전까지 기본 dev 서버에서는 Chrome 말풍선을 표시할 수 없다.
- `chromeGuard`: `{status: "CLEAR" | "EXCLUDED_SITE" | "PRIVACY_BLOCKED", remainingMs: number}` 또는 `null`. 반드시 해당 세션·해당 전경 ID·연결된 Extension의 현재 탐색 상태에 묶는다.
- Chrome 내부 페이지·사이트 권한 없음·제외 목록 확인 실패는 사용자 제외 사이트와 구분한다. 새 탭/탐색 시 직전 상태를 무효화해야 한다. 상태 갱신 API의 필드와 순번은 서버와 합의한다.
- 페이지 제목·주소·본문과 기기별 식별 정보는 말풍선에 전달하지 않는다.

## 확인

- 검증된 응답, 누락·만료, 다른 전경 응답, 탭 전환 중 늦은 제외/민감 결과, 말풍선 표시·제거를 자동 검증한다.
- 실제 서버 계약 구현 이후 두 앱 실기기 QA를 묶어 진행한다.
