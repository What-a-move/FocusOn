# FocusOn Hybrid 관련성 평가 안내

2026-10-28 미사용 Graph 생성·비교 실행 코드를 제거했다. 현재 평가 경로는 `streamlit_runner.compare_graphless`와 `graphless_hybrid`다. 이전 1~7번 방식과 Graph 기반 평가 CLI는 지원하지 않는다. 과거 실험의 결과 파일·보고서는 이력으로 남기며 현재 실행법이나 운영 정확도로 해석하지 않는다.

## 테스트 순서

1. [AI README](../README.md)의 의존성·Streamlit 실행 명령을 따른다.
2. 확정 목표를 입력하고 동일한 `goal.text`를 선언한 JSON 또는 단일 추출 JSON을 넣는다. 기대 라벨은 모델 호출 전에 정한다.
3. APP_META·TAB_META, CHROME_DOM, DESKTOP_APP_OCR을 나누어 확인한다. 메타데이터 non-RELATED는 최종 무관 판정이 아니며 본문 재확인이 필요하다.
4. 결과의 라벨·reasonCode·실제 근거 문단, 원래 본문 V·키워드 K·결합 H, 문맥 점수, 관련/무관 커버리지 분자·분모를 확인한다.
5. 처리 실패·민감/제외·잘림·낮은 품질과 의미적 UNCERTAIN을 구분한다. 실패는 무관 판정 또는 정답 보류로 세지 않는다.
6. 전체·API 왕복·로컬 시간, 요청 수·cacheHit를 함께 기록한다. 재사용 벡터·모델 로딩·최초 Kiwi 로딩을 구분한다.

## 평가 기준

입력·라벨·임계값을 같은 평가 결과에 맞춰 함께 바꾸지 않는다. 개발 목표에서 선택한 설정은 다른 목표 평가 전에 고정한다. 정확도 외에 3라벨 혼동행렬, 라벨별 정밀도·재현율, Macro F1, RELATED→UNRELATED 오탐과 보류율을 기록한다. 합성 기대 일치율은 실제 사용자 화면의 독립 정확도가 아니다.

현재 점수/근거 버전과 고정 수치는 [README](../README.md)와 `graphless-calibrated-config.json`, [보정 결과 보고서](../docs/features/graphless-hybrid-calibration-REPORT.md)를 따른다. 이번 삭제 작업은 기존 수치·기대 라벨·본문을 재보정하지 않는다.

개발 보정 CLI:

```bash
PYTHONPATH=AI:. python3 -m AI.evaluation.calibrate_graphless --help
```

자동 회귀:

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q
```

현재 도구는 실제 Client 수집·Server 인증·공개 HTTP 분석 API·알림을 실행하지 않는다. 공개 confidence 보정과 제품 통합 검증은 별도로 필요하다.
