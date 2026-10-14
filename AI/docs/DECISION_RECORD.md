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

- 결정일: 2026-09-22
- 담당 영역: AI·Server·Client
- 상태: 제안

### 결정 내용

AI 내부는 `RELATED`, `SUPPORTING`, `UNCERTAIN`, `OFF_TASK`, `UNAVAILABLE`을 유지한다. 제품 표시에서는 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 사용한다. `SUPPORTING`은 `RELATED`의 보조 유형, `OFF_TASK`는 `UNRELATED`, `UNAVAILABLE`은 `UNCERTAIN`과 처리 실패 상세 상태로 투영한다. `EXCLUDED`와 `PRIVACY_BLOCKED`는 관련성 결과가 아닌 비분석 결과다.

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
- 상태: 제안

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
- 상태: 제안

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
- 상태: 제안

### 결정 내용

Notion 기획서에 카메라 MVP 포함과 출시 미정 내용이 함께 있어 제품 출시 여부는 팀 결정 전까지 보류한다. AI는 원본 영상·프레임·랜드마크를 받지 않으며, 출시되더라도 클라이언트의 최소 상태·집계값만 선택적으로 사용한다. 개인화 휴식은 카메라 없이 시간·휴식 선택 데이터만으로도 동작해야 한다.

### 영향받는 파일

- `AI/README.md`
- `AI/docs/CONTEXT.md`
- `AI/docs/DEVELOPMENT_RULES.md`
- `AI/docs/features/personalized-break-recommendation-PLAN.md`

## 결정 020 - 이재빈 노션 기준 Hybrid MVP와 학교 테스트 지침

> 통합 전 이 Branch의 결정 015. 최신 dev의 결정 015~019를 보존하기 위해 번호만 이동했다.

- 결정일: 2026-10-07
- 담당 영역: AI / 이재빈
- 상태: 확정 — 이재빈의 MVP 개발 방향. 배포·공통 API 전체 합의·성능 검증 완료를 뜻하지 않는다.
- 근거: 이재빈 노션 전처리·관련성 기능 문서와 사용자 전달 Hybrid 비교 결과.
- 결정 내용: 목표별 소형 Graph + 키워드 + 벡터 Hybrid 6번, Qwen/Qwen3-Embedding-4B·FP16, Windows GPU의 WSL2·Linux·vLLM 운영 방향. 역할별 점수·근거를 독립 평가한다.
- 변경 내용: 기존 후보 1~7은 비교 기록으로 유지하고 현재 #14는 채택 방식의 구현·평가를 수행한다. 지침은 Issue에서 도출하지 않고 노션을 직접 기준으로 작성한다.
- 경계: qualityScore는 전처리 품질, cosine·결합 점수는 관련성 내부 신호. 사용자 판정 수정·삭제·수정 기반 개인화는 현재 구현에서 제외한다.
- 미확정: 숫자 결합 비율·정규화·임계값·품질 산식·평가 완료 기준, 백엔드 컬럼 9개, 실제 장비 호환성과 리비전/실측, 추가 LLM 제공자·예산.
- 영향: #14 PLAN, CONTEXT, NEXT_TASK. 공통 API·공유 타입은 별도 합의 없이 변경하지 않는다.
- 관련 Issue: [#14](https://github.com/What-a-move/FocusOn/issues/14), [#11](https://github.com/What-a-move/FocusOn/issues/11)

## 결정 021 - 입력 전처리와 관련성 구현 Issue·Branch 통합

> 통합 전 이 Branch의 결정 016. 결정 내용은 유지하고 번호만 이동했다.

- 결정일: 2026-10-07
- 담당 영역: AI / 이재빈
- 상태: 확정 — 사용자 요청에 따른 작업 단위 통합
- 결정 내용: #14의 Graph·Hybrid·관련성 구현·평가를 #11의 입력 검증·전처리 범위에 통합한다. 작업 Branch는 `feat/11-ai-preprocessing-relevance` 하나를 사용한다.
- 유지 사항: 전처리와 관련성 모듈·테스트 분리, 노션 요구사항·출력 계약·개인정보·미합의 9개 필드 정책은 유지한다.
- 보존 사항: 로컬 미커밋 전처리·실험 파일은 백업 후 복원한다. 통합 문서 커밋은 해당 코드를 구현 완료 또는 원격 게시로 표시하지 않는다.
- 이력: #14는 #11로 통합됐음을 표시하고 중복 이슈로 닫는다. 기존 커밋은 통합 Branch의 조상으로 보존한다.
- 영향: 학교 PLAN, 이슈 #11/#14, Branch·CONTEXT·NEXT_TASK·문서 안내 링크.
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11), [#14 통합 이력](https://github.com/What-a-move/FocusOn/issues/14)

## 결정 022 - 학교 Ollama 초기 테스트와 Mac 개발 분리

- 결정일: 2026-10-07
- 담당 영역: AI / 이재빈
- 상태: 확정 — 사용자 요청에 따른 개발·테스트 환경 방향. 실제 장비 검증·배포 완료를 뜻하지 않는다.
- 근거: 최신 이재빈 관련성 노션 4.1절과 학교 PC 설치·테스트 지침서 작성 요청.
- 결정 내용: Mac에서 검증·전처리·Graph·Hybrid 판정을 개발하고 학교 Windows GPU PC에서는 Ollama로 Qwen3 Embedding 4B·FP16 임베딩 API를 먼저 테스트한다. 추후 WSL2·Linux·vLLM으로 전환한다.
- 기존 결정과의 관계: 결정 020의 모델·정밀도·Hybrid 방식을 유지하며 vLLM 운영 전 초기 Ollama 테스트 단계를 추가한다. 최종 출력 계약과 임계값은 이번 작업에서 변경하지 않는다.
- 검증 경계: 합성 입력으로 벡터·GPU 사용·학교 로컬 warm 측정·Mac 왕복을 구분한다. 실제 설치와 Mac 접근은 학교 PC에서 검증해야 한다.
- 영향: 학교 SETUP 지침, CONTEXT, NEXT_TASK, 기능 문서 안내. 원문·이미지·비밀 정보·모델 가중치는 저장소에 올리지 않는다.
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)

## 결정 023 - 통합 #11 학교 내부 실행 경계와 실험 정책

- 결정일: 2026-10-07
- 상태: 구현 선택 기록. 숫자 정책·Graph 템플릿·공개 API의 팀 확정을 뜻하지 않는다.
- 결정 내용: 결정 022에 따라 Ollama 초기 Client와 내부 전처리→Graph→Hybrid 파이프라인·합성 평가 CLI를 먼저 구현한다. 입력 콘텐츠·확정 목표·Server 검증 문맥을 분리하며 공개 API 경로·envelope·confidence는 새로 정의하지 않는다.
- 품질 산식: 원천 품질 × (1 - 깨진 문자 비율) × 정제 문단 수 / (정제 문단 수 + 중복 제거 수 + UI 제거 수). DOM 원천 품질은 1, OCR은 capture 성공 시 인식 confidence다. Client qualityScore는 최종 품질로 신뢰하지 않는다. 이 산식은 단위 기반 초기 실험 정책이다.
- Hybrid: 키워드는 동의어 2개 이상 일치 시 1, 1개면 0.5, 없으면 0. cosine은 [-1,1]에서 max(0,cosine)로 변환해 결합한다. 역할별 임계값·본문 커버리지·결합 비율은 `relevance_calibration.json`에 기록한 미보정 실험값이다.
- 캐시: 목표군·사용자·run·모델 digest·Graph·정책·전처리/추출기·instruction·설정과 정제 해시를 분리한 메모리 TTL/LRU. 기본 128개/300초는 실험 설정이며 영속 멱등 처리는 Server 책임이다.
- 안전: 제목만 있거나 본문을 입력 상한으로 잘라 대표성을 확인할 수 없으면 모델 미호출·UNCERTAIN. 응답 직전 최신 문맥을 다시 조회하고 stale/제외 결과에는 근거를 남기지 않는다.
- 모델 확인: `qwen3-embedding:4b-fp16` 이름·정확한 manifest digest·F16·embedding capability·2560차원을 확인한다. real 모드의 digest가 없거나 모델/연결이 틀리면 fake로 대체하지 않는다. GPU 실제 사용·메모리·속도는 학교에서 별도 확인한다.
- 영향받는 파일: `AI/src/`, `AI/evaluation/relevance_*`, 테스트·CONTEXT·NEXT_TASK·PLAN·REPORT.
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)

## 결정 024 - 기존 제목·본문 입력의 누락 정보 반영

- 결정일: 2026-10-11
- 상태: 사용자 요청에 따른 구현 선택 기록. 새 공개 계약 또는 점수 정확도 확정을 뜻하지 않는다.
- 근거: 김성현 OCR 입력 계약의 `pageTitle`·`appName`·`urlHost`·`text`, 이재빈 관련성 계획의 제목·대표 본문·충돌 검사.
- 내용: 제목과 명시된 Markdown 소제목을 기존 Hybrid 방식으로 비교한다. 관련·무관 통과 근거가 함께 있으면 보류하며 제목·소제목은 본문 커버리지에서 제외한다. 본문 근거로 판정된 경우 같은 방향의 제목·소제목 ID를 함께 반환한다.
- 전처리: 내부 `HEADING` 종류로 소제목을 구분하고 코드 fence의 언어·설명·코드 문자열을 보존한다. 공개 입력 컬럼을 추가하거나 제공되지 않은 정보를 생성하지 않는다.
- 메타데이터: 앱·도메인은 개인정보 검사와 결과 캐시 구분에 사용한다. 임의 점수 가중치를 추가하지 않는다.
- 버전: `school-text-v2`, `school-hybrid-v2`. 판정 버전을 캐시 범위에 포함해 구 정책 결과를 재사용하지 않는다.
- 검증 경계: 기존 결합 비율·임계값은 미보정 실험값이다. 제목/본문 충돌 정책과 코드 입력 변경은 학교의 실제 Qwen 평가가 필요하다.
- 영향: 통합 #11 전처리·Hybrid·파이프라인·관련 테스트와 PLAN/REPORT/CONTEXT/NEXT_TASK.

## 결정 025 - 학교 모델 연결을 Tailscale 사설 HTTPS로 진행

- 결정일: 2026-10-13
- 상태: 사용자 선택에 따른 연결·테스트 방향. 실제 설치·로그인·모델 연결 완료를 뜻하지 않는다.
- 근거: 학교 LAN HTTP 연결 timeout 진단 이후 사용자의 Tailscale 선택.
- 내용: Mac과 학교 Windows PC를 같은 tailnet에 연결한다. 학교의 기존 로컬 FastAPI `127.0.0.1:8000`을 Tailscale Serve로 사설 HTTPS에 연결하고 `/health`·합성 `/api/embed` 순서로 확인한다.
- 경계: HTTPS 인증서·tailnet 로그인과 접근 규칙을 사용한다. 기존 LAN 방화벽 허용 범위를 임의로 넓히지 않는다. 원문·토큰·키는 문서나 로그에 남기지 않는다.
- Client: 학교 FastAPI는 두 경로만 제공하므로 기존 Ollama metadata 검증 경로와는 다르다. 실제 health 응답을 받은 뒤 필요한 Client 연동을 검토하며 TLS·모델 리비전·벡터 검증을 조용히 제거하지 않는다.
- 준비: Mac 공식 설치 패키지 다운로드·체크섬·개발자 서명·Apple 공증을 확인했다. 설치 화면의 Computer Use 권한은 대기 상태이며 계정 로그인·VPN 허용·학교 Serve 주소는 아직 확보하지 못했다.
- 영향: 학교 SETUP·CONTEXT·NEXT_TASK. Hybrid 산식·입력/출력 컬럼·더미 데이터는 이번 연결 선택으로 변경하지 않는다.

## 새 결정 기록

다음 결정은 `결정 026`부터 추가한다.
