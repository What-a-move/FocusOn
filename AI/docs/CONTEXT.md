# AI 작업 컨텍스트

> AI 담당자가 작업 시작 시 가장 먼저 읽는 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

- 학습 목표와 화면·페이지 정보의 관련성 판단
- OCR·페이지 텍스트 정제와 입력 품질 판단
- 페이지 이동·체류 시간을 이용한 목표 이탈 및 학습 흐름 분석
- MediaPipe가 클라이언트에서 계산한 상태값의 보조 분석
- 분석 결과의 상태값·점수·신뢰도 반환
- AI API 입력·출력 계약 관리
- 평가 데이터와 모델·프롬프트 품질 확인

## 현재 상태

### 완료된 작업

- 이번 필드 정합성 변경은 `LANGSMITH_TRACING=false LANGCHAIN_TRACING_V2=false LANGSMITH_TRACE_RAW_GOAL_TEXT=false PYENV_VERSION=what-a-move PYTHONPATH=AI pyenv exec python -m pytest AI/tests -q`로 73개 통과를 확인했다. 모델은 Fake/Mock으로 검증했고 Backend·실제 모델 통합은 미검증이다. 최초 실행에서 로컬 `.env`의 tracing 설정으로 LangSmith 연결 실패 로그가 발생해 이후 검증에서는 tracing을 껐다. `.env` 값은 변경하지 않았다.
- 2026-10-25 #12 연동 필드: AI 내부 요청이 `goalText`, `conversationTurn`, `clarificationSessionId?`, `previousAnswers[{question,answer}]`를 수신·직렬화하고 정상 응답에 대화 ID를 그대로 반환한다. 추적용 내부 `requestId`와 이행용 이전 입력 별칭은 유지한다. 회차 0~2와 답변 수 일치·최대 2개를 검사하며 두 답변 뒤 직접 질문·Repair fallback 질문 모두 추가 생성하지 않는다. AI HTTP 경로의 SDK 재시도는 0이다. Backend의 공개 경로·ID 발급/소유권·목표 생성 연결·1초→3초 재시도는 미구현이며 해당 담당자의 후속 작업이다. 이번 기준은 사용자가 전달한 팀 메시지이며 Notion 연결 도구는 404로 최신 본문을 재확인하지 못했다.
- Issue #12에서 JEV 판단, OpenAI 구조화 생성, LangGraph 분기·1회 Repair를 결합한 학습 목표 설정 보조를 구현했다.
- `POST /internal/v1/goals/clarify`와 camelCase Pydantic 계약, 공통 422·502·503 오류 응답을 구현했다.
- 6개 `clarityStatus`, 후보별 JEV 검수, 전체 재평가, fallback, 로그 개인정보 제외를 Fake 기반 테스트로 검증했다.
- Offline 평가 Fixture와 명시적으로만 실행되는 JEV Live 평가 경로를 추가했다.
- 실제 JEV·OpenAI Workflow를 수동 확인하는 로컬 Streamlit 테스트 화면을 추가했다. 최초 `목표 저장`은 분석만 시작하고 `CLEAR` 결과의 최종 확인 후에만 현재 테스트 세션 메모리에 저장하며, 제품 저장소나 학습 세션은 변경하지 않는다.
- 로컬 목표 보조는 TypeSafe 공식 API의 `TYPESAFE_API_KEY`로 JEV 판단을 호출하고, OpenAI 공식 API의 `OPENAI_API_KEY`로 구조화 생성을 호출한다. Vercel AI Gateway 설정과 우회 경로는 제거했다.
- Streamlit은 1차 JEV 판단을 먼저 표시하고, 사용자가 다음 단계 생성 버튼을 누를 때만 OpenAI 질문·추천·GoalProfile 생성과 후속 JEV 검수를 실행한다. 후보별 JEV 검수와 실패 후보 Repair는 최대 3개를 병렬 실행한다. 확인 질문은 OpenAI 생성 요청을 기본 2건 병렬 실행해 먼저 도착한 유효한 질문 하나만 사용하고, 이미 답한 질문은 제외한다.
- 질문 생성은 Streamlit에 실제 OpenAI 문구 초안을 스트리밍 표시한다. 5초는 성능 목표이며, 유효한 모델 질문은 즉시 사용하고 로컬 기본 질문은 질문 생성 시작 후 10초 이상 지났을 때만 사용한다. 두 호출이 일찍 실패하거나 기존 질문만 반복해도 기본 질문을 조기 표시하지 않는다. 설정·인증 오류는 바로 표시한다. 합성 목표 1회 OpenAI Live 확인에서 첫 초안 1.933초, 최종 질문 2.748초였으나 최초 JEV 판단·화면 렌더링을 포함한 P95 지연과 실제 비용은 검증되지 않았다.
- Streamlit에서 질문에 답하면 답변 문구가 목표 입력값에 반영되고, 해당 문장으로 다시 판단한다. 목표 입력은 계속 편집할 수 있으며 검증된 `CLEAR` 결과를 사용자가 최종 확인하기 전에는 저장하지 않는다.
- Streamlit의 JEV 판단 결과는 확률·구체성 수치만 있는 JSON 대신 판단 항목, 값, 한국어 설명이 나란히 표시되는 표로 요약하며 원본 JSON은 펼침 영역에 유지한다. `CLEAR`의 최종 확인 정보가 생성된 뒤에도 같은 판단 표를 계속 표시한다.
- 목표 명확성 Router는 최초 구현의 4개 JEV 질문과 Score Threshold를 사용한다. 강한 `INVALID`·복수 목표·불명확 용어 판정 후 `is_usable_goal` 0.60, 구체성 신뢰도 0.45, 구체성 점수 2.00/1.00 기준으로 `CLEAR`·추천·질문으로 분기한다.
- 최초 JEV 판단의 영어 instructions와 criteria, snake_case state(`goal_text`, `selected_goal_text`, `clarification_answers`)를 복원했다. 질문 답변에 주제·활동·범위가 있으면 최종 GoalProfile과 추천 후보에 반영한다.
- OpenAI 구조화 생성에는 `reasoning_effort=minimal`, `verbosity=low`, 최대 600 출력 토큰을 기본 적용해 질문 생성 대기를 줄인다.
- Streamlit 목표 입력 시 로컬 `convaiinnovations/laya-multilingual`도 JEV와 동일한 state·네 typed question으로 비교 판단한다. Laya는 비교 표에만 쓰고 실제 상태, Workflow, GoalProfile, 저장에는 연결하지 않는다. 최초 비교는 패키지 설치와 공개 체크포인트 다운로드가 필요하다.
- LangSmith 로컬 수동 테스트는 `LANGSMITH_TRACE_RAW_GOAL_TEXT=true`일 때 목표 원문·선택 목표·후속 답변을 표시한다. `goal_assessment_comparison` 부모 trace 아래에 JEV와 Laya 판단을 함께 남기며, 각 모델 output에는 현재 Router 상태가 포함된다. 이 설정은 로컬 테스트 외 환경에서는 꺼야 한다.
- 2026-09-28 로컬 Live 확인에서 Laya 호출·응답 매핑은 정상이나, 단일 목표 `React Query 캐시 무효화 구현하기`와 `Spring Security JWT 인증 구현하기`도 복수 목표 Noul이 각각 86.57%, 97.12%로 나왔다. 현재 기본 모델의 목표 분류 zero-shot 결과는 원시 실험값으로만 표시하며 JEV 확률과 비교하거나 정책에 사용하지 않는다.
- AI 기본 디렉터리와 평가 디렉터리가 생성되어 있다.
- AI 전용 문서 구조와 기능별 템플릿을 구성했다.
- 공통 API 응답 초안은 루트 `docs/API_CONTRACT.md`에 정의되어 있다.
- 현재 활동 관련성 API, 분석 기준, Prompt 가이드를 초안으로 작성했다.
- 관련성, 학습 흐름, MediaPipe 사용자 상태를 분리된 기능으로 개발하는 방향을 정리했다.
- Issue #3에서 AI 개발 불변 규칙, 다중 상태 모델, 데이터 수명, 평가 기준 문서 초안을 작성했다.
- DOM 우선·Apple Vision OCR 보조 경로와 ColPali 시각 콘텐츠 비교 실험 경로를 문서로 구분했다.
- 목표·논리 세션·회차·이벤트·시간 집계 계약을 `goal_session_spec.md`로 분리했다.
- 콘텐츠 감지·DOM·Apple Vision OCR·ColPali 입력 경계를 `content_acquisition_spec.md`로 분리했다.
- FocusSessionAgent, 피드백·개인화, 비동기 근거 기반 노트의 상세 계약 초안을 추가했다.

### 진행 중인 작업

- 루트 `docs/ARCHITECTURE.md`의 Desktop 중심 흐름과 Notion의 Extension 중심 기본안 차이 검토
- Server·Extension·공유 타입 담당자와 다중 상태 및 API 경로 검토
- 목표·세션·회차·이벤트 관계와 사용자당 동시 활성 회차 정책 검토
- Agent 도구 Schema·예산·Checkpoint 저장소와 피드백 적용 범위 검토
- 학습 노트 Queue·근거 Schema·재생성·삭제 정책 검토
- 기능별 PLAN·ERROR·REPORT와 GitHub Issue·PR 역할 정리
- ColPali 실행 위치와 개인정보·자원 예산 검토

### 아직 진행하지 않은 작업

- 목표 보조 외 나머지 AI 기능의 서버 Endpoint와 Schema 구현
- OCR·페이지 텍스트 정제와 품질 판단 구현
- 규칙 기반 관련성 판단 구현
- 임베딩 기반 유사도 판단 구현
- 페이지 관련성 분석용 LLM Fallback 구현
- Server 연동
- 성능·정확도 평가 자동화
- 목표·세션·회차·이벤트·집계 Schema 구현
- FocusSessionAgent 상태 그래프와 제한 도구 구현
- 피드백 저장·캐시·알림·집계 무효화 구현
- 비동기 학습 노트 작업과 근거 검증 구현
- 페이지 이동·체류 시간 기반 학습 흐름 분석
- MediaPipe 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동

### 확인된 문서·코드 차이

- 2026-10-11 팀원 메시지에서 관련성 120건 비교와 Hybrid 우선 개발 방향을 전달받았다. Hybrid 99/120(82.5%), Macro F1 0.732는 **팀원 보고값**이며 평가 데이터·재현 코드·결합식은 저장소에서 확인되지 않았다. 현재 관련성 분류기는 미구현이고, `evaluation_spec.md`와 결정 036은 구현 방향을 기록한 것이다.
- 팀원은 OCR `text`·`ocr` 전달, AI 전처리의 최종 `qualityScore`·`contentHash`, 관련성 출력 그대로 이탈·복귀 정책에 입력, Backend 9개 컬럼 협의 예정이라고 설명했다. 저장소 계약에는 제안으로 반영했으며 필드 하위 Schema·저장 컬럼·계산식은 미확정이다.
- 팀원 메시지상 담당 기능 6건의 수정 방향: 목표 설정 보조는 잘못 들어간 휴식 내용을 목표 명확성·사용자 확정으로 교체, OCR 추출은 JSON 책임 분리, OCR 품질은 실패·중복 책임 분리 및 ColPali 후속 PoC, MediaPipe는 60초 후보·90초 최종 게이트, 개인화 휴식 추천은 최근 7일·카메라 비필수·미루기/끄기, 이탈·복귀는 관련성 출력 소비·상태 분리·제외 모드·재집중 안내다. 이는 팀원 보고이며 여섯 Notion 페이지의 표시 본문과 대조가 남아 있다. 개인화 휴식 추천은 화면 관련성 판정 수정 결과를 저장하는 후속 개인화와 별개 기능이다.
- 브라우저에서 확인한 팀원 담당 [학습 목표 설정 보조](https://app.notion.com/p/1-3e70b6e5e64c8075801dcb72eb26ce0e), [OCR 텍스트 추출](https://app.notion.com/p/OCR-1-3e70b6e5e64c80ceb7e0d039a1fd8451), [OCR 품질 검증·실패·중복 처리](https://app.notion.com/p/OCR-1-3e70b6e5e64c80f396d1deed877300f6), [MediaPipe 기반 카메라 신호 처리](https://app.notion.com/p/MediaPipe-1-3e70b6e5e64c8099acdaed8d4b6a387f), [개인화 휴식 추천](https://app.notion.com/p/1-3e70b6e5e64c8024ad6ed2cf70516db5), [목표 이탈·복귀 판단](https://app.notion.com/p/1-3e70b6e5e64c80dda777ebecfd84dc56)의 표시 본문에는 팀원 메시지의 수정이 아직 확인되지 않는다. 예: 카메라 30초 알림, 목표 질문 무제한, ColPali 현행 사용, 이전 주 기준 휴식 추천 등이 남아 있다. 이 6개 페이지를 이미 최신화된 원본으로 취급하지 않는다.
- 최신 [AI 분석 계약](https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c)은 `REVIEW` 상태이며 목표 질문 최대 2회, 화면 이탈 임계값, 카메라 60초 후보·90초 최종 게이트를 제시한다. 팀원 담당 페이지와 충돌할 때 최종 팀 합의·Notion 게시 상태 확인이 필요하다.
- Notion [AI 분석 계약](https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c)의 `REVIEW` MVP 기준에 맞춰 화면 분석·카메라 게이트·개인정보·목표 질문 제한을 관련 문서에 정리했다. 관련성·이탈 흐름·카메라 분석은 아직 코드 골격/미구현이므로 문서 동기화는 구현 완료를 뜻하지 않는다.
- 목표 입력은 현재 `goalText` 최대 200자 설정을 유지하므로 MVP 목표 문장 2~40자 정합성 조정은 남아 있다. `previousAnswers` 최대 2개, `conversationTurn` 0~2 및 세 번째 AI 질문 차단은 Schema·Workflow에 반영했다. `clarification_answers_max_count=20` 로컬 설정이 있어도 Schema의 최대 2개 한도를 늘리지 못한다. 최초 JEV의 네 가지 typed 판단 항목은 사용자에게 던지는 AI 질문 횟수와 다른 개념이다.
- `docs/DATA_PRIVACY.md`의 제외 정책을 `EXCLUDED`(화면 분석 중지, 유효한 동의가 있으면 카메라 분석 가능)와 `PRIVACY_BLOCKED`(화면·카메라 모두 중단)로 구분했다. 이 정책의 Client·Server 구현은 아직 확인되지 않았다.
- `packages/shared-types`는 Notion 기준 공개 상태 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 정의한다. 내부 AI 세부 상태는 API 경계에서 변환한다.
- Extension Manifest에는 아직 `activeTab`, `nativeMessaging` 권한이 없고 Content Script·Service Worker는 골격 상태다.
- Server에는 실행 설정과 빌드 골격만 있고 목표·세션·이벤트·AI 연동 코드가 없다.
- AI의 목표 설정 보조 관련 `src/`, `tests/`, `evaluation/`은 구현됐고, 관련성·흐름·사용자 상태 파일은 여전히 골격이다.
- 루트 아키텍처는 Desktop 화면 분석을 기본으로 설명하고, 최신 AI 기획은 Chrome DOM·로컬 OCR을 기본으로 제안한다. 팀 합의 후 공통 문서를 갱신해야 한다.

## 현재 기술

- 언어: Python
- API 서버: FastAPI 0.141 계열
- Schema 검증: Pydantic 2.13 계열
- 테스트: pytest 9.1 계열, pytest-asyncio 1.4 계열
- 목표 보조 모델: TypeSafe 공식 API `jev-latest` 판단과 OpenAI 공식 API `gpt-5-mini` Structured Output 생성. 제공자별 일시 장애는 UI에 JEV 또는 OpenAI 서비스로 구분해 표시
- Workflow: LangGraph 1.2 계열, 요청 단위 실행·Checkpointer 없음
- 분석 대상: 화면·페이지에서 추출한 텍스트와 메타데이터
- OCR: Extension 서비스 워커와 macOS 네이티브 모듈의 Apple Vision 보조 경로 제안; AI는 허용된 정제 텍스트만 평가
- 시각 분석: ColPali는 PDF·Canvas·이미지·슬라이드 대상 비교 실험 단계
- 카메라 분석: MediaPipe 클라이언트 결과값만 후속 사용자 상태 분석에 사용
- 원본 저장: 원본 화면·카메라 영상은 기본 저장하지 않음

## 예상 데이터 흐름

```text
Extension 사전 제외·권한 검사
  → DOM 우선 추출
  → 정보 부족 시 macOS 로컬 Apple Vision OCR
  → 개인정보·품질 검사
  → Spring Server 인증·소유권·최신 버전 검증
  → AI 텍스트 정제
  → 규칙 기반 판단
  → 임베딩 판단
  → 애매한 경우 LLM 또는 FocusSessionAgent 판단
  → 관련성·흐름·행동 상태 분리
  → Server 결과 저장
  → Extension·웹 대시보드 상태·알림·기록 표시
```

## 반드시 지켜야 하는 조건

- 입력 데이터에 비밀번호·결제 정보·개인 메시지가 포함되지 않도록 확인한다.
- 원본 화면·카메라 영상을 기본 저장하지 않는다.
- OCR 실행과 원본 이미지 처리는 AI 서버 담당 범위에 포함하지 않는다.
- Server 또는 외부 ColPali로 원본 이미지를 전송하려면 현재 개인정보 결정 변경이 먼저 필요하다.
- 관련성 분석 API에는 체류 시간과 MediaPipe 상태값을 섞지 않는다.
- 학습 흐름과 사용자 상태는 별도 분석 기능으로 구현한다.
- 추출·분석·관련성·흐름·행동 상태를 서로 다른 필드로 관리하는 안을 검토한다.
- `OFF_TASK`만으로 `DRIFT_RISK`, 알림 또는 차단을 결정하지 않는다.
- 판단이 어렵거나 실패한 경우 목표 무관이나 이탈로 단정하지 않는다.
- 규칙이나 임베딩으로 충분히 판단 가능한 요청에는 LLM을 호출하지 않는다.
- 모델·프롬프트·임계값 변경은 `DECISION_RECORD.md`에 기록한다.
- 분석 결과에는 최소한 상태값과 신뢰도 기준을 포함한다.
- 오래된 목표·회차·탭 결과는 적용 전에 폐기한다.
- 같은 이벤트·세션 명령·노트 작업의 중복 처리는 멱등해야 한다.
- 화면 판정 정정 피드백 우선 적용은 후속 개인화 제안이며 MVP에 포함하지 않는다. 목표 문장 수정은 별도 GoalProfile 흐름이다.
- 학습 시간은 Server가 계산하고 LLM이 추정하지 않는다.
- 학습 노트는 관찰 근거 없이 이해·해결 완료를 주장하지 않는다.
- 회차 종료와 시간 저장은 학습 노트 생성 성공에 의존하지 않는다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 작성한다.

## 작업 종료 시 갱신

- 완료·진행 중·미착수 작업
- 입력·출력 계약 변경
- 모델·프롬프트·임계값 결정
- 발생한 오류와 평가 결과
- 다음 작업과 선행 조건
