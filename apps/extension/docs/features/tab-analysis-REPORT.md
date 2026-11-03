# Extension 기능 결과 리포트

## 기본 정보

- 기능명: Chrome 탭·페이지 분석 (화면 분석 3단계)
- 기능 ID: E01b·E06, Service Worker 탭 분석
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 작성자: jae1
- 작성일: 2026-11-03
- 관련 기획서: `tab-analysis-PLAN.md`
- 관련 Issue·PR: #61(#62)
- 관련 Branch: `feat/61-extension-tab-analysis`

## 구현 결과

- 기획한 내용 중 구현한 범위: `optional_host_permissions` 사이트 접근(E01b·E06), 15초 체류(Worker 타이머 + 저장된 시작 시각 + 30초 알람), 제외 도메인·Chrome 내부·민감 페이지 판정, `TAB_META` → 관련 없음·판단 보류면 `CHROME_DOM`, 늦은 응답 버림, 권한 철회 `local-events`
- 기획과 달라진 내용: 없음
- 구현하지 못한 내용: 같은 페이지 안 내용 변경(스크롤) 30초 재전송은 하지 않았다(기존 기록 그대로).

## 변경 파일

| 파일 경로 | 변경 내용 |
| --- | --- |
| `extension/background/index.ts`·`tab-analyzer.ts` | 탭 추적·판정·전송 |
| `extension/content/inspect-page.ts`, `extension/shared/page-rules.ts` | 본문 추출·민감 판정·글자 정리 |
| `features/session/SiteAccessStep.tsx` | E01b·E06 |
| `tests/tab-analyzer.test.ts`·`page-rules.test.ts` | 단위 테스트 |

(전체 목록은 #62)

## 사용자 흐름 테스트

Chrome for Testing에 빌드 결과물을 올리고 실제 Desktop·로컬 서버로 집중을 시작해 확인했다. 서버에 아직 없는 `analysis-runs`만 QA 중계 서버가 가짜로 답했다(탭 정보는 항상 `UNRELATED`로 답해 본문 단계까지 확인). 중계 서버는 길이·도메인만 기록하고 제목·본문은 남기지 않았다. QA 뒤 중계 서버를 끄고 확장을 정상 주소로 다시 빌드했으며, 시험용 제외 사이트도 지웠다.

- [x] Extension 설치: 오류 없이 로드, Service Worker 시작(개발용 ID `fbhlmioghblalhmcokcpoapjpnjccbde`)
- [x] Popup 실행: 연결 → E01b → 권한 허용 → 세션 화면 (#52 리포트)
- [x] 현재 탭 확인: react.dev에 15초 → `TAB_META`(제목 22자·도메인, 기기 ID 헤더) → `UNRELATED` → `CHROME_DOM`(본문 2,000자, 상한)
- [x] 페이지 이동 기록: 탭을 바꿀 때마다 15초를 새로 셈
- [x] 15초 체류 재분석·Service Worker 재시작: 체류 중 Worker를 강제로 끄면 30초 알람으로 다시 깨어나고(`sw.restart`) 체류 기록이 남아 있음. 그때 Chrome이 맨 앞 앱이 아니어서 규칙대로 건너뜀. 맨 앞일 때 이어서 보내는 동작은 단위 테스트로 확인
- [x] 제외 도메인: `youtube.com`을 Desktop 설정에서 제외 → `TAB_META` `guardStatus: EXCLUDED`만, 제목·도메인·본문 없음
- [x] 민감 화면: GitHub 로그인(비밀번호 칸) → `PRIVACY_BLOCKED`만, 내용 없음
- [x] 권한 거부·꺼짐: E01b·E06 화면 (#52 리포트). 꺼짐 시 서버 요청이 CORS로 막히는 문제는 아래
- [ ] Desktop 연결 실패: #52 리포트(연결 해제)

## 테스트 환경

- macOS: 26.6.1
- Chrome: Chrome for Testing 153.0.8010.12
- Extension: 0.1.0 (빌드 결과물)
- 테스트 일시: 2026-11-03

## 오류 기록

- 관련 `기능명-오류명-ERROR.md` 목록: `extension-host-permission-cors-ERROR.md`, `extension-dev-stale-service-worker-ERROR.md`
- 해결된 오류: 다시 빌드한 뒤 Service Worker가 예전 코드로 동작(QA 절차로 해결)
- 남은 오류: 사이트 접근을 끄면 서버 요청이 CORS로 막힘 → Backend #151

## 성능 및 제한사항

- Content Script 영향: 정적 content script 없음, 판정할 때만 `scripting.executeScript`
- Service Worker 제한: 알람 최소 30초라 Worker가 꺼지면 최대 30초 늦게 판정
- 페이지별 접근 제한: 사이트 접근 허용 페이지만 본문을 읽음, Chrome이 맨 앞일 때만 분석
- 네트워크 끊김 처리: 다시 보내거나 쌓지 않음
- 개인정보 처리 제한: 제외·민감 화면은 상태만, 본문 최대 2,000자, 로그는 글자 수만

## 다음 작업

- 서버 `analysis-runs` 병합 뒤 중계 서버 없이 연동 QA
- Backend CORS(#151) 반영 뒤 사이트 접근 끄기 → `local-events` 자동 멈춤 확인

## 문서 갱신 확인

- [x] `CONTEXT.md` 갱신 필요 여부 확인 (NEXT_TASK에 기록)
- [x] `NEXT_TASK.md` 갱신
- [x] `DECISION_RECORD.md` 갱신 필요 여부 확인 (변경 없음)
- [x] PR에 이 리포트 연결
