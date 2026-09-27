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
- 구현 범위: JEV 판단, OpenAI 생성, LangGraph Workflow, 내부 FastAPI Endpoint, Fake 기반 테스트, 선택형 실제 평가 Script
- 구현 제외: 목표 저장, 공개 Server API, Desktop·Extension UI, 장기 대화 저장
- 구현한 OCR·텍스트 정제: 목표 입력 공백·길이·답변 개수 검증만 수행; OCR·페이지 입력은 범위 밖
- 구현한 규칙 기반 판단: JEV 확률에 대한 고정 우선순위와 환경변수 Threshold
- 입력·출력 변경: camelCase 요청·응답, 6개 `clarityStatus`, 후보·질문·GoalProfile, 공통 오류 형식 추가
- 기획과 달라진 내용: 없음
- 구현하지 못한 내용: 승인된 데이터의 실제 외부 모델 평가, Server 인증·연동, 배포

## 변경 파일

| 파일 경로 | 변경 내용 |
| --- | --- |
| `AI/src/analysis/goal_analyzer.py` | JEV 평가 타입과 결정적 Router |
| `AI/src/models/jev_client.py` | TypeSafe SDK 판단·검수 Adapter |
| `AI/src/models/llm_client.py` | OpenAI Structured Output 생성·Schema Repair |
| `AI/src/workflow/goal_assistance.py` | Stateless LangGraph 분기·검수·1회 Repair |
| `AI/src/api/schemas.py`, `routes.py`, `main.py` | 내부 API, camelCase Schema, 오류 처리 |
| `AI/tests/` | Router·Workflow·API·SDK 계약·로그 테스트 |
| `AI/evaluation/` | Offline Fixture와 명시적 Live JEV 평가 |
| `AI/docs/` | API·Prompt·평가·결정·상태 문서 갱신 |

## 테스트 결과

- 정적 검사: `python -m compileall -q src` 통과
- 단위·API·Workflow 테스트: 27개 통과
- Offline 평가: 7개 Fixture 통과
- 실제 외부 모델 평가: 미실행

## 주요 결과

- 네 가지 최초 JEV 질문을 한 요청에서 실행하고 결정적 우선순위로 6개 상태를 선택한다.
- OpenAI Structured Output으로 후보·질문·GoalProfile만 생성하며 상태를 결정하지 않는다.
- 후보별·GoalProfile별 세 가지 JEV 검수와 최대 1회 Repair, 두 번째 실패 시 질문 fallback을 적용했다.
- 후보 선택과 질문 답변을 새 요청으로 받아 전체 판단을 처음부터 다시 수행한다.
- camelCase 내부 API, 동적 길이 검증, 422·502·503 공통 오류 계약을 구현했다.
- Fake Client 기본 테스트와 외부 호출을 명시적으로 잠그는 Live 평가 Script를 분리했다.
- 로그 Utility는 허용된 운영 메타데이터 외 목표·답변·모델 원문과 인증 값을 폐기한다.

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
- [ ] 실제 Server·Client 통합

## 평가 결과

- 평가 데이터: `evaluation/evaluation_cases.json` 합성 Fixture
- 전체 케이스 수: 7
- 정답 케이스 수: 7
- 오판 케이스 수: 0
- 대상 상태: 6개 `clarityStatus`와 Prompt Injection 1개
- 외부 모델 호출률: 기본 테스트·Offline 평가 0%
- 실제 모델 Precision·Recall·fallback 비율: 미측정

## 성능 및 제한사항

- 단위·Offline 평가 시간: 로컬에서 약 1초 이내
- 실제 JEV·OpenAI 응답 시간·비용: 미측정
- 후보 검수는 Schema상 최대 3개를 `asyncio.gather`로 병렬 실행한다.
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
