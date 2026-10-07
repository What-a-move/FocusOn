# 입력 검증·전처리 및 유사도 실험 코드 게시 결과

## 기본 정보

- 기능명: 입력 검증·전처리 및 학습 목표·콘텐츠 관련성 판단
- 기능 ID: relevance-analysis
- 작성자: 이재빈 작업 공간 / Codex 검증
- 작성일: 2026-10-07
- 관련 기획서: [통합 PLAN](learning-goal-content-relevance-evaluation-PLAN.md)
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)
- 관련 Branch: `feat/11-ai-preprocessing-relevance`
- 상태: 기존 로컬 코드 게시·부분 구현 검증. 전체 기능 완료가 아니다.

## 구현 결과

- DOM/OCR 내부 입력 모델과 문단/코드/표 순서를 보존하는 정제·청크·해시 모듈을 게시한다.
- 개인정보·추출 실패·품질 부족·중복·일부 run/목표/navigation stale 검증을 수행한다.
- 기존 GoalProfile과 합성 본문으로 후보 1~7을 비교하는 Streamlit 실험 코드를 보존한다. 현재 MVP 채택은 Hybrid 6번이며 실험 화면을 운영 판정기로 사용하지 않는다.
- 합의된 공개 API·현재 실행 문맥 Adapter·최종 품질 산식·운영 Graph/Hybrid·원격 임베딩 서버 연결은 아직 구현 완료가 아니다.

## 변경 파일

- `AI/src/preprocessing/models.py`, `content_parser.py`, `text_cleaner.py`, `validator.py`
- `AI/tests/test_content_parser.py`, `test_text_cleaner.py`, `test_preprocessing_validator.py`, `test_similarity_streamlit.py`
- `AI/evaluation/similarity_methods.py`, `similarity_streamlit.py`, 합성 사례·실험 의존성·HTML 비교 도구·평가 지침
- AI 문서의 현재 상태·다음 작업 및 이 결과 리포트

## 기능 테스트

Python 3.11.6 환경에서 다음 모델 없는 테스트를 실제 실행했다.

```bash
PYTHONPATH=AI python3 -m pytest AI/tests/test_content_parser.py AI/tests/test_text_cleaner.py AI/tests/test_preprocessing_validator.py AI/tests/test_similarity_streamlit.py -q
```

- 결과: 30 passed, 6 subtests passed.
- 정제·청크·중복·해시·민감 후보·추출 실패·품질 부족·버전 불일치와 실험 계산 경계를 검증한다.
- 임베딩·재정렬 관련 테스트는 Fake Client를 사용한다. 실제 모델 정확도·GPU 지연·네트워크 왕복·Server 인증을 검증한 결과가 아니다.
- Streamlit 실제 브라우저 실행·GPU 모델 추론은 이번 게시 작업에서 실행하지 않았다.

## 평가 결과·제한사항

- 고정 테스트의 안전 로직 통과는 실제 모델 정확도나 MVP 출시 기준 충족을 뜻하지 않는다.
- 내부 quality_score는 현재 입력값을 품질 검사에 사용한다. 최신 PLAN의 '전처리에서 최종 qualityScore 산출' 산식과 입력 Adapter 연결은 후속 작업이다.
- 내부 CurrentAnalysisContext는 일부 run/goalVersion/navigation 비교를 수행한다. Server 검증 문맥·sessionStatus/exclusionMode·관찰 시각·응답 직전 재검증 전체가 구현된 것은 아니다.
- Streamlit은 이전 기본 모델 `intfloat/multilingual-e5-small`과 선택적 `BAAI/bge-reranker-v2-m3`를 사용한다. 해당 코드가 Qwen3 4B·FP16 또는 현재 Hybrid 6번 운영 코드로 바뀐 것은 아니다.
- 후보 비교 도구의 제한된 직접/보조 Graph와 임시 점수 규칙은 실험 이력이다. 선수 관계·관계 근거·정규화·최종 임계값은 최신 PLAN 기준으로 보완한다.
- 로딩/실행 시간, Precision/Recall/Macro-F1·P50/P95 및 사용자 전달 328ms는 이번 로컬 테스트가 새로 검증한 수치가 아니다.
- 최근 활동 보완, 정책 계층·알림, 원격 임베딩 Client·캐시, FastAPI·Server 연동은 미완료다.

## 보안·오류·문서 확인

- 게시 대상 소스에서 알려진 비밀 토큰 패턴을 검사했으며 자격 증명 값을 출력하지 않았다.
- 기존 ZIP 패키지와 개인 LangChain 호출 스크립트는 공개 코드 커밋에 넣지 않고 로컬에 보존한다.
- 모델 파일·가상환경·.env는 게시 대상이 아니다. 테스트 Fixture는 합성 데이터다.
- 테스트는 실제 실행 결과만 기록한다. 원문·비밀 값·모의 정확도는 결과로 남기지 않는다.
- CONTEXT·NEXT_TASK에는 부분 구현과 후속 작업 경계를 유지한다.

## 최신 dev 통합

- dev의 AI 계약 문서 커밋을 함께 가져오고 로컬 구현·학교 지침을 보존했다.
- 결정 번호 중복은 dev의 015~019를 유지하고 이 Branch 기록을 020·021로 옮겨 해결했다.
- 모델 실행 방식·공유 API·품질 산식을 이번 게시 작업에서 새로 확정하지 않았다.
