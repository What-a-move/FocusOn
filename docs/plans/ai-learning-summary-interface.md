## 목적과 범위
#29 합성 데이터 전용 AI 학습 정리 코어에서 서버가 제공할 RELATED 근거 입력과 결과를 시험한다. 실제 사용자 데이터 전송은 보류한다. AI 영역만 수정한다.

## 입출력 계약
목표 2~40자, 기록 동의 및 concept/observation/label/page_type 근거 목록을 받아 READY/INSUFFICIENT_DATA/FAILED와 overview, keyConcepts, nextStartPrompt를 반환한다. 내부 근거 ID는 출력하지 않는다. 목표·근거·출력의 민감값 검사를 유지한다.

## 실패·예산 정책
동의가 없거나 중복 제거된 RELATED 근거가 2개 미만이면 모델을 호출하지 않는다. 근거 최대 20개, 항목 120자, 직렬화 입력 2400자, 출력 1200자, 모델 timeout 20초 및 재시도 0이다. 생성 실패는 FAILED다. 실제 데이터 및 HTTP 노출은 계약·제공자·보존·고지 승인 후 연결한다.

## 기계가 판별 가능한 완료 조건
합성 입력으로 READY, 중복·민감정보·비RELATED·동의 철회에 따른 INSUFFICIENT_DATA, 모델 실패 FAILED, 출력 검증 실패를 확인한다. 기존 synthetic DOM API와 테스트 호환을 유지한다. PYTHONPATH=AI:. PYENV_VERSION=what-a-move pyenv exec python -m pytest AI/tests/test_learning_summary_demo.py AI/tests/test_learning_summary_openai.py -q 및 loop-engineering ai profile을 실행한다.
