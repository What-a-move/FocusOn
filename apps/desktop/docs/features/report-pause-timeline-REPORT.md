# Desktop 집중 리포트 일시정지 구간 표시 결과

## 기본 정보

- 기능: [#370](https://github.com/What-a-move/FocusOn/issues/370)
- 담당 영역: Frontend, Desktop
- 기획: [일시정지 구간 기획](report-pause-timeline-PLAN.md)
- 서버 계약: [#356](https://github.com/What-a-move/FocusOn/pull/356)

## 구현 결과

- 집중 시간 축에 실제 멈춘 지점을 표시하고, 목록에 멈춤·재개 시각을 보여 준다.
- 재개 없이 끝난 구간은 종료 시각까지 표기한다.
- 조회 실패 시 기존 리포트에서 일시정지 구간만 다시 요청할 수 있다.

## 변경 파일

| 위치 | 변경 내용 |
| --- | --- |
| `features/report/` | 집중 시간 축, 실제 시각 목록, 조회 실패 상태 |
| `electron/main/report-ipc.cts` 및 공용 리포트 계약 | 인증된 구간 조회와 응답 검증 |
| `tests/electron/report-service.test.cjs` | 빈 목록, 순서, 열린 마지막 구간, 요청 경로 검증 |

## 사용자 흐름 테스트

- [x] 실제 macOS QA 앱 `6e3b9cd`에서 멈춤 → 재개 → 종료, 멈춘 채 종료, 기록 없는 회차, 조회 실패와 재시도를 사용자 확인했다.
- [x] Desktop 빌드와 리포트 자동 테스트 11개 통과.
- [x] 로컬 QA 서버의 구간 조회 200과 재개 시각 저장 확인.

## 테스트 환경과 제한

- 로컬 macOS arm64, QA 백엔드와 복제 개발 DB.
- 구간 기록 기능이 도입되기 전 회차는 빈 구간 목록이다.
- [합성 데이터 화면 캡처](screenshots/report-pause-timeline.png).

## 문서 갱신

- [x] `CONTEXT.md`, `NEXT_TASK.md`, `IPC_CONTRACT.md`에 반영했다.
