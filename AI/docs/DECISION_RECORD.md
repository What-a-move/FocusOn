# AI 결정 기록

> AI 분석 기준·모델·프롬프트·데이터 처리 결정을 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 001 - AI 분석 범위

- 결정일: 2026-09-04
- 담당 영역: AI
- 상태: 확정

### 결정 내용

AI는 사용자의 학습 목표와 Desktop·Extension에서 전달한 최소 화면·페이지 텍스트 및 메타데이터의 관련성을 판단한다. 이후 페이지 이동·체류 시간 기반 학습 흐름과 클라이언트 MediaPipe 상태값을 별도 기능으로 보조 분석한다.

### 결정 이유

AI가 수집 범위를 임의로 넓히지 않고, 학습 목표 이탈 판단이라는 핵심 역할에 집중하기 위해서다.

### 영향 범위

- Desktop 화면 분석 데이터
- Extension 페이지 분석 데이터
- Server AI 요청·결과 API
- 관련성 점수와 집중 상태 표시

## 결정 002 - 원본 데이터 처리

- 결정일: 2026-09-04
- 담당 영역: AI
- 상태: 확정

### 결정 내용

원본 화면과 원본 카메라 영상은 AI 서버에 기본 전송하거나 저장하지 않는다. 분석에 필요한 최소 상태값·텍스트·메타데이터만 사용한다.

### 결정 이유

사용자 개인정보 노출과 불필요한 데이터 보관을 줄이기 위해서다.

## 결정 003 - 불확실한 판단 처리

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

판단 정보가 부족하거나 신뢰도가 기준에 미달하면 `UNCERTAIN`을 반환한다.

### 결정 이유

불확실한 결과를 집중 또는 비집중으로 단정해 잘못된 알림을 발생시키지 않기 위해서다.

## 결정 004 - OCR 처리 역할 분리

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

OCR 엔진과 원본 화면 처리는 Desktop 등 클라이언트가 담당한다. AI는 전달받은 OCR 텍스트의 정제·품질 판단과 학습 목표 관련성 분석을 담당한다.

### 결정 이유

원본 화면을 AI 서버로 전송하지 않는 개인정보 원칙을 유지하고 AI 서비스가 분석 로직에 집중하기 위해서다.

### 영향 범위

- Desktop OCR 처리
- `AI/src/preprocessing/`
- 관련성 분석 입력 Schema
- 루트 `docs/DATA_PRIVACY.md`

## 결정 005 - 단계적 관련성 판단

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

관련성 판단은 텍스트 전처리, 규칙 기반 판단, 임베딩 유사도 판단, 필요한 경우 LLM 정밀 판단 순서로 구성한다. 규칙이나 임베딩만으로 충분히 판단 가능한 요청에는 LLM을 호출하지 않는다.

### 결정 이유

명확한 사례의 결과를 안정적으로 유지하면서 LLM 비용과 응답 시간을 줄이기 위해서다.

### 후속 확인

- [ ] 규칙 기반 확정 조건
- [ ] 임베딩 모델과 유사도 임계값
- [ ] LLM Fallback 진입 조건
- [ ] 단계별 응답 시간과 비용

## 결정 006 - 관련성·학습 흐름·사용자 상태 분리

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 확정

### 결정 내용

현재 활동 관련성, 페이지 이동·체류 시간 기반 학습 흐름, MediaPipe 결과 기반 사용자 상태 보조 분석을 별도 모듈과 API 계약으로 분리한다.

### 결정 이유

페이지가 목표와 관련 있는지와 사용자가 실제로 화면을 보고 있는지는 다른 문제다. 입력과 판단 근거를 분리해야 오판 원인을 확인하고 기능별로 평가할 수 있다.

### 영향 범위

- `AI/src/analysis/relevance_analyzer.py`
- `AI/src/analysis/learning_flow_analyzer.py`
- `AI/src/analysis/user_state_analyzer.py`
- 후속 AI API 명세

## 결정 007 - 외부 콘텐츠 분석

- 결정일: 2026-09-06
- 담당 영역: AI
- 상태: 제안

### 결정 내용

YouTube 자막과 PDF 추출 텍스트는 현재 활동 관련성 API가 안정된 뒤 별도 입력 수집 기능으로 검토한다. Supadata 등 외부 API는 비용, 개인정보, 실패 처리, 데이터 보존 정책을 확인한 뒤 선택한다.

### 결정 이유

외부 콘텐츠는 분석 범위를 넓히지만 MVP 관련성 판단의 필수 조건은 아니며 외부 서비스 장애와 비용이 추가되기 때문이다.

## 결정 008 - Rule ID 기반 AI 개발 기준

- 결정일: 2026-09-12
- 담당 영역: AI
- 상태: 제안

### 결정 내용

AI의 개인정보, 실패 처리, 상태 분리, LLM 권한, 캐시, stale 결과, 시간 집계와 평가 규칙을 `DEVELOPMENT_RULES.md`의 고정 Rule ID로 관리한다. 각 구현 Issue와 PR은 관련 Rule ID와 검증 결과를 연결한다.

### 결정 이유

같은 원칙이 API·분석·Prompt 문서에 서로 다른 표현으로 반복되어 구현 시 확인 누락과 의미 충돌이 발생할 수 있기 때문이다.

### 영향 범위

- `AI/docs/DEVELOPMENT_RULES.md`
- AI 코드·테스트·평가 데이터
- 관련 Issue·PR 검토 방식

### 관련 Issue·PR

- Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)

## 결정 009 - Notion 공개 상태와 AI 내부 세부 상태 분리

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Shared·Client
- 상태: 제안

### 결정 내용

`extractionStatus`, `analysisStatus`, `relevanceLabel`, `driftState`, `recommendedAction`을 분리한다. 공개 `FocusState`와 API의 관련성 값은 Notion 기준 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`로 통일한다. `SUPPORTING`, `OFF_TASK`, `UNAVAILABLE` 같은 내부 세부 상태는 Server가 공개 상태로 변환한다.

### 결정 이유

추출 실패, 목표 무관, 지속적 이탈과 실제 사용자 행동 제안을 하나의 값으로 표현하면 실패가 이탈로 보이고 짧은 방문이 잘못된 알림으로 이어질 수 있기 때문이다.

### 고려한 대안

- 내부 세부 상태를 공개 API에 그대로 노출: 의미가 풍부하지만 Client·Notion 계약이 달라진다.
- 공개 5개 상태만 내부에서도 사용: 계약은 단순하지만 AI 정책과 실패 원인을 세밀하게 표현하기 어렵다.

### 영향 범위

- `AI/docs/state_model.md`
- `AI/docs/api_spec.md`
- `packages/shared-types`
- Server 저장 Schema와 Client 표시

## 결정 010 - DOM·Apple Vision 기본 경로와 ColPali 실험 경로

- 결정일: 2026-09-12
- 담당 영역: AI·Extension·macOS 네이티브 모듈
- 상태: 제안

### 결정 내용

브라우저 MVP는 DOM 추출을 우선하고 정보가 부족할 때 허용된 로컬 Apple Vision OCR을 사용한다. ColPali는 PDF·Canvas·이미지·슬라이드처럼 시각 구조가 중요한 콘텐츠의 검색·근거 보강 후보로 기준선 비교 후 편입한다.

이 결정의 OCR 책임 표현은 결정 016에서 Client별 경로로 구체화한다. Chrome은 Extension 내부 OCR, macOS Desktop은 Apple Vision OCR을 사용하며 Chrome이 Desktop Native Messaging으로 자동 우회하지 않는다.

### 결정 이유

DOM은 비용과 개인정보 위험이 낮고, Apple Vision은 원본 이미지를 로컬에서 텍스트로 바꿀 수 있다. ColPali는 시각 구조 이해에 장점이 있을 수 있지만 실행 위치, 모델 자원과 이미지 전송 정책 검증이 필요하다.

### 고려한 대안

- 모든 페이지에 ColPali 사용: 일반 DOM 페이지의 비용·지연이 증가하고 원본 이미지 처리 범위가 넓어진다.
- OCR만 사용: 표·수식·슬라이드의 구조 정보가 손실될 수 있다.

### 영향 범위

- Extension 추출기와 Native Messaging
- AI 시각 콘텐츠 실험
- `AI/docs/data_lifecycle.md`
- `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)

## 결정 011 - 목표·논리 세션·실제 회차 분리

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Shared·Client
- 상태: 제안

### 결정 내용

`goalId`는 학습 목표, `sessionId`는 목표를 이어 가는 논리 세션, `runId`는 실제 한 번의 공부 회차, `eventId`는 관찰 구간으로 분리한다. 시간과 회차 상태는 Server가 관리하고 AI 결과는 원래 이벤트에 귀속한다.

### 결정 이유

목표를 다음 날 이어 공부하는 흐름과 일시정지·종료·새 회차를 구분하고, 중복·역순 이벤트와 늦은 AI 결과가 시간과 현재 상태를 덮어쓰는 문제를 막기 위해서다.

### 영향 범위

- `AI/docs/goal_session_spec.md`
- Server Entity·집계·세션 API
- `packages/shared-types`
- Extension 세션·이벤트 처리

### 관련 Issue·PR

- Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)

## 결정 012 - 제한된 단일 FocusSessionAgent

- 결정일: 2026-09-12
- 담당 영역: AI
- 상태: 제안

### 결정 내용

명확한 전처리·캐시·임베딩·시간 정책은 고정 코드로 처리하고, 여러 허용 근거를 조회해야 하는 모호한 사례만 단일 상태 기반 FocusSessionAgent로 처리한다. Agent는 읽기 도구, 사용자 범위와 호출·시간 예산을 강제한다.

### 결정 이유

모든 이벤트에 Agent를 사용하면 비용과 지연, 비결정성과 권한 표면이 커진다. 반대로 모호한 보조 학습 흐름에는 제한된 문맥 조회가 필요할 수 있기 때문이다.

### 고려한 대안

- 모든 분석을 Agent로 처리: 흐름은 유연하지만 비용·안전·재현성이 나빠진다.
- Agent 없이 단일 LLM 호출만 사용: 단순하지만 피드백과 최근 흐름 조회를 안전하게 조합하기 어렵다.

### 영향 범위

- `AI/docs/focus_session_agent_spec.md`
- `AI/docs/prompt_guide.md`
- AI Worker·Checkpoint·모델 호출

## 결정 013 - 범위가 명확한 피드백 우선 적용

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Client
- 상태: 제안

### 결정 내용

사용자의 명시적 관련성 수정은 같은 사용자·목표 버전·콘텐츠 범위에서 AI 캐시보다 우선한다. 한 페이지의 수정은 Domain 전체나 다른 목표로 자동 확대하지 않으며 `계속 보기`, 닫기와 무응답은 정답 라벨로 사용하지 않는다.

### 결정 이유

사용자 선택을 즉시 반영하면서도 한 번의 수정이 과도한 허용 규칙으로 일반화되는 것을 막기 위해서다.

### 영향 범위

- `AI/docs/feedback_personalization_spec.md`
- 피드백 API·저장 Schema
- 캐시·알림·집계·노트 무효화

## 결정 014 - 비동기 근거 기반 회차 노트

- 결정일: 2026-09-12
- 담당 영역: AI·Server·Client
- 상태: 제안

### 결정 내용

회차 종료와 시간 저장을 먼저 확정하고 학습 노트는 낮은 우선순위의 비동기 작업으로 생성한다. 노트의 학습 주장은 허용된 `evidenceId`에 연결하고, 열람만으로 이해·구현·오류 해결 완료를 주장하지 않는다.

### 결정 이유

모델 장애가 핵심 시간 기록을 막지 않게 하고, 사용자가 확인하지 않은 학습 성취를 AI가 만들어내는 문제를 방지하기 위해서다.

### 영향 범위

- `AI/docs/session_note_spec.md`
- Server 집계·Queue·노트 저장
- 노트 Prompt·Schema·평가

## 결정 015 - 제품 표시 상태와 AI 내부 상태 매핑

- 결정일: 2026-10-13
- 담당 영역: AI·Server·Client
- 상태: 확정

### 결정 내용

AI 내부는 `RELATED`, `SUPPORTING`, `UNCERTAIN`, `OFF_TASK`, `UNAVAILABLE`을 유지한다. 제품 공개 `relation`에서는 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 사용한다. AI 관련성 판정은 앞의 세 값만 반환하며 `EXCLUDED`와 `PRIVACY_BLOCKED`는 AI 호출 전 정책 차단 결과다. `SUPPORTING`은 `RELATED`의 보조 유형이고 별도 공개 상태로 노출하지 않는다. `OFF_TASK`는 `UNRELATED`, `UNAVAILABLE`은 `UNCERTAIN`과 처리 실패 상세 상태로 투영한다.

### 결정 이유

AI 분석의 의미를 잃지 않으면서 기존 제품·공유 타입의 표시값과 실패·민감정보 상태를 구분하기 위해서다.

### 영향받는 파일

- `AI/docs/state_model.md`
- `AI/docs/api_spec.md`
- `AI/docs/prompt_guide.md`
- `AI/docs/evaluation_spec.md`

## 결정 016 - Client별 로컬 OCR 경로

- 결정일: 2026-09-22
- 담당 영역: AI·Extension·macOS 네이티브 모듈
- 상태: 제안

### 결정 내용

Chrome은 DOM 우선 후 Extension 내부 로컬 OCR을 사용하고, macOS Desktop은 허용된 활성 앱 화면에 Apple Vision 로컬 OCR을 사용한다. Chrome OCR 실패를 Desktop Native Messaging이나 외부 OCR로 자동 우회하지 않는다.

### 결정 이유

현재 Notion 기획의 Client 책임을 분리하고, 권한·설치·지연·개인정보 경계를 서로 다른 경로에서 검증하기 위해서다.

### 영향받는 파일

- `AI/docs/content_acquisition_spec.md`
- `AI/docs/CONTEXT.md`
- `AI/docs/DEVELOPMENT_RULES.md`

## 결정 017 - 목표 보조 명확성 상태

- 결정일: 2026-09-22
- 담당 영역: AI·Server·Client
- 상태: 변경됨 — 결정 020 참조

### 결정 내용

목표 보조는 `CLEAR`, `NEEDS_SELECTION`, `NEEDS_SUGGESTION`, `NEEDS_QUESTION`, `INVALID`, `UNRECOGNIZED_TERM`을 사용한다. 사용자 확인 전에는 목표를 확정하지 않으며 AI 오류·무응답이어도 직접 입력 목표를 사용자가 확인하면 세션을 시작할 수 있다.

### 영향받는 파일

- `AI/docs/goal_session_spec.md`
- `AI/docs/api_spec.md`
- `AI/docs/prompt_guide.md`
- `AI/docs/evaluation_spec.md`

## 결정 018 - 분석 제외 모드와 Server 상태 투영

- 결정일: 2026-09-22
- 담당 영역: AI·Server·Client
- 상태: 변경됨 — 결정 020 참조

### 결정 내용

Server는 `sessionStatus=ACTIVE/PAUSED/ENDED`, `exclusionMode=NONE/ANALYSIS_ONLY/ANALYSIS_AND_TIME`, `pauseReason`, `resumeRequired`를 관리한다. `ANALYSIS_ONLY`는 타이머를 유지하고 `ANALYSIS_AND_TIME`은 타이머·학습 기록을 중지한다. 두 모드 모두 콘텐츠 payload를 AI에 보내지 않는다.

### 영향받는 파일

- `AI/docs/goal_session_spec.md`
- `AI/docs/content_acquisition_spec.md`
- `AI/docs/api_spec.md`
- `AI/docs/evaluation_spec.md`

## 결정 019 - 카메라 출시 여부 보류

- 결정일: 2026-09-22
- 담당 영역: AI·Desktop·Server·Client
- 상태: 변경됨 — 결정 020 참조

### 결정 내용

Notion 기획서에 카메라 MVP 포함과 출시 미정 내용이 함께 있어 제품 출시 여부는 팀 결정 전까지 보류한다. AI는 원본 영상·프레임·랜드마크를 받지 않으며, 출시되더라도 클라이언트의 최소 상태·집계값만 선택적으로 사용한다. 개인화 휴식은 카메라 없이 시간·휴식 선택 데이터만으로도 동작해야 한다.

### 영향받는 파일

- `AI/README.md`
- `AI/docs/CONTEXT.md`
- `AI/docs/DEVELOPMENT_RULES.md`
- `AI/docs/features/personalized-break-recommendation-PLAN.md`

## 결정 020 - Notion MVP·API·분석 계약 동기화

- 결정일: 2026-10-12
- 담당 영역: AI·Server·Client
- 상태: 제안 (Notion AI 분석 계약 `REVIEW`, 팀 승인 전)

화면 개입 임계값·UNCERTAIN 처리의 최신 기준은 결정 021로 대체한다. 아래 120초 등 수치는 당시 검토 이력이다.

### 결정 내용

Notion 공개 목표 명확화 경로는 `POST /api/v1/goals/clarify`이고 판정값은 `CLEAR`, `AMBIGUOUS`, `MULTI_GOAL`, `UNKNOWN_TERM`이다. 질문은 대화당 최대 2회이며 AI 실패 시 사용자가 목표를 수동 확정할 수 있다. MVP에는 화면 관련성 판정 수정·저장 공개 API가 없다.

관련성 결과 `UNRELATED`는 콘텐츠 관계를 나타내며 시간 기반 `driftState`와 분리한다. 유효 이탈은 15초 미만 화면 전환을 제외하고 `GENTLE` 120초 연속, `NORMAL` 최근 10분 내 30초 이상 2회, `ACTIVE` 30초 연속으로 판정한다. `RELATED`는 현재 구간을 초기화하고 `UNCERTAIN`은 누적하지 않는다. 카메라는 60초에 후보만 만들고 90초에 최신 화면·권한을 다시 확인한다. 최신 화면이 `RELATED`면 억제한다.

화면 `EXCLUDED`에서는 화면 콘텐츠 분석을 중지하고 타이머와 별도 동의된 로컬 카메라 분석을 유지한다. `PRIVACY_BLOCKED`에서는 화면·카메라 분석을 중지하고 타이머는 유지한다. 카메라 원본·프레임·랜드마크는 서버·AI로 보내지 않는다. 최근 7일 개인화 휴식 추천은 P1 후보이며 MVP 기본 휴식 제안과 구분한다. 최신 제외 상태를 확인할 수 없는 동안에는 화면·카메라 분석을 모두 중지한다. 확인된 `EXCLUDED`에서만 별도 카메라 동의·권한에 따라 로컬 분석을 허용하며, 카메라 집계·전송은 별도 승인 전 금지한다.

팀원 보고의 120건 비교에서 Hybrid는 99건 정답·Macro F1 0.732로 가장 높았지만 원자료와 결합식은 미확인이다. 우선 평가 후보일 뿐 구현·배포 결정이 아니다. AI 전처리는 허용된 `text`·`ocr` 입력에서 최종 `qualityScore`·`contentHash`를 생성하며, 이탈 정책은 관련성 최종 라벨·confidence를 덮어쓰지 않는다. Backend 저장 컬럼 9개는 협의 전이다.

### 결정 이유

PR #15의 최신 문서 계약을 유지하면서 Notion 제품·API 정합성과 팀원 평가 보고, 현행 MVP 분석 정책을 구분하기 위해서다. 상세 AI 분석 계약은 Notion 문서 검토와 팀 승인을 기다린다.

### 영향받는 파일

- `AI/docs/analysis_rules.md`, `AI/docs/state_model.md`, `AI/docs/api_spec.md`
- `AI/docs/content_acquisition_spec.md`, `AI/docs/evaluation_spec.md`
- `AI/docs/feedback_personalization_spec.md`, `AI/docs/goal_session_spec.md`
- `docs/API_CONTRACT.md`, `docs/DATA_PRIVACY.md`

### 관련 Issue·PR

- Issue: [#16](https://github.com/What-a-move/FocusOn/issues/16)
- Issue: [#23](https://github.com/What-a-move/FocusOn/issues/23)

## 결정 021 - 화면 개입 v1과 MVP 학습 정리 객체

- 결정일: 2026-10-24
- 상태: 확정 (사용자 공유·최종 정정 및 최신 화면 분석 5.2·5.3, 리포트 5.3 대조)
- 결정 내용: Server가 관리하는 `v1`의 연속/반복 OR 조건은 `analysis_rules.md` 9.1을 따른다. `UNCERTAIN`은 비누적·연속 구간 종료이고 이전 30초 보류는 적용하지 않는다. 실제 전송부터의 서버 5분 cooldown과 Client의 확인 후 재집중 UI를 구분한다. 세션에 고정한 버전과 분석·개입 기록의 버전은 같아야 한다.
- 학습 정리: `keyConcepts`는 `name`, `briefExplanation`, `sessionEvidence` 객체 최대 5개다. 서로 다른 근거 2개 미만이면 `INSUFFICIENT_DATA`이며 전체 결과는 1,200자 이하, 원문·URL·코드·개인정보는 제외한다.
- 이유: 최신 사용자 확정과 제품 문서에 맞추고, 이전 Notion AI 분석 계약의 임계값·버전 예시가 새 세션 정책을 덮지 않게 한다.
- 제한: AI 코드·Policy Engine 구현 완료를 뜻하지 않는다. 현재 Notion 내 차이와 rolling window 경계의 미정 부분은 `docs/NOTION_SYNC_AUDIT.md`에 기록한다.
- 영향받는 파일: `analysis_rules.md`, `state_model.md`, `evaluation_spec.md`, `NEXT_TASK.md`, `CONTEXT.md`, `api_spec.md`, `session_note_spec.md`.



## Issue #11 AI 구현·평가 기록

아래 결정 028 이후는 Issue #11 기능 브랜치에서 이어진 AI 내부 구현 기록이다. 최신 공통 정책과 충돌하면 위의 최신 shared-dev 결정 및 해당 기능 정합성 보고서를 따른다.

## 결정 028 - 콘텐츠 부족 시 출처별 다음 수집 단계 반환

- 결정일: 2026-10-17
- 상태: 통합 #11 내부 구현 선택. 공개 API 계약이나 실제 Client 수집 구현 완료를 뜻하지 않는다.
- 근거: 허재원 화면 분석 기획과 통합 PLAN의 메타데이터 → DOM → 현재 viewport OCR 순서.
- 내용: 관련성 결과가 콘텐츠 부족·충돌 때문에 `UNCERTAIN`이면 내부 `next_content_source`를 반환한다. `TAB_META → CHROME_DOM → CHROME_VIEWPORT_OCR`, `APP_META → DESKTOP_APP_OCR`만 허용하며 OCR 이후에는 종료한다.
- 안전: 개인정보·제외·비활성 대상·stale·Graph 부재·임베딩 장애는 추가 수집으로 우회하지 않는다. 다음 단계는 새 `clientRunId`와 증가한 출처별 `sequence`를 사용하는 새 관측이다.
- 경계: foreground 앱·창·탭 선택과 DOM/OCR 실행은 Client 책임이다. 내부 `next_content_source`를 팀 합의 공개 응답 필드로 확정하지 않는다.
- 임의 목표: 이 결정 시점에는 고정 템플릿 제한을 유지했다. 이후 확정된 GoalProfile 기반 Graph 경로는 결정 029에서 구현했다.
- 영향: 내부 관련성 결과·테스트와 통합 #11 PLAN/REPORT/CONTEXT/NEXT_TASK.

## 새 결정 기록

## 결정 029 - 확정된 임의 목표의 Graph 생성 경로

- 결정일: 2026-10-17
- 상태: 통합 #11 내부 구현. 생성형 LLM 제공자 연결·공개 API·Server 영속 저장 완료를 뜻하지 않는다.
- 근거: 사용자의 임의 목표 지원 및 기존 Goal Graph 구조 유지 요청, GoalProfile 정본의 사용자 확인·버전 규칙.
- 구조: 기존 `GoalGraph`·`Concept`·역할·aliases·description·connection과 Hybrid 6번 계산을 유지한다. `ConfirmedGoal.templateId`는 선택값이며, 값이 있으면 기존 검토 템플릿만 사용하고 없으면 임의 목표 생성 경로를 사용한다.
- 생성: 주입된 생성형 LLM Client는 확정 목표 문장과 확인된 GoalProfile만 받아 동일 Graph Schema의 concepts 후보를 생성한다. 현재 화면·DOM·OCR·검색 기록은 입력하지 않는다.
- 검증: 관련 개념은 DIRECT/PREREQUISITE/SUPPORTING만 허용하고 DIRECT 최소 1개를 요구한다. 무관 주제는 기존 Graph와 같이 `role: null`이며 관련 aliases와 중복될 수 없다. 개념·aliases 개수/길이·ID 중복·민감정보·추가 필드도 검사한다.
- 실패 경계: API Key·Client가 없거나 timeout·잘못된 JSON·Schema 실패이면 확정 목표/GoalProfile 범위만 사용하는 최소 Graph로 내려가 세션 시작을 막지 않는다. 최소 Graph는 무관 주제를 추측하지 않으므로 양성 무관 근거가 필요한 `UNRELATED` 대신 근거 부족 `UNCERTAIN`을 유지한다. 미확정 목표는 기존처럼 거부한다.
- 캐시: 동일 목표·버전·Profile·생성기 fingerprint는 프로세스 내부에서 한 번 생성해 재사용한다. 운영의 재시작 간 재호출 방지는 Server가 확정 Graph와 graphVersion을 영속 저장해야 한다.
- 미연동: 실제 생성형 LLM 제공자·모델·API Key·timeout/재시도·외부 전송 고지·Server 저장 Adapter는 미정이다. 이번 테스트에서 외부 LLM을 호출하지 않았다.
- 버전: 임의 목표 Graph 해석 변경을 구분하기 위해 분석기 버전을 `school-hybrid-v4`로 올린다.
- 검증: 임의 목표 최소 Graph, Profile 역할 변환, 관련·무관 LLM Schema 검증, aliases 충돌·invalid/timeout fallback, 동일 버전 단일 생성, 기존 템플릿 회귀를 포함해 전체 191 passed, 8 subtests passed.
- 영향: `AI/src/api/schemas.py`, `analysis/goal_analyzer.py`, `models/llm_client.py`, `prompts/goal_prompt.py`, 관련성 resolver·테스트·통합 문서.

## 결정 030 - 임의 목표 Graph의 OpenAI 호환 LLM 생성과 ChromaDB 재사용

- 결정일: 2026-10-19
- 상태: 통합 #11 내부 구현. 실제 API Key를 사용한 생성 품질 승인이나 공개 API·Server 저장 완료를 뜻하지 않는다.
- 생성 호출: `GoalGraphLLMClient`의 실제 Adapter는 OpenAI 호환 `/chat/completions`를 사용한다. 모델·Base URL·API Key·timeout·최대 1회 재시도는 `AI/.env`에서 주입하며 비밀 값은 Git·로그·fingerprint에 포함하지 않는다.
- 구조화 출력: JSON mode를 요청하고 기존 `GeneratedGoalGraph` Schema로 다시 검증한다. Provider 오류·timeout·HTTP 오류·잘못된 응답은 확정 목표/GoalProfile 최소 Graph로 안전하게 내려간다.
- 저장: 생성된 Graph는 목표 전체 입력과 생성기 fingerprint를 해시한 키로 로컬 ChromaDB에 저장해 프로세스 재시작 후 재사용한다. 화면 DOM/OCR 본문·스크린샷·분석 결과는 이 Collection에 저장하지 않는다.
- Chroma 사용 경계: 이 저장소는 정확한 키 조회를 위한 파생 Graph 보관소이며 Hybrid 코사인 유사도 계산용 벡터 저장소가 아니다. 로컬 `PersistentClient`는 현재 개발·단일 인스턴스 범위이며 운영 다중 인스턴스와 Server 소유권·삭제 전파는 후속 계약이 필요하다.
- 활성화: API Key와 모델이 모두 설정된 경우에만 LLM·Chroma 경로를 구성한다. 둘 중 하나만 설정되면 설정 오류로 중단하고, 둘 다 비어 있으면 기존 최소 Graph 경로를 유지한다.
- 의존성: `chromadb==1.5.9`, `python-dotenv==1.2.3`을 고정한다.
- 영향: `AI/src/config.py`, `analysis/goal_analyzer.py`, `analysis/goal_graph_runtime.py`, `models/llm_client.py`, `storage/goal_graph_store.py`, 테스트·통합 문서.

## 결정 031 - 임베딩 요청 전용 Client

- 결정일: 2026-10-21
- 상태: 사용자 지시에 따른 통합 #11 내부 구현 변경.
- 근거: 학교 프록시와의 호환성을 맞추고 요청마다 반복되는 모델 메타데이터 왕복을 제거한다.
- 호출: 기본 `OllamaEmbeddingClient`와 real 평가 CLI는 `POST /api/embed`만 호출한다. 시작 시와 임베딩 전후의 `/health`, `/api/tags`, `/api/show`, `/api/version` 조회를 수행하지 않는다.
- 응답: 반환 모델명·벡터 개수·설정 차원·유한값·0벡터 검증과 정규화, timeout·응답 크기·재시도 제한은 유지한다.
- 버전 경계: revision·F16은 캐시와 평가 기록에 사용하는 로컬 설정이며 서버 실상태 자동 검증을 뜻하지 않는다. 모델 교체 시 revision 설정 갱신과 AI 프로세스 재시작으로 이전 캐시를 폐기한다. 조회하지 않은 서비스 버전은 평가에서 null로 유지한다.
- 검증: 관련 테스트 112 passed. 기본 Client의 실제 학교 요청은 `/api/embed` 2회뿐이었고 `dev-state-direct`가 `RELATED / GOAL_CONNECTED`를 반환했다. 공개 API·Server 연결과 독립 정확도는 별도다.

## 결정 032 - 목표 Graph 생성 지침 보강 및 Hybrid 임계값 하향 실험

- 결정일: 2026-10-21
- 상태: 사용자가 요청한 통합 #11 내부 실험. 운영 품질·공개 계약 승인값이 아님.
- 근거: 생성에 성공한 Graph에서도 기존 합성 14건이 모두 UNCERTAIN이었다. 별칭 1개 시 결합 점수가 기준에 미달하고 무관 주제의 좁은 범위로 양성 무관 근거가 형성되지 않는 것을 실측했다. Notion 관련성 계획은 역할·키워드·문단 벡터의 Hybrid를 유지하며 숫자는 테스트로 조정하도록 한다.
- Graph: `goal-graph-v2`에서 직접 구성 요소와 선행 지식·보조 도구를 구분하고 정식 용어·약어·자연스러운 한/영 별칭과 목표 밖의 일반 활동 범주를 요청한다. 현재 화면·평가 라벨로 목표를 확장하지 않으며 Schema·민감정보 검증과 실패 보류는 유지한다.
- 캐시: 생성기 fingerprint에 Prompt 버전과 지침 내용을 포함해 이전 생성 Graph의 재사용을 막는다. 기존 Graph의 구조와 JSON 컬럼은 유지한다.
- 실험값: DIRECT 0.72→0.62, PREREQUISITE/SUPPORTING 0.76→0.65, UNRELATED 0.80→0.75. `school-hybrid-v5` / `school-experimental-v2`로 구분한다. 가중치 0.35/0.65, 최소 코사인 0.55, 키워드 산식·게이트, 커버리지·충돌/실패 처리는 유지한다.
- 검증: 기존 14건과 실행 전 기대 라벨을 고정한 별도 합성 20건에서 실제 Qwen 벡터를 재사용해 이전·하향 기준을 비교한다. 단위·회귀 테스트는 219 passed, 8 subtests passed. 합성 결과·한계는 평가 리포트를 따른다.

## 결정 033 - 인접한 설명과 명시 코드의 문맥 근거 보완

- 결정일: 2026-10-21
- 상태: 사용자 요청에 따른 통합 #11 내부 구현, school-hybrid-v6.
- 범위: 기존 개별 문단 분석에 인접 설명+코드 묶음을 추가한다. 전처리된 BODY/OCR와 CODE가 붙어 있고 원문에서도 같은 쌍이었던 경우만 허용한다. 소제목·표·다른 입력을 넘지 않으며 표식 없는 코드 경계를 추측하지 않는다.
- 제한: 묶음 최대 8,000 UTF-8 바이트와 임베딩 Client 제한 중 작은 값. 초과 시 원래 단위를 유지한다. 묶음과 개별 문단 벡터는 같은 콘텐츠 배치로 요청한다.
- 판정: 묶음의 키워드·코사인·결합 점수를 같은 텍스트로 계산하고, 개별 반대 근거는 충돌 처리에 유지한다. 원래 문단 ID로 근거를 반환하고 커버리지는 원본 길이를 한 번씩만 계산한다. 숫자 기준과 공개 JSON 계약은 유지한다.
- 검증: 235 passed, 8 subtests passed. 실제 학교 Qwen API의 고정 causal mask 합성 사례에서 같은 벡터·Graph·숫자 조건으로 묶음 없이 UNCERTAIN, 적용 시 RELATED를 확인했다. 요청 2회 모두 HTTP 200, 전체 7.583초. 단건 확인이며 전체 정확도 개선 검증은 아니다.
- 기록: `evaluation/code-context-grouping-smoke.json`과 트랜스포머 평가 리포트. 별칭 생성 기준에 대한 제안은 실제 생성 Prompt 변경과 구분한다.

## 결정 034 - 현재 Hybrid를 재사용하는 로컬 Streamlit 비교

- 결정일: 2026-10-21
- 상태: 사용자 요청에 따른 내부 테스트 도구. 공개 계약·운영 임계값 변경 없음.
- 기존 E5 기본 화면을 학교 Qwen API로 교체하고 6번은 RelevanceAnalyzer school-hybrid-v6를 직접 호출한다. 후보 기준선과 현재 판정기를 구분하며 임계값 미보정 방식은 최종 판정을 보류한다.
- 입력 중 모델을 호출하지 않고 명시적 비교 버튼으로만 실행한다. 기대 라벨은 입력 계산에서 분리하며 실패 UNCERTAIN을 정답으로 집계하지 않는다. 원문·결과는 파일·DB에 자동 저장하지 않는다.
- 목표 Graph는 기존 resolver를 사용해 같은 목표의 생성/저장을 재사용한다. 임베딩은 /api/embed만 호출하며 7번 재정렬 모델 준비는 선택 사항이다.
- 검증: 전체 250 passed·8 subtests passed, 로컬 UI/HTTP 확인 및 실제 학교 Qwen 공부·요리 사례 실행. 독립 정확도와 재정렬 실측은 미완료다.

- 후속 사용자 정정: 현재 Streamlit에서는 채택한 Hybrid 6번만 실행하고 후보 1~5·7의 선택·재정렬 준비 UI는 제거한다. 기존 분석 결합식·판정 기준은 유지한다. 목표 Graph 기본값은 자동 생성으로 맞춘다.

## 결정 035 - Streamlit 세션별 분석기·연결 재사용

- 결정일: 2026-10-21
- 상태: 사용자 지연 문제 제기에 따른 로컬 테스트 UI 수정. 분석 규칙 변경 없음.
- HTTP Client·RelevanceAnalyzer를 같은 브라우저 세션에서 재사용해 기존 300초 목표 벡터·결과 캐시를 활용한다. 설정 변경 시 닫고 교체한다. 원문을 캐시하지 않으며 진단 벡터는 해시 키 기반 512건/300초로 제한한다.
- UI가 채우는 clientRunId만 매 제출마다 새 값으로 발급한다. 제공된 ID와 stale/멱등성 검사는 유지한다. 모델 요청을 하지 않은 결과는 cacheHit로 표시한다.
- 검증: 254 passed·8 subtests passed. 실제 동일 JWT 자동 Graph에서 첫 분석 2.576초/2회, 동일 화면 0.049초/0회, 본문 변경 1.000초/1회. Graph 생성 제외 단건 측정이며 독립 속도 기준선·GPU 순수 처리·운영 P95가 아니다.

## 결정 036 - 목표 기반 별칭 생성 Prompt 보강만 적용

- 결정일: 2026-10-21
- 상태: 사용자 요청 1번 범위의 내부 실험.
- goal-graph-v3.1에서 짧은 정식 용어·자연스러운 작업 표현·명사 변형·단일 소유 개념별 별칭을 요청한다. 점검은 동일 생성 응답 안에서 수행하고 추가 검증 LLM·매칭/결합 산식·임계값·JSON 계약은 변경하지 않는다.
- 기존 fingerprint로 캐시를 분리한다. 최초 v3 중복 별칭 실패는 기록에 보존하며 구조 검증을 완화하지 않는다. 목표 문장만 사용한 JWT 테스트에서 새 생성 HTTP 1회·13.363초, 기존 JWT는 별칭 적중이 개선돼도 H=0.5444·커버리지 49.6%로 UNCERTAIN이었다. 개발용 기대 일치 1/4로 독립 정확도 통과가 아니다.
- 검증: 255 passed·8 subtests passed 및 실제 학교 Qwen 재평가. 자세한 이력은 기능 REPORT를 따른다.

## 결정 037 - JSON 예시를 포함한 간결한 목표 Graph 생성 프롬프트

- 결정일: 2026-10-22
- 상태: 사용자 요청에 따른 내부 프롬프트 변경. 공개 입력 계약 변경 없음.
- `goal-graph-v4`는 기존 `concepts`·`unrelated_topics` JSON 예시, DIRECT/PREREQUISITE/SUPPORTING 정의와 관련 문서의 짧은 한·영 별칭 지침으로 구성한다. 역할을 억지로 모두 생성하지 않고 전역 별칭 중복·임의 범위 확장 금지는 유지한다.
- 별칭은 확정 목표를 기준으로 DOM/OCR 본문에 등장할 법한 표현을 생성한다. 실제 현재 본문·기대 라벨을 OpenAI에 전달하거나 판정 대상에 맞춰 목표를 확대하지 않는다.
- 사용자 지시에 따라 OpenAI 프롬프트 입력의 `goalProfile`은 제거한다. 내부 Schema·최소 Graph 폴백의 profile 지원까지 제거하는 작업은 포함하지 않는다.
- 기존 Schema 검증·매칭·가중치·임계값은 유지한다. 프롬프트 fingerprint로 기존 저장 Graph와 구분하며 과거 평가 결과를 새 버전 성능으로 사용하지 않는다.
- 입력 지침은 679단어에서 255단어로 줄었다. 생성 지연·의미 정확도 개선은 실측 전이다.
- 검증: 생성기·LLM Client·런타임 관련 테스트 26 passed, 변경 파일 `git diff --check` 통과. 새 필드 생성 없이 기존 JSON 출력과 프롬프트 변경 시 캐시 분리, profile 비전송을 확인했다. 실제 LLM을 재호출한 성능 평가는 아니다.

다음 결정은 `결정 046`부터 추가한다.


## 결정 038 - Streamlit 전용 Graph 없는 키워드·벡터 Hybrid와 본문 커버리지 실험

- 결정일: 2026-10-22
- 상태: 사용자가 명시한 Streamlit 테스트 범위. 운영 판정기·공개 계약·Notion 확정 정책의 교체가 아니다.
- 현재 화면은 목표 text → 결정적 키워드 추출 → 목표/정제 문단 실제 Qwen 벡터 → K/V 결합 → 문단 근거 → 본문 커버리지 → 3라벨 순서다. GoalProfile·템플릿·실제 본문에서 목표 키워드를 생성하지 않고, Graph 생성·OpenAI·ChromaDB를 호출하지 않는다.
- K는 목표의 고유 키워드 중 문단 적중 비율이다. H의 초기값은 K 0.20 + max(0, cosine) 0.80이다. 기존 Graph의 0.35/0.65와 역할별 기준은 가져오지 않는다. K=0인 의미상 관련 자료도 cosine 0.75 이상이면 H 0.60에 도달하도록 실험 시작점을 둔다. 이 숫자의 정확도 검증은 별도다.
- 초기 관련 문단 조건은 H≥0.60·cosine≥0.55, 무관 후보는 K=0·H≤0.20이다. 관련 커버리지≥0.60 또는 무관 커버리지≥0.70·본문≥80자·완전 추출 조건으로 각각 RELATED/UNRELATED를 정한다. 두 종류의 근거가 함께 있으면 커버리지 통과 여부와 무관하게 UNCERTAIN이며, 부족한 근거·실패도 보류한다. 낮은 목표 유사도 기반 무관 판정의 위험을 UI에 명시한다.
- 제목/소제목은 점수만 표시하고 본문 커버리지에서 제외한다. 설명+코드 묶음은 원본 ID의 합집합으로 세며 기존 본문 글자 수를 중복 합산하지 않는다. 입력·OCR·목표 버전·민감/제외/상태 검사와 실패 보류를 거친다. 품질 검사에서 잘린 본문은 판정을 보류하고 PARTIAL은 UNRELATED를 확정하지 않는다.
- 기존 HTTP Client와 Qwen 형식을 사용한다. 목표/본문 벡터만 해시 키 512개/300초 캐시에 두며 입력·기대 라벨·임계값 변경 때 판정을 새로 계산한다. 관련성 결과·원문을 파일/DB에 자동 저장하지 않는다. 실시간 문맥 재검증·이벤트 멱등 저장은 실제 서비스 분석기의 별도 기능이다.
- 검증: 합성 벡터 기반 스코어/커버리지·기대 라벨 격리·실패/정책 검사·캐시·기존 분석기 및 Streamlit 화면 회귀 41 passed. 이는 Qwen의 독립 정확도 검증이 아니다.

- 실제 연결 확인: 변경 전 고정한 합성 공부/요리/혼합 3건을 학교 Qwen API로 실행해 RELATED/UNRELATED/UNCERTAIN을 각각 확인했다. Graph 생성 0회, 임베딩 HTTP 2회, 전체 1.600초·API 누적 1.586초였다. 공부 1.241초·요리 0.353초·혼합 0.006초이며 혼합은 앞 두 본문 벡터를 재사용했다. 사전 별도 임베딩 요청으로 모델이 예열된 상태의 단회 측정이다. 합성 3건 기대 일치는 독립 정확도·평균 화면 지연·P95의 근거가 아니다.


## 결정 039 - Streamlit 실험 기준 고정과 평가 목표 불일치 차단

- 결정일: 2026-10-22
- 근거: 사용자가 현재 기준 고정과 QueryDSL 불일치 분석을 요청했다.
- 결정: 결정 038의 수치를 그대로 유지하고 현재 Streamlit에서 가중치·임계값 변경 컨트롤을 제거해 읽기 전용으로 표시한다. 기존 GraphlessConfig 산식과 점수·커버리지 조건은 변경하지 않는다.
- 평가 보호: `cases` 데이터셋에 `goal.text`가 있으면 사용자가 입력한 목표와 정규화 공백/영문 대소문자를 제외하고 일치해야 한다. 서로 다른 목표를 비교하면서 원래 정답 라벨로 일치율을 계산하지 않도록 임베딩 호출 전에 오류를 표시한다. 단일 실제 추출 payload에 새 필드를 요구하지 않는다.
- 분석: 화면에 보였던 25%는 QueryDSL 목표와 로드 밸런싱 데이터의 조합이었다. 올바른 QueryDSL 목표/데이터의 별도 재현은 7/12(58.33%)이며 자세한 점수는 `evaluation/querydsl-fixed-diagnostic.json` 및 성능지표 8절에 기록한다. 이는 사람 검수 전 합성 자료의 단회 결과다.

## 결정 040 - 동일 개념 중심의 별칭 생성 지침

- 결정일: 2026-10-22
- 사용자 요청에 따라 `goal-graph-v4.1`에서 정식 명칭·한영 동의어·표준 약어·표기 변형을 우선한다. 관련 도구·선수 지식·상위 주제를 같은 개념의 별칭으로 생성하지 않고, 조사·작업 지시·일반어를 제외한다. 개념별 2~5개를 권장하되 개수를 채우기 위한 표현은 만들지 않는다.
- 확정 목표와 개념 정의만 사용한다. 현재 본문·기대 라벨 비전송, 전역 별칭 중복 금지·JSON Schema·기존 fingerprint 캐시 분리는 유지한다.
- 현재 Graph 없는 Streamlit은 이 프롬프트를 호출하지 않는다. 실험의 키워드 추출·고정 가중치·임계값·입력 계약을 변경하지 않으며, 실제 LLM 생성 품질·속도 개선은 검증 전이다.
- 검증: 기존 생성기·LLM Client 테스트 24 passed, 단일 Desktop OCR 예시의 Streamlit 입력 파싱 통과. 실제 LLM·임베딩 호출은 수행하지 않았다.

## 결정 041 - Streamlit 로컬 키워드 정규화와 OpenAI 미호출 시간 표시

- 결정일: 2026-10-22
- 사용자 요청에 따라 실험용 키워드 추출에 Kiwi 0.23.1을 추가한다. 목표·본문 모두 NFKC 정규화 후 같은 명사/고유명사·영문 식별자 규칙으로 분석해 토큰을 비교한다. 기술 식별자를 먼저 보호하고 조사·어미·불용어를 제외하며, 임의 접미사 절단과 한글 부분 문자열 매칭을 제거한다. 별칭 생성이나 목표 확장은 하지 않는다.
- 입력 계약·Qwen 전송 문장·가중치·문단 통과 조건·커버리지 기준은 유지한다. 프로세스에서는 분석기만 재사용하며 원문/키워드 목록을 전역 캐시하지 않는다. 설치 의존성은 Streamlit 실험 requirements에만 추가한다.
- 현재 경로에는 OpenAI Client가 없으므로 미호출 0회/0초로 표시한다. Qwen 왕복·로컬 시간과 구분하고 Kiwi 최초 초기화는 로컬 시간에 포함한다. 앞으로 OpenAI 호출을 추가할 경우 실제 요청 경계의 계측도 함께 추가해야 한다.
- 검증: 관련 로컬 테스트 48 passed. 기존 QueryDSL 단일 합성 화면을 실제 학교 Qwen API 1회로 실행해 관련 커버리지 70.22%·RELATED를 확인했다. 두 번째 문단의 코사인은 0.6820으로 같고 키워드 점수 0.25→0.4286·H 0.5956→0.6313이다. 전체 6.094초·임베딩 5.211초·로컬 0.883초·OpenAI 0회다. 단건 재검증으로 전체 정확도 개선을 확정하지 않는다.
- Streamlit 재시작 후 같은 입력으로 UI에서도 RELATED·70.2%·OpenAI 미호출 0회/0초를 확인했다. 이 별도 실행은 전체 2.441초·임베딩 1.471초·로컬 0.970초(분석기 최초 초기화 포함)였으며 두 실행을 합친 운영 평균/P95로 사용하지 않는다.

## 결정 042 - Streamlit 핵심 용어·일반 표현·목표 구문 구분

- 결정일: 2026-10-22
- 사용자 승인으로 `kiwi-core-phrases-v2`를 적용한다. 작은 명시 일반 표현 목록을 목표의 나머지 명사/기술 식별자와 분리한다. 나머지는 이 실험의 핵심 용어 후보이며 의미적으로 핵심임을 모델이 검증한 결과는 아니다. K는 핵심 용어 적중 비율로 계산하고 단독 일반 표현은 점수/무관 후보 차단에 쓰지 않는다. 일반 표현만 있는 목표는 UNCERTAIN으로 보류한다.
- 목표의 일반 표현+핵심 용어 인접 순서쌍을 구문으로 보존한다. 본문은 같은 정규화 후 조사 연결만 허용하고 문장/줄/동사 경계를 넘지 않는다. `선택 정렬`도 구문으로 유지한다. 구문은 문맥과 근거를 표시하며 별도 가산하지 않는다. 한영 동의어나 선수 개념을 생성하지 않는다.
- 입력 JSON·임베딩 전송 문장·고정 가중치와 수치·커버리지·개인정보/품질 검사는 유지한다. 공개 API 필드가 아닌 Streamlit 내부 진단에 핵심/일반/구문과 무시한 단독 일반 적중을 표시한다. OpenAI·지식 DB는 호출하지 않는다.
- 검증: 관련 테스트 55 passed. 실제 Qwen 임베딩 36회로 합성 3목표/36건을 실행하고, 동일 벡터를 캐시에서 재사용해 v1 전체 명사 K/무관 조건을 재현 비교했다(재현 추가 임베딩 0회). TCP/UDP 8→9/12, 로드 밸런싱 8→8/12, QueryDSL 9→9/12, 전체 25→26/36(69.44→72.22%)이며 실패 0건이다. 변경 라벨은 tcpudp-35m 1건, 기대 라벨·본문·임계값은 변경하지 않았다. 새 방식 실행 시간 합계 18.656초는 단회 환경값이다. 이미 확인한 합성 개발 자료이며 독립 운영 정확도 개선의 증거로 쓰지 않는다.
- 파일 감시를 끈 새 Streamlit 프로세스에서 TCP/UDP 12건을 별도로 재실행해 9/12 일치·tcpudp-35m UNRELATED·K=0·H=0.1886·무관 커버리지 100%와 핵심/일반/구문 표시를 확인했다. 웹 재반영 종료 139는 [오류 기록](features/hybrid-streamlit-reload-ERROR.md)에 원인 미확정으로 남긴다.

## 결정 043 - 벡터 기본점수와 키워드 보강을 분리한 Streamlit 실험

- 결정일: 2026-10-22. 사용자가 새 결합식 적용을 명시적으로 승인했다.
- `vector-base-keyword-boost-v1`에서 S=max(0,V), H=S+0.20×K×(1−S)를 사용한다. 키워드 부족으로 벡터 점수를 낮추지 않으며 점수는 0~1 범위에 둔다. K는 기존 핵심 용어 적중 비율이고 일반 표현·목표 구문 규칙은 유지한다.
- 설정은 keyword_boost=0.20으로 표시하며 keyword_weight/vector_weight와 합계 검사, unrelated_passage_threshold는 제거한다. 관련 문단의 V≥0.55와 H≥0.60은 유지한다. 무관 후보는 핵심/구문 적중 없음·V≤0.25로 분리해 이전 식의 K=0·H≤0.20 범위를 보존한다. 커버리지·추출 품질·길이·충돌 검사는 유지한다.
- JSON 입력·정제 문장·임베딩 호출·원문 보관 정책과 운영 RelevanceAnalyzer는 변경하지 않는다. Streamlit에 버전·새 식·벡터 기본/키워드 보강/최종 점수를 구분한다. OpenAI는 미호출이다.
- 검증: 관련 모델 없는 테스트 72 passed. 키워드 미적중 시 V=0.70이 H=0.70으로 유지되는 것, V=0.54에서 키워드가 모두 맞아 H=0.632여도 최소 코사인 조건으로 보류하는 것, 무관 코사인 0.25 경계·점수 범위·기존 품질/충돌/캐시/입력 검사를 확인했다. 사용자 평가용 자료·기대 라벨은 변경하지 않았고 실제 모델 평가를 추가 실행하지 않았다. 과거 버전의 일치율은 이번 버전의 검증 결과가 아니다.


## 결정 044 - Streamlit 관련 커버리지·최소 글자 수의 판정 차단 제거

- 결정일: 2026-10-22. 사용자가 관련 비율/길이만으로 보류하지 않고 정보 부족·실패는 보류하는 실험 변경을 승인했다.
- `vector-base-keyword-boost-v2`: 관련 커버리지 설정을 제거하고 본문 통과 근거 존재·무관 근거와 충돌 없음으로 RELATED를 반환한다. 결합식·문단 점수 기준·커버리지 계산은 유지하며 사유를 RELATED_EVIDENCE_FOUND로 구분한다.
- Streamlit의 전처리만 min_text_chars=0으로 변경하고 중복 본문 30자 검사를 제거한다. 빈 본문·메뉴·문장부호뿐·근거 부족·저품질·추출/모델 실패는 보류한다. UNRELATED의 기존 비율·최소 길이·완전 추출 조건은 유지한다. 운영 판정기·공개 JSON·팀 정책은 변경하지 않는다.
- 본문 근거 한 개만으로도 통과할 수 있으므로 관련 화면 보류 감소와 무관/혼합 화면 오탐을 같이 평가해야 한다. 모델 없는 회귀와 UI 검증은 실제 Qwen 정확도 증거가 아니다.
- 검증: 모델 없는 관련 회귀·Streamlit UI 테스트 79 passed. 낮은 관련 커버리지에서 통과, 짧은 오류/코드, 짧지만 근거 없는 본문, 빈/메뉴/문장부호 입력의 무호출, 낮은 OCR 품질과 기존 충돌·무관 안전 조건을 확인했다.


## 결정 045 - Streamlit 전처리·문맥·키워드 개선과 개발 전용 보정

- 결정일2026-10-22. 사용자가 진단의1~4번 구현과 실제 개선 측정을 승인했다. `body-context-hybrid-v3`와 `kiwi-weighted-terms-v3`를 실험 경로에 적용한다.
- Streamlit에서만 제거 메뉴 수의 품질 페널티를 끄고 추출된 JSX/제네릭/entity·반복 소제목 경계를 보존한다. 운영 전처리 기본값과 공개 입력/출력은 유지한다.
- 넓은 용어는 낮은 보강 가중치를 사용하며 모든 키워드의 무관 판정 veto를 제거한다. 같은 화면의 실제 제목/창 제목/명시 소제목을 본문 문맥으로 보강하되 raw본문 최소 V를 요구한다. 메타데이터는 목표/query/본문 K에 넣지 않는다.
- 혼합48건의 라벨을 모델 호출 전에 고정했다. 개발 목표24건만72개 후보 선택에 사용하고 다른 평가 목표24건에는 고정 적용한다. 3라벨 지지 없는/모델 실패뿐인 자료로 안전한 보정을 발행하지 않으며 설정의 scoring/keyword/model/revision/dimension/instruction 버전을 검증한다.
- 선택 V0.30/H0.35, 무관V0.25. 별도 평가18/24→20/24, Macro-F1 0.771→0.811이나 무관1/6·정보부족3/6의 RELATED 오탐이 남았다. 기존 React24는4/24→23/24. 미검수 합성 실험이며 운영 기준 확정이 아니다. 평가 후 기준을 재튜닝하지 않았다. [결과](features/graphless-hybrid-calibration-REPORT.md)를 따른다.


## 결정 046 - Streamlit 메타데이터 전용 제목 판정 실험

- 2026-10-23 사용자 요청으로 TAB_META·APP_META를 본문 경로와 분리하고 본문 길이·커버리지 조건을 제외한다. 실제 pageTitle/windowTitle만 제목 근거로 사용하며 앱 이름·도메인만으로 확정하지 않는다. Graph·OpenAI·수집기·공개 API 변경은 없다.
- title-hybrid-v1: V≥0.55/H≥0.60, 보강0.20, 무관V≤0.25의 미보정 기준을 별도 고정한다. 빈/일반 제목·경계·충돌은 UNCERTAIN 후 TAB_META→CHROME_DOM, APP_META→DESKTOP_APP_OCR 요청을 표시한다. 본문 보정 V0.30/H0.35는 유지한다.
- 실제 Qwen 합성240건191/240(79.6%), 관련160 중111통과49보류, 모호80 모두보류, 실패0. 무관 정답이 없어 무관 성능·운영 정확도 검증은 아니다. 결과를 보고 임계값을 재조정하지 않는다. [실험 보고서](features/graphless-hybrid-calibration-REPORT.md).

## 결정 047 - Graphless 도입 표현의 약한 무관 후보 보류

- 2026-10-25 사용자 요청으로 002의 오보류 원인을 검토하고 실험 경로에 `adjacent-intro-v1`을 추가했다. 원래 점수 버전·숫자 임계값은 유지하고 근거 정책 버전을 별도 표시한다. 과거 보정 성능은 새 근거 정책의 정확도로 표시하지 않는다.
- 단순 도입+설명 합침은 002의 V0.249로 무관 기준0.25를 벗어나지 못했다. 대신 원본에서 인접한 동일 BODY/OCR, 한 줄 명사형 표현·종결 문장부호 없음, 다음 설명보다 짧음, 다음 설명에 문장부호 있음인 경우에 한해 두 원문 벡터의 문서 간 코사인≥0.50과 다음 설명의 목표 V>0.25를 확인한다. 통과한 도입 표현은 `INTRO_CONTEXT_DEFERRED`로 무관 근거에서 보류하며 관련 근거로 승격하지 않는다. 정제 중 제거된 경계·명시 소제목·코드·다른 출처를 넘어 연결하지 않는다.
- 무관 후보/인정 근거를 구분해 표시하고 원래 점수와 전체 분석 글자 수는 유지한다. 관련·인정된 무관 근거가 모두 있으면 계속 UNCERTAIN이다. 문서 간0.50은 새로 도입한 임시 휴리스틱이며 목표 코사인의 보정값을 그대로 전용한 것이 아니다. 실제 다른 활동의 의미를 확정하는 분류기는 아니다.
- 실제 Qwen 56건·56POST·36.342초. 동일 벡터에서 기존 정책을 재현 비교해 대조8건7→8건, 기존48건43→43건, 실패0. 002 도입 V0.227806·다음 설명과 코사인0.519579·다음 설명 V0.2521로 무관 후보가 보류돼 RELATED다. 점수·기대 라벨은 수정하지 않았다. 추가 API 호출·OpenAI·Graph 생성은 없다. 합성 개발/회귀 결과로 독립 운영 정확도를 주장하지 않는다.

## 결정 048 - 의존적인 후속 설명의 무관 근거 재검토

- 2026-10-25 사용자가 72건 개선안 구현·재평가를 승인했다. Streamlit 실험에 `dependent-followup-v2`를 추가한다. 기존 명사형 도입 표현 처리와 수치 임계값은 유지하며 공개 입력·출력, 메타데이터 판정, 운영 Graph 분석기는 변경하지 않는다.
- 한국어 후속 문장에서 앞 내용을 가리키는 표현과 추상적인 학습 명사·동작을 Kiwi로 검사한다. 전체 문장 예외 목록이나 caseId별 가중치는 사용하지 않는다. 작은 수동 어휘 규칙이며 Kiwi가 의미나 전공 주제를 이해하는 분류기는 아니다. 새로운 주제 명사·영문 식별자·알 수 없는 동작·불완전한 문장은 이 경로에서 제외한다. 영어·OCR 오탈자 등은 지원 범위를 벗어나 보류가 남을 수 있다.
- 원본에서 바로 이어진 동일 BODY/OCR의 앞 문단과 후속 문장만 묶는다. 제거된 문단·제목·소제목·코드·출처 경계를 넘어 연결하지 않고 8,000 UTF-8 바이트를 넘는 묶음은 만들지 않는다. 실제 원문 두 문단만 사용하며 제목이나 목표 문장을 묶음에 삽입하지 않는다.
- 앞 문단 자체의 V/H와 묶음 문맥의 V/H가 모두 기존 관련 기준을 통과할 때만 후속 문장의 낮은 유사도 무관 후보를 `FOLLOWUP_CONTEXT_DEFERRED`로 보류한다. 후속 문장을 관련 근거로 올리지 않고 원점수·전체 글자 수·관련 커버리지를 보존한다. 다른 무관 근거가 있으면 계속 충돌로 보류한다. 보류된 문장을 다음 문장의 관련 근거로 전파하지 않는다.
- 조건에 맞는 문맥만 기존 임베딩 입력 배치에 추가한다. 입력 벡터 수는 늘며 배치 상한을 넘으면 HTTP 요청도 늘 수 있다. Graph·OpenAI 호출은 없다. `FOLLOWUP_CONTEXT` 행과 앞 문단/문맥 식별자를 내부 진단에 표시한다.
- 자동 회귀376 passed,16 subtests passed. 새 MQTT/Redis 합성16건은 같은 실제 Qwen 벡터에서 기존 정책10/16→새 정책16/16, 기존 대조8건8/8·기존48건43/48 유지다. 새 자료도 독립 사람 검수·운영 정확도는 아니며 기존48건의 오판5건은 남아 있다. 전체600건 결과는 기존 [보정 리포트](features/graphless-hybrid-calibration-REPORT.md)에 별도 기록한다.

- 전체 실제 Qwen600건은456→528건(76→88%) 일치,72건 교정·새 오판 증가0·딴짓60/60 유지다. 최초 실패2건은 재시도 성공·최종 실패0. 90개 후속 문맥을 배치에 추가해72개 무관 근거를 보류했다. 전체185.774초·381POST이며 캐시 혼합 단회 값이다. 기준 조정 없는 합성 개발 결과로 한정한다.

## 결정 049 - 일반 메타데이터 제목의 무관 오판 방지

- 2026-10-25 사용자가 모호한 콘텐츠를 UNRELATED로 확정하지 않고 UNCERTAIN으로 처리하도록 확인했다. Streamlit 메타데이터 버전을 `title-hybrid-v2`로 갱신한다.
- 정보성 검사에 한해 앱 이름 단독 또는 구분자 뒤의 알려진 브라우저·앱 이름과 입력 `appName`을 제거한다. `내용`·`파일`·`화면` 등 일반적인 제목 표현만 남으면 관련·무관 근거로 쓰지 않는다. 모든 제목이 이 조건이면 임베딩 없이 UNCERTAIN/METADATA_INSUFFICIENT와 기존 다음 수집 출처를 반환한다. 같은 입력에 다른 유의미한 제목이 있어도 일반 제목은 반대 근거가 될 수 없다.
- 유의미한 제목의 임베딩 문자열·키워드 점수·벡터 임계값과 본문 판정은 유지한다. 앱 접미사를 실제 임베딩 입력에서도 삭제한 최초 진단에서 기존 무관 1건이 보류로 바뀌어, 최종 구현은 정보성 검사만 수정했다. 운영 Graph 분석기나 공개 API 필드를 변경하지 않는다.
- 관련 자동 회귀 127 passed. 최종 구현의 학교 Qwen 재평가 120건에서 기대 일치 36→66건, 일반 제목의 무관 오판 30→0건, 해당 30건 모두 UNCERTAIN·임베딩 0회다. 기존 정답이 오답으로 바뀐 건은 0건이며 전체 불일치 54건은 남아 있다. 90 POST·32.873초·실패 0건. 합성 개발 자료이며 실제 화면 독립 정확도가 아니다.
- 원본 파일·기대 라벨은 변경하지 않았고 입력 SHA-256을 확인했다. 결과: `evaluation/new-topic-metadata-prefilter-results.json`.


## 결정 050 - 10/26 화면 분석 MVP 계약 경계 적용

- 사용자 제공 최신 허재원 화면 분석/AI 계약과 이번 구현 요청을 기준으로 기존 #11 작업에 적용한다. 첨부 문서 자체는 REVIEW 상태이며 Notion 실시간 갱신을 확인했다고 주장하지 않는다.
- 메타데이터는 RELATED만 확정한다. 낮은 코사인의 무관 후보도 UNCERTAIN이며 사유 코드 종류와 관계없이 정상 현재 관측은 Chrome DOM 또는 Desktop OCR로 재확인한다. 차단·stale·실행 실패는 추가 캡처로 우회하지 않는다. DOM 뒤 Chrome OCR 요청은 제거하고 enum만 유지한다. 현재 입력으로 Chrome OCR을 직접 요청해도 모델을 호출하지 않는다.
- 공개 중첩 JSON과 내부 실행 문맥은 `relevance_contract`에서 변환한다. Server 문맥과 식별자·시각·목표가 다르면 거부한다. 원문 text는 최대 2,000자, 초과는 거부하며 기존 민감정보 검사를 유지한다. Client 캡처/PII 제거와 Backend 상태·이탈 정책은 별도 담당 범위다.
- 내부 진단 사유와 공개 5종 reasonCode를 분리한다. 공개 response confidence는 별도의 검증된 값을 명시적으로 받아야 하며 누락/0.75 미만이면 UNCERTAIN이다. cosine·Hybrid·OCR confidence를 판정 confidence로 전용하거나 임의 상수로 채우지 않는다. confidence 보정 및 Server 실행 연결은 후속 검증이 필요하다.
- 본문 점수 임계값·산식·정답 fixture와 과거 실측 결과는 유지했다. 메타데이터 정책 버전은 title-hybrid-v3-mvp, 내부 graph 분석기는 school-hybrid-v7-mvp다. 세션에 고정된 기존 정책을 자동 마이그레이션하는 구현이 아니다.

- 검증: 모델 없는 관련 회귀 310 passed·16 subtests passed, Streamlit 초기/입력/판정 UI 검증과 8501 health HTTP 200. 실제 Qwen/OpenAI 평가 및 Client/Server 통합은 실행하지 않았다.

## 결정 051 - 미사용 목표 Graph 실행 경로 제거

- 2026-10-28 사용자 요청에 따라 현재 Streamlit에서 사용하지 않는 Goal Graph 생성 Prompt, OpenAI 생성 Client, 생성/최소/템플릿 Graph resolver, ChromaDB 저장소·환경 설정, Graph 전용 분석기와 이전 비교/평가 CLI를 삭제한다. ChromaDB와 이전 재정렬 설치 안내도 제거한다.
- 실제 사용 중인 Kiwi + Qwen Hybrid와 공개 입력/응답 검증 도구는 유지한다. 공용 벡터 수학을 별도 모듈로 옮기고 평가된 retrieval 지침 문자열은 그대로 둔다. 판정 산식·숫자 임계값·근거 정책·기대 라벨은 이번 정리에서 바꾸지 않는다.
- Graph 전용 테스트는 제거하고 공통 벡터·스키마·캐시·문단 묶음 검사는 보존한다. 과거 실험 보고/결정은 이력으로 보존하되 현재 실행 안내에서 제거된 코드의 사용을 권하지 않는다.
- 공개 API·confidence 보정·실제 수집 연동은 이번 코드 삭제의 완료 범위가 아니다. 예전 Graph 분석기의 이벤트 멱등/현재 문맥 callback도 현재 실험 경로로 자동 이식됐다고 간주하지 않는다. 실제 Backend 통합에서 구현·검증한다.

- 검증: 전체 AI 회귀 262 passed·16 subtests passed. 기존 고정 48건은 동일한 합성 벡터에서 삭제 전후 판정/점수/근거/커버리지/임베딩 입력이 일치했다. 실제 API 정확도 재평가는 수행하지 않았다.

다음 AI 결정은 결정 052부터 추가한다.
