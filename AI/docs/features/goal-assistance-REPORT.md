# 학습 목표 설정 보조 결과 리포트

> 구현과 검증이 끝난 뒤 실제 결과를 기록한다.

## 기본 정보

- 기능명: 학습 목표 설정 보조
- 기능 ID: `goal-assistance`
- 작성자: Codex
- 작성일: 2026-09-27
- 관련 기획서: `AI/docs/features/goal-assistance-PLAN.md`
- 관련 Issue·PR: [#12](https://github.com/What-a-move/FocusOn/issues/12)
- 관련 Branch: `feat/12-ai-goal-assistance`
- 모델·프롬프트 버전: JEV `jev-latest`, OpenAI 환경 설정 모델, 목표 Prompt v1

## 구현 결과

- 구현 상태: AI 영역 구현 및 로컬 검증 완료
- 구현 범위: JEV 판단, OpenAI 생성, TypeSafe·OpenAI 직접 API Key 설정, LangGraph Workflow, 내부 FastAPI Endpoint, Fake 기반 테스트, 선택형 실제 평가 Script, 로컬 Streamlit 테스트 화면
- 구현 제외: 목표 저장, 공개 Server API, Desktop·Extension 제품 UI, 장기 대화 저장
- 구현한 OCR·텍스트 정제: 목표 입력 공백·길이·답변 개수 검증만 수행; OCR·페이지 입력은 범위 밖
- 구현한 규칙 기반 판단: JEV 확률에 대한 고정 우선순위와 환경변수 Threshold. 하나의 핵심 주제와 학습 활동을 파악할 수 있으면 세부 범위·완료 조건·방법이 없어도 `CLEAR`로 허용
- 입력·출력 변경: camelCase 요청·응답, 6개 `clarityStatus`, 후보·질문·GoalProfile, 공통 오류 형식 추가
- 기획과 달라진 내용: 없음
- 구현하지 못한 내용: 승인된 데이터의 실제 외부 모델 평가, Server 인증·연동, 배포

## 변경 파일

| 파일 경로 | 변경 내용 |
| --- | --- |
| `AI/src/analysis/goal_analyzer.py` | JEV 평가 타입과 결정적 Router |
| `AI/src/models/jev_client.py` | TypeSafe SDK 판단·검수 Adapter |
| `AI/src/models/llm_client.py` | OpenAI Structured Output 생성·Schema Repair |
| `AI/src/config.py` | TypeSafe·OpenAI 직접 제공자 설정 |
| `AI/src/workflow/goal_assistance.py` | Stateless LangGraph 분기·검수·1회 Repair |
| `AI/src/api/schemas.py`, `routes.py`, `main.py` | 내부 API, camelCase Schema, 오류 처리 |
| `AI/tests/` | Router·Workflow·API·SDK 계약·로그 테스트 |
| `AI/evaluation/` | Offline Fixture와 명시적 Live JEV 평가 |
| `AI/streamlit_app.py` | 실제 Workflow 수동 테스트 화면 |
| `AI/docs/` | API·Prompt·평가·결정·상태 문서 갱신 |

## 테스트 결과

- 정적 검사: `python -m compileall -q src` 통과
- 단위·API·Workflow·Streamlit 렌더·저장 조건·직접 제공자 설정 테스트: 43개 통과
- Offline 평가: 15개 Fixture 통과
- Streamlit 기동 검사: `127.0.0.1:8501/_stcore/health` 응답 `ok`
- 실행 환경: 사용자 pyenv 가상환경 `what-a-move` (Python 3.11.6)
- 실제 외부 모델 평가: 현재 `TYPESAFE_API_KEY`가 TypeSafe 공식 API에서 401을 반환해 새 Key 입력 후 별도 Live 평가가 필요함

## 주요 결과

- 사용자 요청에 따라 JEV 최초 구현의 네 가지 질문과 영어 instructions·criteria로 복원했다. 강한 무효·약어·복수 목표 판정 뒤에는 학습 목표 적합도 0.60, 구체성 신뢰도 0.45, 구체성 2.00/1.00 threshold로 `CLEAR`·추천·질문 경로를 결정한다.
- OpenAI Structured Output으로 후보·질문·GoalProfile만 생성하며 상태를 결정하지 않는다.
- 후보별·GoalProfile별 세 가지 JEV 검수와 최대 1회 Repair, 두 번째 실패 시 질문 fallback을 적용했다.
- 후보 선택과 질문 답변을 새 요청으로 받아 전체 판단을 처음부터 다시 수행한다. 최신 답변에 주제·활동·범위·결과가 있으면 추천 후보와 최종 GoalProfile의 해석된 목표에 반영한다.
- camelCase 내부 API, 동적 길이 검증, 422·502·503 공통 오류 계약을 구현했다.
- Fake Client 기본 테스트와 외부 호출을 명시적으로 잠그는 Live 평가 Script를 분리했다.
- 로그 Utility는 허용된 운영 메타데이터 외 목표·답변·모델 원문과 인증 값을 폐기한다.
- Streamlit에서 `목표 저장` 클릭 시 선분석, 추천 후보 선택, `BROAD` 원문 직접 시작, 빠른 선택지·직접 답변, `CLEAR` 최종 확인 후 세션 메모리 저장, 결과 JSON 확인을 지원한다.
- `TYPESAFE_API_KEY`는 JEV 판단에, `OPENAI_API_KEY`는 질문·추천·GoalProfile 생성에 각각 직접 사용한다.
- 학습 목표 성립 가능성은 구체성·난이도·계획 품질과 분리해 넓은 정상 목표도 보수적으로 낮게 채점하지 않도록 JEV 지침을 보강했다. 질문·추천·GoalProfile 생성에는 최소 추론·낮은 응답 길이 설정을 적용했다.
- Streamlit은 Laya Multilingual에 JEV와 같은 state·네 typed question을 전달해 비교 표로만 보여 준다. Laya는 상태 Router, 질문·추천, GoalProfile, 저장 흐름에 영향을 주지 않는다.
- Laya Live sanity check는 호출·응답 매핑은 확인했지만 단일 한국어 목표를 복수 목표로 높게 응답했다. 따라서 Laya 열은 `원시값 (실험용)`으로 표시하고 JEV 확률과 직접 비교하지 않도록 경고한다.

## 기능 테스트

- [x] 정상 입력과 6개 상태
- [x] 필수값 누락·공백
- [x] 길이·개수 초과
- [x] JEV·OpenAI 장애와 안전한 오류
- [x] 신뢰도 부족 질문 경로
- [x] 로그 민감 데이터 제외
- [x] JEV 판단·OpenAI 생성·Router 단계 분리
- [x] Prompt Injection을 데이터로 취급하는 Prompt·Fixture
- [x] 생성 결과 후보별 검수·1회 Repair·fallback
- [x] Streamlit 초기 렌더와 외부 호출 없는 기본 동작
- [ ] 실제 Server·Client 통합

## 평가 결과

- 평가 데이터: `evaluation/evaluation_cases.json` 합성 Fixture
- 전체 케이스 수: 14
- 정답 케이스 수: 14
- 오판 케이스 수: 0
- 대상 상태: `CLEAR`, `NEEDS_QUESTION`, `NEEDS_SELECTION`, `UNRECOGNIZED_TERM`, `INVALID`와 Prompt Injection
- 외부 모델 호출률: 기본 테스트·Offline 평가 0%
- 실제 모델 Precision·Recall·fallback 비율: 미측정

## 성능 및 제한사항

- 단위·Offline 평가 시간: 로컬에서 약 1초 이내
- 실제 JEV·OpenAI 응답 시간·비용: 미측정
- 후보 검수는 Schema상 최대 3개를 순차 실행한다.
- JEV·OpenAI Timeout은 기본 10초, 재시도는 기본 최대 2회이며 환경변수로 조정한다.
- Threshold는 초기 실험값으로 승인된 평가 데이터에서 보정해야 한다.

## 오류 기록

- 관련 문서: `AI/docs/features/goal-assistance-ERROR.md`
- 해결된 오류: TypeSafe 예외 Import, API 검증 전 Client 생성, 평가 Module 경로
- 남은 오류: 없음; 실제 Provider 연동은 아직 평가하지 않음

## 문서 갱신 확인

- [x] `CONTEXT.md` 갱신
- [x] `NEXT_TASK.md` 갱신
- [x] `DECISION_RECORD.md` 결정 015 기록
- [x] API·Prompt·평가 계약 문서 갱신
- [ ] PR 연결

## 제한사항

- `AI-PRIV-003` 외부 제공자·지역·보존·학습 이용 조건 승인 전 배포하지 않는다.
- 내부 Endpoint 인증 계약이 확정되기 전 로컬·테스트 용도로만 사용한다.
