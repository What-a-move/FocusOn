# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 세션 결과 화면 (집중 리포트)
- 기능 ID: D16 세션 완료 · D18 세션 리포트
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-26
- 우선순위: MVP - 핵심
- 관련 Issue: #73
- 예정 Branch: `feat/73-desktop-session-result`
- 관련 Notion·Figma: 집중 기록·학습 정리·리포트 설계서(2·3·4·5.4·7), API `GET /sessions/{id}/summary`·`GET /sessions/{id}/learning-summary`
- 디자인 원본: `FocusOn-design/session-result.html`

## 기능 목적

- 사용자의 문제: 집중을 끝낸 뒤 얼마나 했고, 흐름이 어땠고, 무엇을 정리할지 한눈에 보고 싶다.
- Desktop에서 제공할 가치: 점수 없이 실제 시간·흐름·멈춤·알림과 학습 정리 상태를 보여 준다.

## 사용자 흐름

1. 집중 화면에서 끝내기·시간 종료·자동 종료로 세션이 `ENDED`·`ABANDONED`가 되면 `/session-result/?session={id}`로 이동한다.
2. 위: 포키·`이번 집중을 마쳤어요`·목표·실제 시간(분·초)·계획(연장 포함)·1분 이상이면 달력 도장.
3. 왼쪽: 이번 시간의 흐름(목표 활동·다른 활동·판단 보류·분석 안 됨, 목표 활동 비율), 학습 정리 상태. 오른쪽: 기록(시간·멈춤·자동 멈춤·이탈 알림).
4. 학습 정리가 `QUEUED`·`GENERATING`이면 5초마다 다시 조회하고, 끝나면 멈춘다.
5. `홈으로`.

## Desktop 상세 동작

- IPC: `report:session-summary:get { sessionId }`, `report:learning-summary:get { sessionId }` → Main `ReportService`가 각 API를 조회한다.
- 요약 응답 형식은 Notion에 필드 일부만 있다. 설계서 3.1의 이름(`goalSnapshot`, `targetDurationMs`, `accumulatedActiveMs`, `totalPausedMs`, `relatedDurationMs`, `unrelatedDurationMs`, `uncertainDurationMs`)은 그대로 쓰고, 나머지는 제안한다(백엔드 확인 필요): `status`, `startedAt`, `endedAt`, `extensionCount`, `extendedDurationMs`, `pauseCount`, `autoPauseCount`, `autoPauseReasons[]`, `interventionCount`. 제안 필드가 없으면 `기록 없음`으로 보인다.
- 분석 안 됨 = `accumulatedActiveMs − (related + unrelated + uncertain)`(0 미만이면 0). 목표 활동 비율 = related ÷ (related + unrelated), 합이 1분 미만이면 비율 대신 안내 문구(설계서 3.2).
- 학습 정리: `learningSummaryStatus`별 문구(시안 기준 존댓말). `READY`면 `overview`를 함께 보여 준다. 학습 정리 상세 화면(`study-review`)은 아직 없어 `정리 보기` 버튼은 넣지 않는다.
- 세션이 없거나(404) 주소에 세션이 없으면 `기록을 찾을 수 없어요`, 조회 실패면 다시 시도 안내.
- 시안의 `예시 데이터` 태그·`?state=` 미리보기 링크·`집중 기록` 버튼(화면 없음)은 넣지 않는다.

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현
- [x] 예외 상황 확인 (없는 세션·조회 실패·분석 부족·정리 상태 5종)
- [ ] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [x] 테스트 완료
- [x] 디자인 확인(사용자) 후 PR
- [ ] `*-REPORT.md` 작성 (백엔드 병합 뒤)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신
