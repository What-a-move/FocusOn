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

다음 결정은 `결정 022`부터 추가한다.

## 목표 보조 #12 브랜치 결정 이력

공통 결정 015~021과 다른 브랜치에서 같은 번호를 사용했으므로 기존 #12 이력을 GA-015~GA-037로 구분해 보존한다. GA-030·GA-036의 과거 화면 정책은 공통 최신 결정 020·021과 analysis_rules.md를 대체하지 않는다. 내부 목표 보조의 실제 실행 기준은 GA-027·GA-028·GA-035·GA-037이며 공개 응답 변환은 후속 작업이다.

## 결정 GA-015 - 목표 보조의 JEV 판단과 OpenAI 생성 분리

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

학습 목표 설정 보조에서 TypeSafe JEV System One은 타입화된 확률 판단·검증만 수행하고 OpenAI는 사용자 표시 문장과 GoalProfile 구조화 생성만 수행한다. Python Router와 LangGraph가 Threshold, 우선순위, 최대 1회 Repair와 fallback을 결정하며 OpenAI가 JEV 결과 또는 `clarityStatus`를 덮어쓰지 못하게 한다.

### 결정 이유

비생성형 판단 모델과 생성 모델의 책임을 섞으면 JEV 응답에 존재하지 않는 설명을 파싱하거나 생성 문장이 Workflow 결정을 바꾸는 오류가 생긴다. 결정적 코드를 신뢰 경계로 두어 판단 재현성과 사용자 확인 보장을 유지한다.

### 고려한 대안

- OpenAI 단일 호출로 판단과 문구를 함께 생성: 단순하지만 분기 재현성과 확률 기반 평가가 약해진다.
- JEV가 사용자 문구까지 생성한다고 가정: JEV의 질문 타입·응답 계약과 맞지 않아 제외했다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/src/workflow/goal_assistance.py`
- `AI/docs/prompt_guide.md`
- `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-016 - 로컬 목표 보조의 Vercel AI Gateway 단일 Key 경로

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 목표 보조 테스트는 `AI_GATEWAY_API_KEY` 하나가 설정되면 Vercel AI Gateway를 우선 사용한다. JEV는 TypeSafe 호환 Base URL과 `typesafe-ai/jev`, 자연어 생성은 OpenAI 호환 Base URL과 `openai/gpt-5-mini`를 사용한다. Gateway Key가 없으면 기존 TypeSafe·OpenAI 직접 제공자 Key를 사용하는 호환 경로를 유지한다.

### 결정 이유

TypeSafe 또는 별도 Jev 서비스의 Key·잔액과 호출 주소가 맞지 않아 발생하는 인증 오류를 제거하고, 판단과 생성 모델의 로컬 테스트 인증을 한 Key로 단순화하기 위해서다. 모델 역할 분리는 결정 GA-015를 그대로 유지하며 Gateway는 호출·과금 경로만 통합한다.

### 영향 범위

- `AI/src/config.py`
- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/streamlit_app.py`
- `AI/.env.example`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-017 - Vercel JEV 추론 권한은 실제 요청 결과로 안내

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

`AI_GATEWAY_API_KEY`의 존재와 모델 목록 조회 성공은 JEV 추론 권한이 있다는 뜻으로 표시하지 않는다. 실제 JEV 요청이 `no_providers_available`와 `RestrictedModelsError`를 반환하면 API Key 또는 모델명 오류로 합치지 않고, Vercel AI Gateway 유료 크레딧이 필요한 상태로 안내한다.

### 결정 이유

2026-09-27 실제 호출에서 유효한 Key로 모델 목록은 조회됐지만 무료 등급의 `typesafe-ai/jev` 추론은 403으로 거부됐다. TypeSafe 제공자를 명시해도 같은 결과였으므로 애플리케이션 라우팅으로 해결할 수 없으며, 사용자가 `.env`를 반복 수정하지 않도록 원인을 정확히 구분해야 한다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/streamlit_app.py`
- `AI/tests/test_model_clients.py`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-018 - 목표 보조는 TypeSafe·OpenAI 공식 API를 직접 사용

- 결정일: 2026-09-27
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 목표 보조는 Vercel AI Gateway를 사용하지 않는다. JEV 판단은 `TYPESAFE_API_KEY`와 `jev-latest`로 TypeSafe 공식 API를 직접 호출하고, 질문·추천·GoalProfile 생성은 `OPENAI_API_KEY`와 `gpt-5-mini`로 OpenAI 공식 API를 직접 호출한다.

### 결정 이유

Vercel 무료 등급에서 JEV 실제 추론이 403으로 제한돼 한 Key로 두 모델을 호출하는 경로가 로컬 테스트 목적을 충족하지 못했다. 사용자가 TypeSafe 공식 API의 잔액을 충전하고 Key를 발급했으므로, 중간 Gateway 설정과 Provider 라우팅을 제거해 각 제공자의 Key만 입력하면 예측 가능하게 실행되도록 단순화한다.

### 영향 범위

- `AI/src/config.py`
- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/streamlit_app.py`
- `AI/.env.example`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-019 - 시작 가능한 학습 목표의 명확성 기준 완화

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

목표의 핵심 주제와 사용자가 하려는 학습 활동을 대략 파악할 수 있으면 세부 범위, 완료 조건, 학습 방법, 자료, 시간의 누락만으로 질문이나 추천으로 보내지 않는다는 정책을 확정한다.

`is_usable_goal`의 `INVALID` 임계값, `has_multiple_main_goals`, `has_unclear_term`의 임계값과 우선순위는 유지한다. `specificity_confidence`와 `is_usable_goal`의 `INVALID` 초과 확률은 관측값으로 보존하되, 단독으로 명확한 목표를 질문 경로로 강등하지 않는다.

### 결정 이유

기존 Score 2의 의미는 "이해 가능하지만 넓음"이었으나 Router가 2.50 이상만 `CLEAR`로 처리해, 바로 시작 가능한 학습 목표까지 불필요하게 구체화하도록 만들었다. 명확성은 상세 계획 수준이 아니라 사용자가 한 회차에서 무엇을 공부하려는지 이해할 수 있는지로 판단해야 한다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/src/analysis/goal_analyzer.py`
- `AI/src/config.py`, `AI/.env.example`
- `AI/evaluation/evaluation_cases.json`
- 목표 보조 Router·Workflow·SDK 계약 테스트

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-020 - 바로 시작 가능성 Noul을 명확성 라우팅에 사용

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

JEV 최초 판단에 `is_startable_as_is` Noul을 추가한다. 이 값은 현재 목표 문장 그대로 한 회차 학습을 시작할 수 있다는 예 확률이며 기본 `STARTABLE_AS_IS_MIN=0.70` 이상이면 `CLEAR`로 라우팅한다. 기존 `specificity_level`과 `specificity_confidence`는 목표의 세밀함을 설명하는 화면 정보로 유지하며, `CLEAR`를 직접 결정하지 않는다.

### 결정 이유

Score의 "구체성"과 Score confidence가 각각 다른 의미를 가져 사용자가 `CLEAR` 기준을 해석하기 어려웠다. 사용자가 실제로 알고 싶은 질문을 별도 Noul로 분리하면 JEV가 확신한 "지금 시작 가능" 확률을 화면에 그대로 표시하고, Router도 동일한 의미의 값으로 결정할 수 있다.

### 영향 범위

- `AI/src/analysis/goal_analyzer.py`
- `AI/src/models/jev_client.py`
- `AI/src/config.py`, `AI/.env.example`
- `AI/streamlit_app.py`
- 목표 보조 평가 Fixture·Router·Workflow·SDK 계약 테스트

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-021 - 한국어 목표 구분용 JEV 판단 기준

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

학습 목표 최초 판단의 다섯 JEV 질문과 criteria를 한국어로 작성한다. 특히 `has_unclear_term`은 약어·오타·은어 때문에 핵심 주제 또는 활동을 추측해야 할 때만 true이며, 목표 범위가 넓다는 사실만으로는 true가 아니다. `is_startable_as_is`는 한국어 활동 표현을 포함해 현재 문장 그대로 시작할 수 있는지를 판단한다.

### 결정 이유

판단 대상이 주로 한국어 목표인데 기준 문구가 영어로만 작성돼 한국어 약어·오타와 넓은 주제의 경계가 충분히 직접적으로 표현되지 않았다. 지침의 언어 변경 자체가 품질을 보장하지는 않으므로, 요청된 약어·오타·넓은 주제 사례를 고정 평가 Fixture에 함께 추가해 실제 Live 평가로 후속 확인한다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/tests/test_model_clients.py`
- `AI/evaluation/evaluation_cases.json`
- `AI/docs/prompt_guide.md`, `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-022 - CLEAR는 바로 시작 가능성과 다른 판단을 함께 만족해야 함

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

`CLEAR`는 `is_startable_as_is`와 `is_usable_goal`이 각각 0.70 이상이고, `has_unclear_term`과 `has_multiple_main_goals`가 각각 0.50 미만일 때만 허용한다. 이미 강한 판정을 위한 `INVALID_USABLE_MAX=0.20`, `UNCLEAR_TERM_MIN=0.70`, `MULTIPLE_GOALS_MIN=0.70`은 유지한다. 강한 임계값에 이르지 않았더라도 CLEAR 확인선에 걸리는 입력은 `NEEDS_QUESTION`이다.

### 결정 이유

높은 바로 시작 가능성 하나가 학습 목표 적합도 또는 불명확 표현의 중간 위험을 덮어써 잘못 `CLEAR`가 될 수 있었다. 예를 들어 바로 시작 가능성 90%, 적합도 60%, 불명확 표현 55%는 사용자가 의미를 확인할 여지가 있으므로 바로 저장시키지 않고 질문 경로로 보내야 한다.

### 영향 범위

- `AI/src/config.py`, `AI/.env.example`
- `AI/src/analysis/goal_analyzer.py`
- `AI/tests/test_goal_analyzer.py`
- 목표 보조 정책·평가·다음 작업 문서

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-023 - 목표 정보 수준 Choice로 넓은 목표와 정보 부족 목표를 분리

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정
- 대체: 결정 GA-020·022의 `CLEAR` 라우팅 규칙과 결정 GA-021의 최초 질문 개수

### 결정 내용

JEV 최초 판단에 `goal_detail_level` Choice를 추가하고 `CLEAR`, `BROAD`, `MISSING` 중 하나를 반드시 선택하게 한다. `INVALID`·`UNRECOGNIZED_TERM`·`NEEDS_SELECTION`의 기존 강한 우선순위를 적용한 뒤 `CLEAR`는 GoalProfile 확인, `BROAD`는 `NEEDS_SUGGESTION`, `MISSING`은 `NEEDS_QUESTION`으로 보낸다. `BROAD` 화면에서는 사용자가 원문을 명시적으로 선택하면 해당 원문으로 GoalProfile 확인 단계에 갈 수 있다.

`is_startable_as_is`, `specificity_level`, `specificity_confidence`는 Streamlit 판단 표의 설명값으로 유지하며 상태를 결정하지 않는다. 질문 답변은 다음 JEV 재평가와 OpenAI 생성 요청에 모두 전달하고, 최신 답변의 주제·활동·범위·결과는 추천 후보와 GoalProfile의 `interpretedGoal`에 반영하도록 Prompt 계약으로 강제한다.

### 결정 이유

Noul 확률과 Score는 넓은 목표와 핵심 정보가 빠진 목표에서 비슷한 값이 나올 수 있어, 바로 시작 가능성이나 복합 임계값만으로는 두 사례를 안정적으로 구별하기 어려웠다. 서로 배타적인 Choice로 정보 수준을 직접 판단하면 `BROAD`에는 선택권과 추천을, `MISSING`에는 질문을 일관되게 제공할 수 있다. 또한 답변이 최종 목표에 반영되지 않으면 질문 과정의 의미가 없으므로 생성 계약과 회귀 테스트에 이를 명시한다.

### 영향 범위

- `AI/src/analysis/goal_analyzer.py`
- `AI/src/models/jev_client.py`
- `AI/src/workflow/goal_assistance.py`
- `AI/src/prompts/goal_prompt.py`
- `AI/streamlit_app.py`
- 목표 보조 Router·Workflow·SDK 계약·Streamlit 테스트와 Offline Fixture
- `AI/docs/features/goal-assistance-PLAN.md`, `AI/docs/prompt_guide.md`, `AI/docs/evaluation_spec.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-024 - 학습 목표 성립 판단과 짧은 생성의 지연을 분리

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

JEV `is_usable_goal`은 목표의 구체성, 난이도, 완료 가능성, 계획 품질을 평가하지 않는다. 공부·연습·구현·문제 해결에 사용할 수 있는 주제 또는 활동이면 범위가 넓거나 완료 조건이 없어도 true를 높게 판단하도록 지침과 criteria를 명시한다. 화면에서는 이 값을 "학습 목표 성립 가능성"으로 표시하고 단독 통과 기준이 아니라는 설명을 붙인다.

OpenAI의 질문·추천·GoalProfile 구조화 생성은 기본 `reasoning_effort=minimal`, `verbosity=low`, 최대 600 출력 토큰으로 호출한다. JEV 판단·상태 라우팅·Schema 검증·실패 시 재시도는 생략하지 않는다.

### 결정 이유

기존 "실제로 수행 가능한 목표"라는 표현은 JEV가 범위가 넓은 정상 학습 목표까지 계획의 완성도 관점에서 보수적으로 채점할 여지를 만들었다. 또한 짧은 질문 하나를 만들 때 모델 기본 추론량이 필요 이상으로 커질 수 있었다. 학습 목표 성립과 정보 수준을 분리하고, 생성 모델의 추론량·출력 길이만 낮추면 판단 의미를 흐리지 않으면서 사용자가 느끼는 대기 시간을 줄일 수 있다.

### 영향 범위

- `AI/src/models/jev_client.py`
- `AI/src/models/llm_client.py`
- `AI/src/config.py`, `AI/.env.example`
- `AI/streamlit_app.py`
- 제공자 설정·JEV Prompt·Streamlit 렌더링 테스트
- `AI/docs/prompt_guide.md`, `AI/docs/features/goal-assistance-PLAN.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-025 - Laya는 목표 설정의 로컬 비교 판단으로만 사용

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

Streamlit 목표 입력 시 `convaiinnovations/laya-multilingual`을 로컬로 호출한다. Laya에는 JEV와 정확히 같은 `originalText`, `selectedGoalText`, `effectiveGoalText`, `clarificationAnswers` state와 여섯 typed question을 전달한다. Laya 결과는 JEV와 나란히 비교 표로 표시한다.

JEV의 `clarityStatus`와 Router만 실제 질문·추천·GoalProfile·저장 흐름을 결정한다. Laya 결과·오류·미설치 상태는 JEV 판단을 변경하거나 저장을 막지 않는다.

### 결정 이유

한국어 목표에서 두 System One 계열 판단의 차이를 확인하려면 입력과 기준이 동일해야 한다. 다만 Laya Multilingual은 자체 확률·Score 보정 체계를 가지며 현재 목표 보조 도메인에서 보정·평가되지 않았으므로, 비교 결과를 제품 동작에 연결하면 안 된다.

### 영향 범위

- `AI/src/analysis/goal_assessment_contract.py`
- `AI/src/models/jev_client.py`, `AI/src/models/laya_client.py`
- `AI/streamlit_app.py`
- `AI/requirements.txt`, `AI/.env.example`
- 모델 계약·Streamlit 비교 렌더링 테스트

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-026 - Laya 기본 체크포인트의 목표 분류 수치를 보정 전 원시값으로 표시

- 결정일: 2026-09-28
- 담당 영역: AI
- 상태: 확정

### 결정 내용

Streamlit의 Laya 열 이름을 `Laya 원시값 (실험용)`으로 표시하고, JEV 확률·통과 기준과 직접 비교할 수 없다는 경고를 함께 보여 준다. Laya 비교 호출은 유지하되, 한국어 목표 라벨 데이터로 fine-tune·확률 보정·holdout 평가를 완료하기 전에는 어떤 상태·저장·추천 정책에도 사용하지 않는다.

### 결정 이유

로컬 Live sanity check에서 Laya 호출과 응답 매핑은 정상이었다. 그러나 단일 목표 `React Query 캐시 무효화 구현하기`와 `Spring Security JWT 인증 구현하기`가 각각 복수 목표 Noul 86.57%, 97.12%로 출력되어, 기본 체크포인트의 목표 분류 zero-shot 수치가 이 도메인에서 신뢰할 수 없음을 확인했다. 이를 UI 버그나 JEV와 동등한 확률처럼 보여 주면 잘못된 비교를 유도한다.

### 영향 범위

- `AI/streamlit_app.py`
- `AI/tests/test_streamlit_app.py`
- `AI/docs/CONTEXT.md`, `AI/docs/NEXT_TASK.md`

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-027 - JEV 최초 목표 판단 계약으로 복원

- 결정일: 2026-09-29
- 담당 영역: AI
- 상태: 확정

### 결정 내용

사용자 요청에 따라 JEV 최초 목표 판단을 최초 기능 구현의 계약으로 복원한다. JEV는 `goal_text`, `selected_goal_text`, `clarification_answers` state와 `is_usable_goal`, `has_multiple_main_goals`, `has_unclear_term` Noul 및 `specificity_level` Score의 네 질문만 받는다. instructions와 criteria도 최초 영어 문구를 사용한다.

Router는 `INVALID` → `UNRECOGNIZED_TERM` → `NEEDS_SELECTION` 우선순위 뒤 `is_usable_goal < 0.60`, `specificity_confidence < 0.45`, `specificity_level` 2.50/1.50 threshold를 적용한다. 나중에 추가한 `is_startable_as_is`와 `goal_detail_level` Choice는 JEV 호출·상태 분기·Streamlit 표에서 제거한다.

Laya는 비교 전용이라는 결정은 유지하되, JEV와 동일한 네 질문 및 state를 받도록 함께 되돌린다. OpenAI의 답변 반영 생성, 직접 제공자 설정, Streamlit의 선분석/최종 확인 흐름은 JEV 판단 계약이 아니므로 변경하지 않는다.

### 결정 이유

후속 한국어 지침, 바로 시작 가능성 Noul, 정보 수준 Choice를 추가한 뒤 사용자가 체감한 JEV 목표 분류가 최초 구현보다 나빠졌다고 판단했다. 동일한 기준으로 성능을 다시 확인할 수 있도록 후속 실험적 판단 변경을 제거하고, 최초 동작을 기준선으로 삼는다.

### 영향 범위

- `AI/src/analysis/goal_analyzer.py`, `goal_assessment_contract.py`
- `AI/src/models/jev_client.py`, `laya_client.py`
- `AI/streamlit_app.py`, `AI/evaluation/`, `AI/tests/`
- 목표 보조·평가·Prompt 관련 AI 문서

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-028 - 목표 명확성·추천 Score 기준 하향

- 결정일: 2026-09-30
- 담당 영역: AI
- 상태: 확정

### 결정 내용

JEV `specificity_level`의 `CLEAR` 기준을 2.50에서 2.00으로, 추천(`NEEDS_SUGGESTION`) 기준을 1.50에서 1.00으로 낮춘다. 1.00 미만만 `NEEDS_QUESTION`으로 남긴다. 학습 목표 적합도, 구체성 신뢰도, 불명확 용어, 복수 목표, 무효 입력의 기준과 우선순위는 변경하지 않는다.

### 결정 이유

사용자가 명확한 목표와 넓지만 학습 가능한 목표를 더 쉽게 확인·추천 경로로 진행할 수 있도록 Score 경계를 낮춘다. 이 값은 평가 데이터로 재조정 가능한 설정값으로 유지한다.

### 영향 범위

- `AI/src/config.py`, `AI/.env.example`
- 목표 Router 경계 테스트와 Offline 평가 Fixture
- 목표 보조·Prompt·평가 관련 문서

### 관련 Issue·PR

- Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)

## 결정 GA-029 - 로컬 목표 보조 테스트의 LangSmith 원문 Trace opt-in

- 결정일: 2026-09-30
- 담당 영역: AI
- 상태: 확정

### 결정 내용

로컬 수동 테스트에서 `LANGSMITH_TRACE_RAW_GOAL_TEXT=true`를 명시한 경우에만 LangSmith 입력에 `originalText`, `selectedGoalText`, `clarificationAnswers`를 기록한다. 기본값과 공유·통합·배포 환경의 값은 `false`다.

Streamlit의 병렬 비교는 `goal_assessment_comparison` 부모 trace로 기록하며, 그 아래 `jev_assess_goal`과 `laya_assess_goal`을 함께 표시한다. 두 모델 output에는 각자의 네 typed judgment와 현재 Router 상태를 기록한다.

### 결정 이유

목표 원문이 완전히 제거된 trace는 반복 테스트의 입력과 정답 기대를 대응할 수 없어 JEV와 Laya의 경계 사례를 분석하기 어려웠다. 로컬에서 개발자가 직접 입력한 테스트 query에 한해 opt-in으로 원문을 보이면 모델 비교와 라우팅 분석이 가능하다. 다만 실제 사용자 입력의 외부 trace 전송은 서비스화 전에 별도 개인정보·보존·접근 정책으로 검토한다.

### 영향 범위

- `AI/src/models/langsmith_tracing.py`
- `AI/src/models/jev_client.py`, `AI/src/models/laya_client.py`
- `AI/streamlit_app.py`, `AI/.env.example`, `AI/README.md`
- `docs/DATA_PRIVACY.md`, `AI/docs/CONTEXT.md`, `AI/docs/NEXT_TASK.md`

## 결정 GA-030 - MVP 화면·카메라 분석 정책 문서 동기화

- 결정일: 2026-10-01
- 담당 영역: AI·Server·Desktop·Extension·공통 개인정보 정책
- 상태: 제안 (Notion 기준 반영, 최종 팀 검토 필요)

### 기준

[AI 분석 계약](https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c)의 현재 MVP 정책을 Markdown 계약에 동기화한다. 이 Notion 페이지는 `REVIEW` 상태이므로 본 기록은 문서 반영 사실을 남기며 최종 팀 승인이나 구현 완료를 의미하지 않는다.

### MVP 문서 기준

- 목표 문장 확인·수정과 화면 관련성 판정 수정·삭제는 별개다. AI 명확화 질문은 대화 전체 최대 2회이고, 여러 목표가 입력되면 최종적으로 하나를 선택한다.
- 화면 관련성 confidence `0.75` 미만은 `UNCERTAIN`으로 처리한다. 15초 미만 화면 전환은 이탈 누적에서 제외한다. 약하게 120초 연속, 보통 최근 10분 안에 30초 이상 이탈 2회, 적극적으로 30초 연속을 적용한다.
- `RELATED`는 현재 이탈 구간과 카메라 알림 후보를 초기화하고, `UNCERTAIN`은 이탈 시간에 누적하지 않는다.
- 카메라 `AWAY` 60초에 후보를 만들고 90초 지속 시 최종 조건을 확인한다. 최신 화면이 `RELATED`면 알림 후보를 억제한다.
- `EXCLUDED`는 화면 분석을 중단하되 유효한 사용자 동의 아래 카메라 분석은 계속할 수 있다. `PRIVACY_BLOCKED`에서는 화면·카메라 분석을 모두 중단한다.
- 화면 관련성 판정 수정·삭제, 그 결과 저장과 개인화는 MVP에서 제외한다. 기존 결정 013은 후속 기능 검토용 제안으로 남기며 MVP에 적용하지 않는다.

### 영향 범위

- `AI/docs/analysis_rules.md`, `state_model.md`, `api_spec.md`
- `AI/docs/feedback_personalization_spec.md`, `evaluation_spec.md`, `data_lifecycle.md`
- `AI/docs/goal_session_spec.md`, `DEVELOPMENT_RULES.md`, `docs/DATA_PRIVACY.md`

## 결정 GA-031 - 목표 후보 검증·Repair 제한 병렬 실행

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (Live 지연·Rate Limit 검증 필요)

### 결정 내용

추천 후보별 JEV 검증과 실패 후보별 OpenAI Repair를 서로 독립된 항목끼리 병렬 실행한다. 동시 호출 수는 응답 계약의 후보 최대 개수인 3개를 넘지 않는다. 결과 순서는 입력 후보 순서에 맞춰 보존하며, 일부 병렬 요청 실패는 기존과 같이 Workflow 실패로 처리한다.

`NEEDS_QUESTION`의 질문 생성은 현재 OpenAI 구조화 요청 1회이므로 이 변경의 병렬화 대상이 아니다. 질문 생성 지연은 실제 요청 시간을 별도로 측정한 뒤 후속 최적화를 결정한다.

### 결정 이유

후보별 JEV 검증과 Repair는 서로의 결과를 기다릴 필요가 없으며, 기존 순차 실행에서는 최대 3회의 네트워크 대기가 누적된다. 제한된 병렬 실행으로 사용자 대기 시간을 줄이되, 후보 수와 동시 요청을 제한해 요청 폭증을 막는다.

### 영향 범위

- `AI/src/workflow/goal_assistance.py`
- `AI/tests/test_goal_workflow.py`
- `AI/docs/features/goal-assistance-PLAN.md`, `CONTEXT.md`, `NEXT_TASK.md`

## 결정 GA-032 - Streamlit 질문 답변의 목표 입력 반영

- 결정일: 2026-10-02
- 담당 영역: AI (로컬 Streamlit 테스트 화면)
- 상태: 구현 (사용성 Live 확인 필요)

### 결정 내용

사용자가 Streamlit 확인 질문에 답하면 해당 답변 문구를 현재 목표 입력값 뒤에 반영하고, 업데이트된 목표 문장으로 다음 JEV 판단을 실행한다. 같은 답변이 이미 목표 문장에 포함된 경우 중복 추가하지 않는다. 사용자는 업데이트된 목표 문장을 계속 수정할 수 있으며, 기존과 같이 `CLEAR` 결과를 별도로 최종 확인하기 전에는 저장하지 않는다.

### 결정 이유

질문 답변과 목표 입력란이 분리되어 보이는 문제를 줄이고, 사용자가 자신의 답변이 목표에 어떻게 반영되었는지 확인·수정할 수 있게 한다. 최종 목표의 승인은 여전히 사용자에게 남긴다.

### 영향 범위

- `AI/streamlit_app.py`
- `AI/tests/test_streamlit_app.py`
- `AI/docs/features/goal-assistance-PLAN.md`, `CONTEXT.md`, `NEXT_TASK.md`

## 결정 GA-033 - 확인 질문 생성 2건 제한 병렬 경주

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (실제 지연·비용 평가 필요)

### 결정 내용

`NEEDS_QUESTION`, `UNRECOGNIZED_TERM` 및 검증 실패 fallback의 확인 질문 생성에서 동일한 구조화 OpenAI 요청을 최대 2건 동시에 실행한다. 먼저 도착한 유효한 질문 하나만 응답에 포함하고 나머지 대기 작업은 취소한다. 이전에 이미 답한 질문은 선택하지 않고 다른 결과를 기다린다. 두 결과가 모두 이전 질문을 반복하면 응답 검증 오류로 처리한다. `OPENAI_QUESTION_PARALLELISM=1`로 단일 호출을 선택할 수 있다.

### 결정 이유

이전 결정 GA-031은 후보 검증과 Repair만 병렬화했으며 질문 생성 지연에는 직접 영향을 주지 않았다. 사용자가 요청한 질문 생성 속도 개선을 실제 경로에 적용한다. 외부 제공자가 취소된 요청을 이미 처리했을 수 있어 호출량과 비용은 최대 두 배까지 늘 수 있다. 속도 향상도 Live 측정 전에는 확정할 수 없다.

### 영향 범위

- `AI/src/models/llm_client.py`, `AI/src/config.py`, `AI/src/prompts/goal_prompt.py`
- `AI/tests/test_model_clients.py`, `AI/.env.example`
- 목표 보조 기획·결과·컨텍스트·다음 작업 문서

## 결정 GA-034 - 확인 질문 스트리밍과 4.5초 제한 시간

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (Live 지연·비용·질문 품질 검증 필요)

### 결정 내용

Streamlit에서 OpenAI Responses 스트림의 질문 문구를 초안으로 순차 표시한다. 최종 응답은 기존 `ClarificationQuestion` Schema 검증을 통과해야 선택지로 표시한다. 질문 생성은 기본 4.5초 안에 유효한 결과가 없으면 상태에 맞는 로컬 기본 질문을 반환한다. 두 요청이 모두 일시 실패·Schema 오류·기존 질문 반복인 경우도 로컬 기본 질문을 사용하며, 설정·인증 오류는 표시한다. 이로써 결정 GA-033의 반복 질문 검증 오류 처리를 대체한다. 이 제한 시간에는 최초 JEV 판단과 Streamlit 렌더링은 포함하지 않는다. 병렬 생성 두 요청 중 한 건은 스트리밍하고 다른 건은 기존 구조화 호출로 유지한다.

### 결정 이유

사용자는 완성된 JSON을 기다리는 동안 아무 내용도 볼 수 없었다. 초안을 실제 토큰에 맞춰 보여주고 생성 시간을 제한하면 대기 상태를 이해할 수 있으며, 지연 시에도 답변 가능한 질문을 제공할 수 있다. 초안은 최종 질문과 달라질 수 있고 로컬 기본 질문은 모델이 생성한 문구가 아니다.

### 영향 범위

- `AI/src/models/llm_client.py`, `fakes.py`, `AI/src/workflow/goal_assistance.py`
- `AI/streamlit_app.py`, `AI/src/config.py`, `AI/.env.example`
- 목표 보조 기획·결과·컨텍스트·다음 작업 문서

## 결정 GA-035 - 5초 성능 목표와 10초 이후 기본 질문 전환 분리

- 결정일: 2026-10-02
- 담당 영역: AI
- 상태: 실험 (Live P50·P95와 비용 검증 필요)

### 결정 내용

결정 GA-034의 4.5초 강제 전환을 대체한다. 질문 생성 5초는 최적화 목표이지 유효한 모델 응답을 취소할 시점이 아니다. 병렬 생성과 Streamlit 질문 초안 스트리밍은 유지하고, 유효한 모델 질문은 먼저 완성되는 즉시 반환한다. 규칙 기반 기본 질문은 생성 시작 후 기본 10초 이상 유효한 모델 질문이 없을 때만 반환한다. 두 모델 호출이 일찍 실패하거나 기존 질문만 반복해도 10초 전에는 기본 질문을 표시하지 않는다. 설정·인증 오류는 기다리지 않고 표시한다. 이 기준은 질문 생성 단계에만 적용한다.

### 결정 이유

4.5초에 무조건 전환하면 조금 늦게 도착할 양질의 모델 질문을 버릴 수 있다. 초기 문구를 스트리밍으로 보여 주면서 5초 목표를 별도로 측정하고, 기본 질문은 충분히 기다린 뒤 최후 수단으로 사용한다.

### 영향 범위

- `AI/src/models/llm_client.py`, `AI/src/config.py`, `AI/.env.example`
- 질문 생성 테스트, Streamlit 실행 안내, 목표 보조 기획·결과·컨텍스트·다음 작업 문서

## 결정 GA-036 - 관련성 Hybrid 우선 평가와 OCR·이탈 입출력 책임 정리

- 결정일: 2026-10-11
- 담당 영역: AI·Server·Desktop·Extension 협의 필요
- 상태: 제안 (팀원 평가 산출물·최신 Notion 본문·Backend 필드 합의 확인 전)

### 결정 내용

팀원이 보고한 120건 비교에서 Hybrid는 99건 정답(82.5%), Macro F1 0.732로 여섯 후보 중 가장 높았다. 관련성 분류의 다음 구현·재평가에서는 Hybrid를 우선 후보로 다룬다. 구성 신호·결합식·가중치·임계값은 아직 확인되지 않았으며, 현재 코드에 Hybrid 분류기가 구현된 것으로 기록하지 않는다. 결정 005의 단계적 처리 구조를 이 보고만으로 폐기하거나 새 운영 임계값으로 대체하지 않는다.

OCR 전달은 개인정보 검사를 통과한 `text`와 `ocr` 메타데이터를 구분하는 제안형으로 정리한다. AI 전처리가 최종 `qualityScore`와 정제 콘텐츠 `contentHash`를 생성하고, 이벤트 중복·최신성은 Server/Client가 담당한다. 이탈·복귀 판단은 최종 화면 관련성 출력의 라벨·confidence를 그대로 소비하며 품질점수·유사도로 덮어쓰지 않는다. Backend가 검토할 9개 저장 컬럼과 정확한 내부 요청 Schema는 확정하지 않는다. ColPali는 현 MVP 구현 범위에서 제외하고 후속 PoC로 유지한다.

### 결정 이유

팀원 메시지의 비교 결과와 입출력 정리 요청을 저장소에 남기되, 재현 자료가 없는 수치를 확정 품질 또는 배포 완료로 오인하지 않기 위해서다. 기존 OCR·이탈 기능 페이지의 본문은 요청 내용과 아직 다르게 보이므로 교차 검증이 필요하다.

### 영향 범위

- `AI/docs/evaluation_spec.md`, `analysis_rules.md`, `content_acquisition_spec.md`, `api_spec.md`
- `AI/docs/CONTEXT.md`, `NEXT_TASK.md`

## 결정 GA-037 - 목표 확인 요청 필드와 대화 ID·재시도 책임 일치

- 결정일: 2026-10-25
- 상태: 전달받은 팀 메시지에 따른 AI 내부 구현 기준; Backend·Desktop 통합 미검증
- 관련 Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)
- 입력은 `goalText`, `conversationTurn`, 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`로 통일한다. AI 내부 경로에는 `requestId`를 추가하며 공개 경로를 AI에 중복 생성하지 않는다. 내부 Python 이름·이전 JSON 입력 별칭은 이행 기간 호환하고 직렬화는 새 이름만 사용한다.
- Backend가 대화 ID의 발급·소유권·회차·최종 목표 연결을 관리하고 AI는 전달받은 ID만 반환한다. AI는 대화 상태를 저장하지 않는다. Streamlit에서만 가상 ID를 메모리에 발급해 흐름을 시험한다.
- 회차는 0~2이며 완료된 답변 수와 일치한다. 두 번 답한 뒤 명확하지 않으면 세 번째 AI 질문을 생성하지 않고 직접 수정·확인을 요청한다. 기존 응답 상태값과 나머지 필드는 유지한다.
- 자동 요청 재시도는 Backend가 retryable 오류에 1초→3초, 최대 2회 수행한다. 같은 대화 ID·회차·답변을 유지하며 Desktop은 자동 재전송하지 않는다. AI HTTP 경로의 제공자 SDK 재시도는 0, 기존 병렬 후보 생성·의미 검수 Repair는 별도다.
- Notion 연결 도구의 404 때문에 이번에는 최신 3.2 본문을 확인하지 못했다. 사용자가 직접 전달한 계약을 변경 근거로 기록한다.
