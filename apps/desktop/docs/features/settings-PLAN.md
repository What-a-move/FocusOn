# Desktop 기능 기획서

## 기본 정보

- 기능명: 설정 화면 (집중·분석·권한·화면)
- 기능 ID: D20·D22·D23 (계정 D25·기기 연결 D24는 다음 Issue)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-27
- 우선순위: MVP - 핵심
- 관련 Issue: #80
- 예정 Branch: `feat/80-desktop-settings`
- 관련 Notion·Figma: `FocusOn-design/settings.html`, `FocusOn-design/exclusions.html`, Notion API `GET`·`PUT /api/v1/users/me/focus-settings`(READY), `GET`·`PUT /api/v1/users/me/analysis-excluded-domains`

## 기능 목적

- 사용자의 문제: 온보딩에서 정한 제외 사이트·앱과 포키 알림 방식을 나중에 바꿀 곳이 없다. 권한이 꺼졌을 때 어디서 확인해야 하는지도 모른다.
- Desktop에서 제공할 가치: 홈 사이드바 `설정`에서 기본 집중 설정, 권한 상태, 분석 제외 목록, 화면 모드를 한곳에서 바꾼다.

## 사용자 흐름

1. 홈 사이드바에서 `설정`을 누르면 `/settings/`로 이동한다. 사이드바는 그대로 남는다.
2. 탭 `집중` · `분석·권한` · `화면` 중 하나를 고른다. 고른 탭은 주소 `#focus`·`#analysis`·`#general`로 남는다.
3. 집중 탭에서 포키가 알려주는 정도·알림 방식·휴식 제안을 바꾸면 바로 서버에 저장하고 `저장했어요`를 보여 준다.
4. 분석·권한 탭에서 화면 기록·알림 상태를 보고, 꺼져 있으면 시스템 설정을 연 뒤 `다시 확인`한다. `분석 제외 목록 › 관리`로 `/settings/exclusions/`에 들어간다.
5. 제외 목록에서 사이트를 추가·풀기하면 서버 전체 목록을, 앱을 추가·풀기하면 이 기기 목록을 바로 저장한다. `← 설정`으로 돌아간다.
6. 화면 탭에서 라이트·다크·macOS 설정 따르기를 고르면 앱 전체에 바로 적용되고 다음 실행에도 유지된다.

## Desktop 상세 동작

- 진입 조건: 로그인과 온보딩을 마친 사용자. 홈 사이드바의 `설정` 링크.
- 정상 동작:
  - 집중 설정은 화면을 열 때 `GET focus-settings`로 읽고, 바꿀 때마다 `PUT`으로 전체 객체(`interventionLevel`, `notificationChannels`, `cameraAnalysisEnabled`, `breakSuggestionEnabled`)를 보낸다. `cameraAnalysisEnabled`는 읽은 값을 그대로 돌려보낸다.
  - 알림 방식은 1개 이상이어야 한다. 마지막 하나는 끌 수 없다.
  - 저장 중에는 같은 묶음 조작을 막는다. 실패하면 이전 값으로 되돌리고 오류를 보여 준다.
  - 제외 사이트는 `PUT analysis-excluded-domains`(전체 교체, `X-Device-Id`)로 저장한다. 온보딩과 달리 `onboarding-readiness`는 건드리지 않는다.
  - 제외 앱은 기존 `analysis:excluded-apps:save`(이 기기 `userData/excluded-apps.json`)를 쓴다.
  - 화면 모드는 Main이 `nativeTheme.themeSource`를 바꾸고 `userData`에 저장한다. 앱 시작 때 저장값을 먼저 적용한다.
- 종료 조건: 사이드바 다른 메뉴나 홈으로 이동.
- 상태 변화: 세션 상태를 바꾸지 않는다. 진행 중 세션에는 바뀐 집중 설정이 적용되지 않는다(세션 시작 때 snapshot). 제외 목록은 다음 관측부터 반영된다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 새 권한 요청 없음. 화면 기록·알림 상태를 보여 준다.
- 권한 요청 시점: 사용자가 `시스템 설정 열기`를 누를 때만 시스템 설정을 연다.
- 권한 허용 시: `다시 확인`을 누르면 허용으로 바뀐다.
- 권한 거부 시: `필수 권한이 꺼져 있어 집중을 시작할 수 없어요.` 안내. 다시 확인해도 꺼져 있으면 `아직 꺼져 있어요. 켰다면 앱을 다시 열어 주세요.`
- 알림은 macOS 상태를 직접 읽지 못하므로 기존 방식(시험 알림 → 사용자 확인 기록, 결정 018)을 따른다.
- 활성 앱 또는 Bundle Identifier 처리: 제외 앱 고르기는 기존 설치 앱 목록 IPC를 쓴다.

## 화면 및 사용자 경험

- 화면 위치: `/settings/`, `/settings/exclusions/`. 홈과 같은 사이드바.
- 주요 컴포넌트: `features/settings/Settings.tsx`(탭·집중·분석·권한·화면), `features/settings/Exclusions.tsx`(사이트·앱 탭), 홈에서 꺼낸 공통 사이드바.
- 시안과 다른 점 (합의된 계약 우선):
  - 시안의 `기본 집중 시간`은 focus-settings에 필드가 없어 넣지 않는다(2026-10-27 사용자 결정).
  - 시안의 `계정`·`기기 연결` 탭은 다음 Issue에서 만든다. 이번에는 탭 자체를 넣지 않는다.
  - 권한 목록의 `Chrome 페이지 분석`은 Extension 사이트 권한이라 Desktop이 확인할 수 없고, `활성 앱 확인`은 macOS 권한이 필요 없다. 그래서 `화면 기록`·`알림` 두 줄만 보여 준다.
  - `카메라 분석` 스위치는 카메라 분석이 아직 구현되지 않아 넣지 않는다. 값은 서버 값을 그대로 유지한다.
  - 제외 앱 고르기는 시안의 예시 목록 대신 실제 설치 앱 목록을 쓴다(Issue #54와 같음).
- 알림 문구(시스템 문구, 존댓말): `저장했어요`, `저장하지 못했어요. 잠시 후 다시 시도해 주세요.`, `알림 방식은 하나 이상 필요해요`, `제외를 풀었어요 · {이름}` + `되돌리기`.
- 로딩·빈 상태·오류 상태: 집중 설정을 읽는 중에는 조작을 막고, 읽기에 실패하면 `다시 시도`를 보여 준다. 서버 값이 없을 때 기본값으로 저장하지 않는다. 제외 사이트도 같다. 빈 목록은 포키와 `아직 제외한 사이트가 없어요`.

## 데이터 및 IPC/API

| Channel | 입력 | 결과 | Main 처리 |
| --- | --- | --- | --- |
| `session:focus-settings:get` (기존) | `void` | `FocusSettings` + `breakSuggestionEnabled` | `GET /api/v1/users/me/focus-settings` |
| `settings:focus-settings:save` | `FocusSettings` 4개 필드 | 저장된 전체 값 | `PUT /api/v1/users/me/focus-settings` |
| `settings:excluded-domains:get` | `void` | `{ domains }` | `GET /api/v1/users/me/analysis-excluded-domains` |
| `settings:excluded-domains:save` | `{ domains }` (최대 50개) | `{ domains }` | `PUT /api/v1/users/me/analysis-excluded-domains` |
| `app:theme:get` / `app:theme:set` | `void` / `{ theme: light·dark·system }` | `{ theme }` | `nativeTheme.themeSource` + `userData/preferences.json` |

```json
{
  "interventionLevel": "NORMAL",
  "notificationChannels": ["MOLE_UI", "MACOS_NOTIFICATION"],
  "cameraAnalysisEnabled": false,
  "breakSuggestionEnabled": true
}
```

- Server·AI 요청 여부: Server만. AI 없음.
- 저장 데이터: 서버(집중 설정·제외 도메인), 이 기기(제외 앱·화면 모드).
- 세션 시작 `sessionOptions`에는 `breakSuggestionEnabled`를 넣지 않는다(API_CONTRACT).

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 권한 거부 | 필수 권한이 꺼져 있다는 안내와 `시스템 설정 열기` → `다시 확인` | 상태만 다시 읽는다 |
| 서버 연결 실패·오프라인 | `저장하지 못했어요` / 읽기 실패 시 `다시 시도` | 값을 되돌리고 성공처럼 보이지 않게 한다 |
| 400 VALIDATION_ERROR | `저장하지 못했어요` | 되돌린다 |
| 401 재로그인 필요 | 기존 인증 흐름(로그인 화면) | `authorizedRequest`가 처리 |
| 분석 제외 앱 | 이 기기에만 저장된다는 설명 | 로컬 파일 |
| 분석 제외 도메인 | 형식 오류·중복·50개 초과 안내 | 온보딩과 같은 정규화·검증 |

## 보안·개인정보

- 카메라 원본 저장 여부: 해당 없음
- 화면 원본 저장 여부: 해당 없음
- 메모리에서 데이터가 유지되는 시간: 화면이 열려 있는 동안
- Server로 전송하는 최소 데이터: 집중 설정 4개 값, 정규화한 도메인 목록. 제외 앱은 보내지 않는다.

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현 (사이드바 이동, 탭 3개, 집중 설정 저장, 제외 사이트·앱 관리, 화면 모드)
- [x] 권한 거부 흐름 구현 (화면 기록·알림)
- [x] 예외 상황 확인 (읽기·저장 실패 되돌리기, 알림 방식 최소 1개)
- [x] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인 (탭 좌우 화살표, radiogroup·switch)
- [x] 테스트 완료
- [x] 실제 오류가 발생했다면 오류별 `settings-오류명-ERROR.md` 작성
- [ ] `settings-REPORT.md` 작성 (focus-settings API 병합 뒤 실제 QA)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신
