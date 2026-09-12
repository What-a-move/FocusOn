# AI 문서 안내

FocusOn AI 영역의 불변 규칙, 상태·API 계약, 데이터 수명, 분석 방식과 평가 기준을 관리한다.

## 작업 시작 순서

AI 작업은 다음 문서를 순서대로 확인한다.

1. `DEVELOPMENT_RULES.md`: 모든 작업에 적용되는 Rule ID와 금지 조건
2. `CONTEXT.md`: 실제 구현 상태와 현재 제한사항
3. `NEXT_TASK.md`: 다음 Issue와 선행 조건
4. `DECISION_RECORD.md`: 팀이 확정·변경·폐기한 장기 결정
5. 작업에 관련된 상세 계약
   - 목표·세션·회차 변경: `goal_session_spec.md`
   - 콘텐츠 감지·DOM·OCR 변경: `content_acquisition_spec.md`
   - 상태 변경: `state_model.md`
   - API 변경: `api_spec.md`
   - 분석 로직 변경: `analysis_rules.md`
   - Agent 도구·상태 변경: `focus_session_agent_spec.md`
   - 피드백·개인화 변경: `feedback_personalization_spec.md`
   - 학습 노트 변경: `session_note_spec.md`
   - 데이터 저장·외부 전송 변경: `data_lifecycle.md`
   - Prompt·모델 변경: `prompt_guide.md`
   - 평가 변경: `evaluation_spec.md`

기능 개발은 루트 작업 규칙에 따라 GitHub Issue와 Issue 번호 Branch에서 진행한다. 기능별 PLAN·ERROR·REPORT와 Issue·PR의 역할은 프로젝트 공통 규칙이 정리되기 전까지 기존 지침을 따른다.

## 문서 상태 구분

- `확정`: `DECISION_RECORD.md`의 확정 결정 또는 루트 개인정보 기준에 근거한다.
- `제안`: 구현 가능한 초안이지만 관련 담당자 합의가 필요하다.
- `실험`: 기준선 비교와 자원·개인정보 검증 후 편입 여부를 결정한다.
- `현재 상태`: 코드와 테스트에서 실제 확인한 사실이다.

Notion의 `FocusPath AI 최종 기획서`는 설계 입력이지만 구현 완료나 전원 합의를 뜻하지 않는다. 기획서의 API 경로, 임계값, TTL, 모델은 승인 전까지 확정값으로 사용하지 않는다.

## 문서 구조

```text
AI/docs/
├── README.md
├── DEVELOPMENT_RULES.md
├── CONTEXT.md
├── NEXT_TASK.md
├── DECISION_RECORD.md
├── goal_session_spec.md
├── content_acquisition_spec.md
├── state_model.md
├── api_spec.md
├── analysis_rules.md
├── focus_session_agent_spec.md
├── feedback_personalization_spec.md
├── session_note_spec.md
├── data_lifecycle.md
├── prompt_guide.md
├── evaluation_spec.md
├── templates/
│   ├── FEATURE_PLAN_TEMPLATE.md
│   ├── DEVELOPMENT_ERROR_TEMPLATE.md
│   └── RESULT_REPORT_TEMPLATE.md
└── features/
    └── README.md
```

## 문서별 책임

| 문서 | 책임 | 넣지 않는 내용 |
| --- | --- | --- |
| `DEVELOPMENT_RULES.md` | 반복 확인할 불변 조건과 Rule ID | 상세 API 예시, 일회성 작업 기록 |
| `CONTEXT.md` | 구현된 것과 미구현 상태 | 장기 설계 결정 |
| `NEXT_TASK.md` | 다음 작업·선행 조건·완료 조건 | 과거 작업 전체 기록 |
| `DECISION_RECORD.md` | 합의된 장기 결정과 변경 이력 | 검토되지 않은 구현 세부값 |
| `goal_session_spec.md` | 목표·논리 세션·회차·이벤트·시간 집계 계약 | DB Entity 구현 세부 |
| `content_acquisition_spec.md` | 감지 이벤트·DOM·Apple Vision OCR·ColPali 입력 경계 | 관련성 판정 임계값 |
| `state_model.md` | 상태 계층·전이·호환 매핑 | 모델 Prompt 전문 |
| `api_spec.md` | Server와 AI 사이 요청·응답 계약 | 내부 모델 선택 과정 |
| `analysis_rules.md` | 단계적 분석과 판정 정책 | 데이터 보존 기간 상세 |
| `focus_session_agent_spec.md` | Agent 진입·상태·도구·예산·실패 계약 | 자유로운 도구 실행 |
| `feedback_personalization_spec.md` | 사용자 수정 의미·적용 범위·무효화·삭제 | 사용자별 모델 자동 재학습 |
| `session_note_spec.md` | 회차 노트 입력·근거·비동기 작업·재생성 | 학습 성취 추정 |
| `data_lifecycle.md` | 수집·전송·저장·삭제 경계 | 사용자 UI 상세 |
| `prompt_guide.md` | 모델 신뢰 경계와 구조화 출력 | 최종 알림·차단 결정 |
| `evaluation_spec.md` | 데이터 세트·지표·출시 차단 조건 | 실제 사용자 원문 |

## 기본 분석 경로

```text
Extension 사전 제외·권한 검사
  → DOM 우선 추출
  → 정보 부족 시 허용된 로컬 Apple Vision OCR
  → 개인정보·품질 검사
  → Server의 인증·소유권 검증
  → AI의 규칙·캐시·임베딩 분석
  → 필요한 경우에만 LLM 또는 FocusSessionAgent
  → 관련성·흐름·행동 상태를 분리해 반환
```

PDF·Canvas·이미지·슬라이드처럼 시각 구조가 중요한 콘텐츠에는 ColPali를 비교 실험한다. ColPali는 OCR 대체나 단독 이탈 판정기가 아니며, 정식 편입 기준은 `evaluation_spec.md`를 따른다.

## AI 전용 확인 항목

- 제외 검사가 캡처·추출·모델 호출보다 먼저 실행되는가
- 원본 화면·카메라·DOM/OCR 원문이 전송·저장·로그되지 않는가
- 추출 실패·모델 실패·목표 무관·이탈이 서로 구분되는가
- `SUPPORTING` 활동을 정상 학습 흐름으로 인정하는가
- `OFF_TASK` 한 번으로 알림·차단을 실행하지 않는가
- 사용자 피드백과 목표 버전 변경이 캐시·대기 알림을 무효화하는가
- 오래된 `goalVersion`, `runId`, `navigationId` 결과를 폐기하는가
- LLM과 Agent의 입력·출력·도구·시간 예산이 제한되는가
- 모델·Prompt·임계값 변경 시 같은 데이터로 회귀 평가하는가
- ColPali가 개인정보·지연·자원 기준선을 통과했는가

## 문서 갱신 원칙

- 장기 결정이 바뀌면 기존 결정을 삭제하지 않고 `DECISION_RECORD.md`에 새 번호로 추가한다.
- API나 상태 의미가 바뀌면 공유 타입과 소비자 영향을 함께 확인한다.
- 평가되지 않은 수치는 `검토 필요` 또는 `실험값`으로 표시한다.
- 문서와 코드가 다르면 `CONTEXT.md`에는 실제 코드 상태를 적고, 차이는 Issue에 남긴다.
- 테스트 결과와 제한사항은 PR에서 리뷰할 수 있게 연결한다.

## 새 문서 생성 기준

다음 조건을 모두 만족할 때만 새 Markdown 파일을 만든다.

- 독립적으로 검토·승인할 계약이나 운영 책임이 있다.
- 기존 문서에 넣으면 서로 다른 변경 주기나 담당자가 섞인다.
- 구현 Issue가 한 문서만 읽고 입력·출력·불변 조건·검증 방법을 확인할 수 있다.
- `README.md`에서 두 번 이내의 링크로 찾을 수 있다.

다음은 기존 문서를 갱신한다.

- 상태 enum·조합: `state_model.md`
- Endpoint·오류 코드: `api_spec.md`
- 관련성·알림 정책: `analysis_rules.md`
- 개인정보·보존·삭제: `data_lifecycle.md`
- 모델·Prompt·출력 검증: `prompt_guide.md`
- 평가·테스트 계층·출시 차단: `evaluation_spec.md`
- 장기 선택과 변경 이력: `DECISION_RECORD.md`

구현되지 않은 설치·배포 Tutorial, 확정되지 않은 모델별 설정표, 같은 내용을 다른 독자 이름으로 복제한 문서는 만들지 않는다. 구현이 생기면 실제 명령과 테스트를 확인한 뒤 How-to와 Tutorial을 추가한다.
