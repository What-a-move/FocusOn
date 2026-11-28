# Desktop 리포트 할 일별 집중 시간·이탈 경로

- 담당: Frontend (`apps/desktop`)
- 우선순위: MVP 기본
- 관련 [#400](https://github.com/What-a-move/FocusOn/issues/400) · 브랜치 `codex/400-desktop-report-detail`
- 설계 원본: [집중 리포트](https://app.notion.com/p/3de0b6e5e64c808683b0e9a4a79fccca), `session-report.html`의 상세 화면
- 선행: #388 리포트 딴짓 종류

## 흐름

- 서버가 확정한 회차별 할 일 시간은 시간과 비율 막대로 보여 준다. 계산 기준은 같은 회차의 관련 집중 시간 전체다.
- 확정된 딴짓 분석 기록을 브라우저·다른 앱으로 나눠 기록 건수 비율을 표시한다.
- 구버전이나 집계가 불완전한 응답은 기존 리포트로 열고, 확인되지 않은 집계는 숨긴다.

## 연결 제안

- 기존 요약 GET에 `relatedDurationMs`와 `goalFocusTimes: [{goalId, goalTextSnapshot, matchedActiveMs}]` 추가. 서버는 실제 회차의 활성 시간에서 `MATCHED`로 확정된 기여 시간만 할 일에 더하고, 할 일 교체 전후의 스냅샷을 보존한다. 데이터가 없는 이전 회차의 필드는 생략한다.
- 기존 분석 기록 GET에 `contentSource`: `TAB_META | CHROME_DOM | APP_META | DESKTOP_APP_OCR`. 공개되면 `COMPLETED + UNRELATED`의 기록 건수 기준으로 분류한다. 분류 출처가 하나라도 빠지면 편향된 비율을 표시하지 않는다.
- 제목은 회차의 할 일 사본만 쓰고 화면 제목·본문·URL·앱 이름을 보내지 않는다.

## 확인

- 집계 정상·0·누락, 중복/잘못된 시간, 레거시 리포트 처리와 상세 읽기.
- 서버에서 필드를 반영한 뒤 실제 회차 비교 QA.
