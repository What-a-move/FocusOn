# 강도별 포키 딴짓 알림 결과

## 기본 정보

- 기능 ID: F04
- 담당 영역·앱: Frontend, Desktop
- 작성일: 2026-11-19
- 관련 문서: [기획서](poki-intensity-PLAN.md), [결정 043](../DECISION_RECORD.md)
- 이슈·브랜치: [#282](https://github.com/What-a-move/FocusOn/issues/282), `codex/282-desktop-poki-intensity`
- PR: 사용자 디자인 확인 대기

## 구현 결과

- 약하게는 좌우 아래에서 팻말만 올리고, 4초 뒤 또는 마우스가 벗어난 1.5초 뒤 내린다. 클릭하면 바로 내려간다.
- 보통은 세션에서 실제로 표시한 연속 딴짓 알림만 세어 1차 구석 포키, 2차 연한 흐림, 3차 이후 적극 단계로 높인다. 마지막 보통 표시 후 10분, 약하게·적극 표시, 세션 종료·변경에 횟수를 초기화한다.
- 적극 단계는 직전 적극 알림과 다른 팻말을 고르고, 올라온 뒤 5초 동안 포키·팻말·버튼의 클릭을 잠근다.
- 공부 표시는 기존 `DISMISSED` 기록으로 보내고 토스트를 띄운다. 포키와 팻말 클릭은 `ACKNOWLEDGED`로 보낸다. 카메라 알림에는 공부 버튼·토스트를 표시하지 않으며 포키·팻말 클릭으로 닫는다.

| 파일 경로 | 변경 내용 |
| --- | --- |
| `electron/main/intervention-watcher.cts` | 보통 단계 계산과 세션 초기화 |
| `electron/main/index.cts`, `electron/main/floating-window.cts` | 좌우 위치·팻말 선택과 주 화면 오버레이 |
| `electron/shared/intervention-contract.cts`, `electron/preload/index.cts` | 단계·좌우·팻말 번호 전달과 창 숨김 신호 |
| `features/floating/` | 상태별 화면, 자동 숨김, 카운트다운, 토스트 |
| `public/design/` | 보통 2차 팻말과 적극 팻말 세 장 |
| `tests/electron/` | 보통 단계와 약하게 자동 숨김 테스트 |

## 테스트 환경 및 결과

- macOS 26.6.1, Apple M4, Electron 44.1.0. 주 모니터는 노치가 있는 1512×982pt 내장 화면.
- 단독 시험에서 별도 전체 화면 앱 위와 일반 Space에 같은 오버레이가 표시됐다. 포커스를 가져오지 않았고, 실측 오버레이 좌표가 주 화면 전체 좌표와 일치했다.
- 환경 파일을 제외한 별도 디렉터리에서 Next.js 정적 빌드 성공. 해당 화면과 실제 Electron 패널·Preload로 기본 8개 상태와 카메라 알림을 검증했다. 구석 캡처는 Electron 렌더링을 검증용 배경 위 실제 창 좌표에 합성했고, 전체 화면 흐림과 토스트는 macOS 화면을 캡처했다.
- Desktop 테스트 192개와 타입 검사, 변경 파일 ESLint 통과. Electron에서 클릭 잠금과 해제, 동작 줄이기를 검증했다. 보통 2차와 적극 해제 가능 상태의 빈 배경에 클릭 이벤트를 보내도 닫히지 않았고 IPC 동작과 확인 기록 요청은 각각 0건이었다. 카메라 알림은 공부 버튼·토스트가 없었고 포키 클릭 뒤 닫히면서 서버 요청이 발생하지 않았다. 검증용 요청 수집기를 사용했다.

## 오류 기록

- [보통 단계가 시간이 지나도 계속 누적됨](poki-intensity-normal-streak-expiry-ERROR.md)
- [카메라 알림에서 공부 표시 안내가 노출됨](poki-intensity-camera-study-feedback-ERROR.md)

- [작업 영역에 갇힌 전체 화면 오버레이](poki-intensity-overlay-workarea-ERROR.md)
- [불투명하게 표시된 흐림](poki-intensity-vibrancy-opaque-ERROR.md)
- [동작 줄이기에서 남아 있던 이동 전환](poki-intensity-reduced-motion-transition-ERROR.md)
- [검증용 분리 빌드의 계약 타입 누락](poki-intensity-qa-isolated-build-ERROR.md)
- [제한된 실행 환경의 테스트 실패](poki-intensity-qa-sandbox-ERROR.md)

## 성능 및 제한사항

- 화면 흐림은 macOS 합성 결과다. 단계별 차이는 덮개 색의 투명도로 준다. 화면 원본을 수집하는 새 동작은 없다.
- 주 모니터에서만 동작한다. 마우스 이동과 macOS 앱 전환은 계속 사용할 수 있다.
- 테스트 알림으로 화면을 검증했다. 실제 서버에 공부 표시가 저장되고 분석에 반영되는지는 확인이 필요하다.

## 다음 작업

- 9개 상태의 디자인을 확인받고 `dev` 대상으로 PR을 연다. PR에 캡처와 이 리포트를 연결한다.
- [현재 상태](../CONTEXT.md), [다음 작업](../NEXT_TASK.md), [결정 기록](../DECISION_RECORD.md)을 갱신했다.
