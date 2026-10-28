# 화면 분석 10/26 계약 정합성 PLAN

- 기준: 사용자가 전달한 2026-10-26 AI 분석 계약·화면 분석 첨부본, 기존 AI Issue #11 작업 범위.
- 원본 담당 문서의 상태는 REVIEW다. 이번 구현은 사용자 승인으로 해당 입력·판정 경계를 AI에 적용한다.
- 범위: AI 내부 분석기와 Graphless Streamlit, 공개 JSON 변환·응답 검증. Desktop/Extension/Server 파일과 Neo4j 실험 복사본은 대상이 아니다.

## 완료 조건

1. 메타데이터는 RELATED만 확정하며 무관 후보·모호한 입력은 UNCERTAIN 후 Chrome DOM/Desktop OCR을 요청한다. 정책 차단·stale·종료/실행 실패를 추가 수집으로 우회하지 않는다.
2. Chrome DOM은 MVP 마지막 수집 단계다. CHROME_VIEWPORT_OCR enum은 유지하되 실행 요청은 모델 호출 없이 UNCERTAIN으로 종료한다.
3. DOM/OCR 입력은 2,000자 이하다. 초과 입력을 조용히 자르지 않는다. 민감정보 검사는 유지한다. 클라이언트 삭제 이후 잔존 민감값은 AI 호출 전에 차단한다.
4. 계약의 metadata/contentPayload/goalSnapshot을 Server 검증 문맥과 명시적으로 결합한다. 클라이언트 값으로 내부 실행 상태를 생성하지 않는다.
5. 공개 decision/reasonCode/modelVersion/promptVersion/confidence를 검증한다. 내부 사유는 진단용으로 보존한다. cosine·Hybrid·OCR confidence를 판정 confidence로 전용하지 않는다. 검증된 판정 confidence가 없거나 0.75 미만이면 UNCERTAIN이다.
6. 기존 단위·통합 회귀에서 차단·stale·모델 실패가 무관 확정으로 바뀌지 않는지 확인한다. 합성 정답/점수 임계값과 과거 결과 파일은 수정하지 않는다.

## 남는 연동 조건

- Server가 현재 기기/출처 간 최신 관측·세션·정책 문맥을 제공하고, 공개 요청을 Adapter로 연결해야 한다.
- Desktop OCR 품질 메타데이터의 Server 전달 위치는 첨부 공개 JSON에 없으므로 계약 합의가 필요하다. 임의 confidence를 채우지 않는다.
- 판정 confidence의 보정/검증은 별도 평가가 필요하다. null을 허용하는 UNCERTAIN만 안전하게 발행하는 경계를 구현한다.
- 타이머/개입 Policy Engine·heartbeat·실제 창 캡처·민감정보 클라이언트 삭제·FastAPI 배포의 실행 완료를 이번 코드 회귀로 주장하지 않는다.
