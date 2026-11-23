# 결과 리포트 — 개입 반응 NOT_OFF_TASK (#300)

## 변경 요약

- `Action`·`Status`에 `NOT_OFF_TASK` 추가, `OFF_TASK` 외 알림에는 400.
- V26: 상태 CHECK와 `ck_intervention_ack` 갱신.
- `docs/API_CONTRACT.md`에 반응 값 설명(서버 제안) 추가.

## 테스트

- `InterventionApiTests.notOffTaskIsRecordedSeparatelyAndOnlyForOffTask`: HTTP로 NOT_OFF_TASK 저장·actedAt, 이후 다른 반응으로 바뀌지 않음, 근거 판정 id 존재, 목록에 NOT_OFF_TASK 표시, 휴식 제안에는 400이고 상태 유지.
- 전체 결과와 PostgreSQL 확인은 #301과 함께 기록한다(같은 스택).

## 남은 제한

- Notion API 계약 반영은 문서 오너 확인이 필요하다.
- Desktop에는 아직 「이것도 공부야」 버튼이 없다. 기존 Desktop은 PENDING만 읽어 영향이 없다.
- 머지 순서: #302 → #303 → #304 → 이 PR(V26).
