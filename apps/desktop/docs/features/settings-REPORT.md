# Desktop 기능 결과 리포트

## 기본 정보

- 기능명: 설정 화면 (집중·분석·권한·화면)
- 기능 ID: D20·D22·D23
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-31
- 관련 기획서: `settings-PLAN.md`
- 관련 Issue·PR: #80, #88(구현), #108(QA 중 발견한 Extension 빌드 수정)
- 관련 Branch: `feat/80-desktop-settings`

## 구현 결과

- 기획한 내용 중 구현한 범위: 집중 설정(개입 강도·알림 채널·휴식 제안) 서버 저장, 마지막 알림 채널 끄기 차단, 분석 제외 사이트 서버 전체 교체(주소 → 도메인 정리, 삭제 되돌리기), 제외 앱 기기 저장, 권한 상태 표시, 화면 모드(라이트·다크·시스템) 기기 저장.
- 기획과 달라진 내용: 없음.
- 구현하지 못한 내용: 계정·기기 연결 탭(D24·D25)은 다음 Issue.

## 변경 파일

| 파일 경로 | 변경 내용 |
| --- | --- |
| `features/settings/Settings.tsx`·`Exclusions.tsx`·`SettingsShell.tsx`·`settings-api.ts`·`settings.css` | 설정 화면과 분석 제외 목록 |
| `electron/main/settings-ipc.cts`, `electron/shared/settings-contract.cts` | focus-settings·제외 도메인 요청, 화면 모드·제외 앱 기기 저장 |
| `features/dashboard/AppSidebar.tsx` | 홈·설정 사이드바 링크 |
| `tests/electron/settings-service.test.cjs` | 요청 형식·응답 검증·화면 모드 저장 테스트 |

(전체 목록은 #88)

## 사용자 흐름 테스트

실제 Google 계정 + 로컬 Docker 서버(dev 최신, 2026-10-30 빌드)로 확인했다. 요청 결과는 Desktop 로그(`focuson.log`)와 서버 DB로 대조했다.

- [x] 앱 실행
- [x] 정상 흐름
  - 집중 설정을 4번 바꿔 `PUT focus-settings` 4번 모두 200. DB 최종값 `NORMAL` · `MOLE_UI` · 휴식 제안 켜짐, `updated_at`이 마지막 요청 시각과 일치
  - 알림 채널이 하나 남은 뒤에는 끄는 요청이 나가지 않음(화면에서 차단)
  - 제외 사이트 추가·삭제·되돌리기에서 `PUT analysis-excluded-domains` 5번 모두 200. `https://www.netflix.com/browse` → DB `netflix.com`
- [ ] 일시정지·재개·종료 (설정 화면 범위 아님)
- [x] 권한 허용: 화면 기록 허용 뒤 앱 재실행 → 허용됨 표시
- [ ] 권한 거부: 미확인
- [x] 분석 제외 앱: 추가 뒤 재실행해도 유지
- [x] 앱 재실행: 다크 모드·제외 앱·집중 설정·제외 사이트 모두 유지
- [x] 서버 끊김(조회): 서버를 끈 상태로 설정을 열면 GET이 `LOCAL_NETWORK_UNAVAILABLE`로 실패하고 1초·3초 재시도(같은 traceId), DB 값 변화 없음
- [ ] 서버 끊김(저장 중): 화면을 연 뒤 서버를 끄고 값을 바꿔 이전 값으로 되돌아가는지 **미확인**. 이번에는 서버를 먼저 꺼서 조회 단계에서 실패했다.
- [ ] 시험 알림: 개발 실행(서명 없는 Electron)에서는 macOS가 알림을 거절해 확인 불가(`settings-dev-notification-unsigned-ERROR.md`)

## 테스트 환경

- macOS: 26.6.1
- Mac 모델·칩: Apple M4
- Electron: 44.1.0 (개발 실행, `open -a`로 실행)
- 테스트 일시: 2026-10-30 ~ 2026-10-31

## 오류 기록

- 관련 `기능명-오류명-ERROR.md` 목록: `settings-dev-launch-ERROR.md`, `settings-dev-notification-unsigned-ERROR.md`, `settings-missing-api-relogin-ERROR.md`, Extension `extension-build-reserved-filename-ERROR.md`
- 해결된 오류: 개발 실행 환경 변수·화면 기록 권한(실행 방법 정리), Extension 로딩(#108)
- 남은 오류:
  - 서버에 없는 API가 401 `AUTH_RELOGIN_REQUIRED`로 응답해 홈에서 로그아웃됨 → 백엔드에 요청함. QA는 커밋하지 않은 임시 수정으로 진행
  - 시험 알림이 실패해도 화면에는 보냄으로 처리됨 → 오류 문구 개선 작업에서 처리

## 성능 및 제한사항

- CPU·메모리 확인: 미측정
- 화면 캡처 제한: 해당 없음
- 카메라 분석 제한: 해당 없음
- 네트워크 끊김 처리: 조회 실패·재시도 확인. 저장 중 끊김 되돌리기는 미확인

## 다음 작업

- 저장 중 서버 끊김 되돌리기 실제 확인 뒤 Issue #80 닫기
- 오류 문구 개선(애매한 문구, 로그인 진행 중 빨간색, 시험 알림 실패 처리)
- 설정 계정·기기 연결 탭(D24·D25)

## 문서 갱신 확인

- [x] `CONTEXT.md` 갱신
- [x] `NEXT_TASK.md` 갱신
- [x] `DECISION_RECORD.md` 갱신 필요 여부 확인 (결정 변경 없음)
- [x] PR에 이 리포트 연결
