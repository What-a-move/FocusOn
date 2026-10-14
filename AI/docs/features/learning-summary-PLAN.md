# AI 기능 기획서: MVP 학습 내용 정리

## 기본 정보

- 기능명: 세션 종료 후 자동 학습 내용 정리
- 기능 ID: `learning-summary-mvp` (제안)
- 작성일: 2026-10-14
- 우선순위: MVP - 기본
- 관련 Issue: [#29](https://github.com/What-a-move/FocusOn/issues/29)
- 작업 Branch: `feat/29-ai-learning-summary`
- 기준 기획: [집중 기록 · 학습 정리 · 리포트](https://app.notion.com/p/3de0b6e5e64c808683b0e9a4a79fccca), 상태 `REVIEW`
- 적용 Rule ID: `AI-SCOPE-001`, `AI-PRIV-001`, `AI-PRIV-002`, `AI-PRIV-003`, `AI-FAIL-001`, `AI-LLM-001`, `AI-NOTE-001`, `AI-NOTE-002`, `AI-STALE-001`

## 기능 목적과 책임

확정된 학습 목표와 실제로 목표에 관련된 활동의 최소 근거를 이용해 세션 종료 뒤 짧은 복습 내용을 자동 생성한다. 자료 열람을 이해·구현·문제 해결 완료로 표현하지 않는다.

- Extension/Desktop: 권한·제외·민감 화면을 먼저 검사하고 허용된 최소 텍스트를 추출한다. 원본 DOM·OCR·캡처를 보존하지 않는다.
- AI: 화면 관련성 결과가 `RELATED`인 허용 텍스트에서 최소 `reviewEvidence`를 만들고, Server가 고정한 입력 Snapshot으로 구조화된 정리 후보를 생성한다. 시간 계산과 영구 저장은 하지 않는다.
- Server: 사용자·세션 소유권, 목표 버전, 근거·중복·개인정보, 입력 마감, 비동기 작업, 저장, 재시도, 조회를 담당한다. 세션 종료와 시간 저장은 AI 성공에 의존하지 않는다.
- Desktop 리포트: 상태와 결과를 조회해 생성 중·완료·근거 부족·실패를 구분하고 완료 결과에 복사 버튼을 제공한다.

## MVP 입력 계약

- 세션 시작 시 확정한 `goalSnapshot`과 해당 `sessionId`/`runId`만 사용한다. 목표를 방문 콘텐츠로 자동 확장하지 않는다.
- `RELATED`로 확정된 허용 화면만 근거 후보가 된다. `UNCERTAIN`, `UNRELATED`, `EXCLUDED`, `PRIVACY_BLOCKED`, 분석 실패, 기록 동의 없는 구간은 제외한다.
- `reviewEvidence`에는 개념/키워드, 관찰된 학습 행동의 짧은 의미, 관측 시각, 관련성 신뢰도, 중복 제거 해시만 둔다. 각 근거는 최대 120자, 중복 제거 후 최대 20개, 전체 AI 입력은 최대 2,400자다.
- 원본 DOM·OCR, URL/제목/검색어 전문, 긴 인용, 코드 전체, 비밀값, 개인 메시지, 이미지·카메라 데이터는 입력 Snapshot·DB·Queue·로그에 남기지 않는다.
- 같은 세션의 서로 다른 근거가 2개 미만이면 생성 모델을 호출하지 않고 `INSUFFICIENT_DATA`로 처리한다.

## 생성·출력 계약

1. `ENDED` 확정과 세션 리포트 제공을 먼저 완료한 뒤 정리 상태를 `QUEUED`로 만든다.
2. 종료 후 최대 30초 동안 이미 진행 중이던 분석 결과만 수용하고 `goalSnapshot`과 허용 근거를 고정한다. 이후 도착한 결과로 `READY` 정리를 자동 덮어쓰지 않는다.
3. Server가 작업을 원자적으로 `QUEUED → GENERATING`으로 점유한다. 세션당 정리는 최대 1개다.
4. AI는 `overview`(2~3문장), `keyConcepts`(최대 5개, 각 `name`·`briefExplanation`·`sessionEvidence`), `nextStartPrompt`(한 문장)만 구조화 출력한다. 전체 결과는 1,200자 이하다.
5. 각 서술은 확정된 근거 범위에 묶인다. 관찰되지 않은 세부 지식·이해도·달성도·완료 여부를 덧붙이지 않는다. 자동 복습 질문은 생성하지 않는다.
6. Server가 Schema·길이·근거·URL/토큰/API Key/OTP/이메일/전화번호 패턴을 저장 직전에 검증한다. 민감값이 의심되면 결과 전체를 폐기하며 부분 마스킹해 저장하지 않는다.
7. 사용자 조회 상태는 `QUEUED`, `GENERATING`, `READY`, `INSUFFICIENT_DATA`, `FAILED`다. 생성 모델 Timeout은 20초, 일시 실패는 Backend가 1분·5분 간격으로 최대 2회 재시도한다. 실패해도 세션·시간 기록은 유지한다.

## 기술·구현 순서

1. **계약 정렬:** 후속 탐색용 `AI/docs/session_note_spec.md`와 `AI/docs/api_spec.md`에 남은 `PENDING`·`COMPLETED`, 다중 버전·사용자 재생성/삭제, 사용자 메모 입력을 MVP와 명확히 분리한다. 공유 타입·Notion API·ERD·개인정보 보존/삭제 정책은 각 담당자 검토 후 일치시킨다.
2. **AI 독립 단위:** Pydantic 입력·출력 Schema, 허용 근거 선택·중복 제거·민감값 검사, 근거 제한 Prompt, Fake 모델 기반 생성기와 내부 API를 구현한다. 외부 모델 호출은 제공자·지역·보존·학습 이용·사용자 고지 승인 전 제품 데이터에 사용하지 않는다.
3. **Backend 비동기 단위:** PostgreSQL/Flyway의 근거·정리 저장, 세션 소유권 검증, 종료 후 입력 마감, 중복 작업 방지, Worker lease·재시도, 결과 검증, `GET /api/v1/sessions/{sessionId}/learning-summary`를 구현한다. 구체적 Queue 방식과 컬럼은 Backend에서 확정한다.
4. **수집·표시 연동:** 별도 진행 중인 관련성 분석의 최종 `RELATED` 결과에서만 근거를 만들도록 연결한다. Desktop 리포트는 5초 간격으로 진행 상태를 조회하고 terminal 상태에서 중단한다. Extension의 DOM/OCR·제외 정책을 통합 검증한다.
5. **통합 검증:** 정상 생성, 근거 0/1개, 같은 근거 중복, 제외·민감·동의 철회, 역순/늦은 결과, Worker 중단·중복 실행, 모델 Timeout·PII 출력, Prompt Injection, UI 상태를 테스트한다. 원문이 로그·DB·로컬 Queue에 없음을 검사한다.

## 선행 조건·미확정 사항

- 현재 `dev`에는 목표 보조 #12의 AI 실행 코드가 아직 통합되지 않았고 화면 관련성 #11, Extension 추출, Server 세션/API도 미구현이다. 학습 정리 AI 단위는 Fake 입력으로 독립 개발할 수 있지만 제품 전체 동작 완료로 간주하지 않는다.
- Notion 기획은 `REVIEW`다. 루트 `docs/DATA_PRIVACY.md`의 MVP 보존 기준은 정리·노트 1년, 분석 실행·정리 근거 90일이다. 실제 컬럼·삭제 전파, Server↔AI 내부 Schema와 인증, Queue 구현 방식은 담당자 합의가 필요하다.
- 현재 `dev`의 AI 서비스는 빈 골격이므로 첫 구현은 외부 모델·실사용자 데이터 없이 표준 라이브러리 기반 검증 코어로 시작한다. Pydantic Schema와 FastAPI 어댑터는 #12 코드 통합 및 내부 인증 계약 확인 뒤 연결한다.
- 목표 보조의 현재 `gpt-5-mini` 사용은 학습 정리 모델 선택을 확정하지 않는다. 후보 모델은 별도 평가와 외부 전송 승인 후 선택한다.
- 화면 관련성 판정 수정·삭제·저장 기반 개인화, 학습 정리의 수동 편집·삭제·재생성, 복습 질문, Graph DB 도입은 이 MVP 기능에서 제외한다.

## 완료 조건

- [ ] 각 계층의 입력·출력·개인정보 계약이 Notion 기획과 일치하고 담당자 검토가 끝났다.
- [ ] `RELATED` 허용 근거만 남고 원본·제외·민감 콘텐츠는 저장/전송되지 않는다.
- [ ] 근거가 2개 미만이면 `INSUFFICIENT_DATA`이며 모델이 호출되지 않는다.
- [ ] 정리는 근거 기반 구조화 형식·길이 제한을 지키고 근거 없는 성취 주장을 하지 않는다.
- [ ] 종료·시간 저장이 생성 지연/실패와 독립적이고 세션당 정리가 중복 생성되지 않는다.
- [ ] Timeout·재시도·Worker 재시작·늦은 결과·PII 출력이 안전하게 처리된다.
- [ ] 조회 API와 Desktop의 대기·완료·근거 부족·실패 UI가 연결된다.
- [ ] 단위·통합·개인정보 테스트와 실제 수동 시나리오를 통과하고 관련 문서·PR을 연결한다.
