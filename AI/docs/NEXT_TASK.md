# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Issue #12 목표 설정 보조 Server 연동 계약 확정
- 담당 영역: AI
- 우선순위: 높음
- 상태: AI 내부 구현 완료, 연동 전 검토 필요

## 작업 목적

구현된 `POST /internal/v1/goals/clarify`를 Server가 안전하게 호출하도록 서비스 인증, Timeout 예산, 배포 환경과 공개 API 변환 계약을 확정한다. AI는 목표 저장·세션 시작·사용자 확정을 담당하지 않는다.

## 선행 조건

- 로컬 `AI/.env`에 `TYPESAFE_API_KEY`와 `OPENAI_API_KEY`를 설정한 뒤 JEV 판단 표, 질문·추천 생성, 후보별 순차 JEV 검수의 Live 흐름을 각각 최소 1회 검증. 이때 `React Query에서 캐시 무효화 구현하기`와 구체성 2.00 이상 목표는 `CLEAR`, 구체성 1.00 이상 2.00 미만 목표는 `NEEDS_SUGGESTION`, 핵심 주제 또는 활동이 없는 목표는 `NEEDS_QUESTION`, 한국어 약어·오타·다중 목표·무효 입력은 기존 우선순위 상태인지 확인한다. 질문 답변 뒤 `interpretedGoal`과 추천 제목이 답변의 주제·활동·범위를 반영하는지도 확인한다.
- `laya` 패키지와 `convaiinnovations/laya-multilingual` 체크포인트를 설치한 뒤 비교 표가 같은 네 판단 항목을 표시하는지 확인한다. Laya 응답·실패 여부와 무관하게 JEV `clarityStatus`, 질문·추천, 저장 경로가 동일한지 확인한다.
- Laya Multilingual 기본 모델은 목표 분류 Live sanity check에서 단일 목표도 복수 목표로 높게 응답해, 현재 값은 원시 실험값으로만 표시한다. 제품 판단에 사용하려면 한국어 목표 라벨 데이터로 fine-tune·확률 보정·holdout 평가를 별도 수행한다.
- 루트 `docs/API_CONTRACT.md` 확인
- `DEVELOPMENT_RULES.md`, `state_model.md`, `data_lifecycle.md`, `evaluation_spec.md` 확인
- `goal_session_spec.md`의 식별자·회차·멱등·시간 집계 경계 확인
- `content_acquisition_spec.md`의 AI 입력과 비분석 상태 계약 확인
- `focus_session_agent_spec.md`, `feedback_personalization_spec.md`, `session_note_spec.md`는 후속 구현 경계로 사용
- Desktop·Extension에서 전달할 최소 데이터 확인
- Server의 AI 요청·결과 저장 범위 확인
- 원본 화면·카메라 영상 미저장 원칙 확인
- DOM 우선·Apple Vision OCR 보조 경로와 AI 서버의 원본 이미지 비수신 경계 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 관련 기능의 PLAN·ERROR·REPORT 파일 준비

## 예상 작업 순서

1. Server→AI 서비스 인증과 내부 네트워크 경계를 확정한다.
2. Server 공개 `POST /api/v1/goals/clarify`와 내부 응답 변환을 확정한다.
3. 전체 Timeout·Retry·멱등성 예산과 `requestId` 전달 규칙을 확정한다.
4. `AI-PRIV-003` 외부 제공자·지역·보존·학습 이용 조건 승인을 완료한다.
5. 승인된 비식별 평가 데이터로 JEV Threshold와 OpenAI 생성 품질을 조정한다.
6. 통합 환경에서 원문·답변·모델 원본 출력이 로그와 Trace에 남지 않는지 다시 검사한다.

## 이후 작업 후보

- Server 관련성 분석 요청·결과 저장 연동
- DOM·Apple Vision OCR 왕복 PoC와 실패 경로 검증
- ColPali 시각 콘텐츠 기준선 비교 PoC
- 페이지 이동·체류 시간 기반 학습 흐름·목표 이탈 분석
- MediaPipe 얼굴·시선·자세 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동
- 평가 자동화와 모델·Prompt Version 관리

## 완료 조건

- [x] 기능 기획서를 작성했다.
- [x] AI 내부 입력·출력 계약을 문서화했다.
- [x] Health Check와 공통 응답 Schema가 동작한다.
- [ ] 다중 상태 또는 호환 상태 계약이 문서와 Schema에서 일치한다.
- [x] 외부 모델 없이 Fake 기반 테스트가 실행된다.
- [x] 개인정보·민감 데이터 제외 기준을 확인했다.
- [x] 실행 명령과 의존성을 문서화했다.
- [x] 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DEVELOPMENT_RULES.md`와 적용 Rule ID를 확인했다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] Desktop·Extension·Server 담당자와 계약을 공유했다.
