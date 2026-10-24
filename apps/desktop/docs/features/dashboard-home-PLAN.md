# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 홈 대시보드 화면 구조
- 기능 ID: D05 목표 없는 홈 · D08 홈 대시보드
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-24
- 우선순위: MVP - 핵심
- 관련 Issue: #41
- 예정 Branch: `feat/41-desktop-dashboard`
- 관련 Notion·Figma: Figma 가이드 D05·D08, `집중 기록 · 학습 정리 · 리포트` 3.4(홈 집중 달력), `화면 구조 · 정보 구조`

## 기능 목적

- 사용자의 문제: 온보딩을 마친 뒤 무엇을 해야 할지, 최근에 얼마나 공부했는지 한눈에 알기 어렵다.
- Desktop에서 제공할 가치: 홈에서 바로 오늘 목표를 시작하고, 집중 달력과 지난 목표로 학습 흔적을 가볍게 돌아본다.

## 사용자 흐름

1. 로그인 → 온보딩 완료(또는 `COMPLETED` 사용자 재실행) → 홈(`/`)
2. 상단에서 오늘 날짜·인사·오늘 집중 N분·이번 달 N일을 본다.
3. `오늘 학습` 영역의 `목표 설정하기`를 누른다(목표 설정 화면은 다음 Issue).
4. 집중 달력에서 월을 바꾸거나 날짜를 누른다(일간 리포트 화면은 이후 Issue, 이번에는 이동 자리만).
5. 지난 목표 최근 3개를 보거나 `전체 보기`로 집중 기록으로 간다(이후 Issue).

## Desktop 상세 동작

Electron Process·Window·IPC·권한의 확정 기본값은 `ELECTRON_ARCHITECTURE.md`, `WINDOW_LIFECYCLE.md`, `IPC_CONTRACT.md`, `MACOS_PERMISSION_FLOW.md`를 따른다. 변경하려면 먼저 `DECISION_RECORD.md`를 갱신한다.

- 진입 조건: 로그인 상태이고 `onboardingStatus = COMPLETED`.
- 정상 동작: 사이드바(홈·집중 시작·집중 기록·학습 노트·설정, 프로필)를 고정하고 내용 영역만 바뀐다. 홈은 `main.html`의 상단 요약 → `오늘 학습` → 집중 달력·지난 목표 2열 구성을 따른다.
- 종료 조건: 다른 메뉴로 이동하거나 로그아웃.
- 상태 변화: 세션 상태는 다루지 않는다. 진행 중 세션은 달력·오늘 합계에 확정값으로 넣지 않는다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 없음
- 권한 요청 시점: 해당 없음
- 권한 허용 시: 해당 없음
- 권한 거부 시: 필수 권한 상실 안내는 세션 시작 흐름에서 처리한다(이번 범위 아님).
- 활성 앱 또는 Bundle Identifier 처리: 해당 없음

## 화면 및 사용자 경험

- 화면 위치: `apps/desktop/app/page.tsx`의 임시 `HomeScreen`을 `features/dashboard/`로 교체
- 주요 컴포넌트: `AppShell`(사이드바·프로필 = 포키 얼굴 + 닉네임), `HomeTopbar`, `TodayHero`, `FocusCalendar`, `RecentGoals`
- 디자인 원본: `FocusOn-design/main.html`, `fo-base.css`, `assets/`. 시안의 `예시` 미리보기 데이터·`preview-tag`는 제품에 넣지 않는다.
- 달력 규칙: 월~일 7열, 오늘 강조와 포키 표시는 분리, 같은 `studyDate`의 종료 확정 합계 `totalActiveMs >= 60000`(서버가 준 `hasStudyActivity`)이면 포키 얼굴, 미래 날짜에는 표시하지 않음
- 문구: 시스템 안내는 존댓말(`아직 기록이 없어요`, `기록을 불러오지 못했어요. 다시 시도해 주세요.`)
- 로딩·빈 상태·오류 상태: 달력 뼈대 로딩, 빈 달, 조회 오류 + `다시 시도`, 오프라인은 마지막 성공 조회 시각 표시. 과거 캐시를 현재 값처럼 단정하지 않는다.
- 다크 모드·반응형: `prefers-color-scheme`, `fo-base.css` 토큰, 창 크기에 따라 2열 → 1열

## 데이터 및 IPC/API

- Renderer 입력: 조회할 월(`YYYY-MM`)
- Main 또는 Preload 전달값: `window.focusOn.reports.getCalendar({ month })`, 최근 목표 조회. 모두 Main에서 `AuthService.authorizedRequest`를 거친다(GET 재시도 규칙 동일).
- Server·AI 요청 여부: `GET /api/v1/reports/calendar?month=YYYY-MM` → `{ month, studyDayCount, days[{ date, totalActiveMs, sessionCount, hasStudyActivity }] }`. 지난 목표·오늘 합계는 일간 리포트 API 기준(계약 확인 후 연결).
- 저장 데이터: 없음(메모리 캐시만, 세션 종료 시 해당 월 무효화는 세션 Issue에서)
- 분석 제외 대상: 해당 없음

```json
{
  "month": "2026-10",
  "studyDayCount": 0,
  "days": [{ "date": "2026-10-24", "totalActiveMs": 0, "sessionCount": 0, "hasStudyActivity": false }]
}
```

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| API 미구현·서버 없음 | `기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.` | 오류 상태 + 다시 시도. 빈 값으로 성공 위장 금지 |
| 네트워크 단절 | `인터넷 연결을 확인해 주세요.` + 마지막 갱신 시각 | GET 재시도 후 오류 표시 |
| 기록 없음 | `아직 기록이 없어요` / `첫 집중을 마치면 여기에 나타나요.` | 빈 상태 |
| 재로그인 필요 | 로그인 화면 | SIGNED_OUT → `/` 로그인 |
| 분석 제외 앱·도메인 | 해당 없음 | 해당 없음 |

## 보안·개인정보

- 카메라 원본 저장 여부: 해당 없음
- 화면 원본 저장 여부: 해당 없음
- 메모리에서 데이터가 유지되는 시간: 화면이 열려 있는 동안 월별 집계만
- Server로 전송하는 최소 데이터: 조회 월

## 완료 조건

- [ ] 기획 내용 확인
- [ ] 정상 흐름 구현 (`main.html` 레이아웃·색·간격·버튼·포키·반응형 일치)
- [ ] 권한 거부 흐름 구현 (해당 없음)
- [ ] 예외 상황 확인 (API 없음·오프라인·빈 상태)
- [ ] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [ ] 테스트 완료
- [ ] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성
- [ ] `*-REPORT.md` 작성
- [ ] `CONTEXT.md`, `NEXT_TASK.md` 갱신
