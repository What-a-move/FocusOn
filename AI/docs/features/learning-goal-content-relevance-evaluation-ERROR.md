# 통합 #11 개발 중 오류 기록

- 기능명: 입력 검증·전처리 및 Hybrid 관련성 판단
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)
- Branch: `feat/11-ai-preprocessing-relevance`

## 오류 001 — 평가 CLI가 정상 보류 사례에서도 실패 종료

- 발생일: 2026-10-07
- 상태: 해결
- 실행 환경: macOS / Python 3.11.6 / Fake Client
- 재현: 개발 합성 세트에 메뉴만 남은 입력과 검은 화면 신호를 포함해 fake 평가 실행.
- 기대: 두 사례를 `UNCERTAIN`으로 처리하고 기대 라벨 일치 시 정상 종료.
- 실제: 8/8 라벨이 일치했지만 종료 코드 2.
- 원인: 품질 부족을 일괄 `FAILED` 처리하고 모든 실패 상태를 CLI 실행 실패로 판단했다.
- 해결: 비분석 품질·캡처 경로를 `SKIPPED`로 구분하고 모델 장애와 알려진 추출 실패를 구분한다. 캡처 상세 사유를 유지한다.
- 변경: `relevance_analyzer.py`, `relevance_eval.py`
- 검증: 개발·평가 split fake CLI 종료 코드와 모델 미호출 회귀 테스트.
- 평가 영향: 실제 모델 정확도에 대한 결과 없음.

## 오류 002 — 캐시 테스트 정리 코드가 다른 테스트로 이동

- 발생일: 2026-10-07
- 상태: 해결
- 실행 환경: macOS / Python 3.11.6 / pytest
- 재현: `test_relevance_pipeline.py` 전체 실행.
- 기대: 목표 정보 없는 사례가 `GOAL_GRAPH_UNAVAILABLE`로 안전 종료.
- 실제: 기대 판정 이후 테스트 코드에서 `NameError: name 'cache' is not defined`.
- 원인: 테스트 추가 시 캐시 clear 검증 두 줄이 마지막 테스트에 남았다.
- 해결: 해당 검증을 TTL/LRU 테스트로 이동.
- 변경: `AI/tests/test_relevance_pipeline.py`
- 검증: 전체 테스트 재실행 결과는 REPORT에 기록한다.
- 평가 영향: 애플리케이션 판정 코드에는 영향 없음.
