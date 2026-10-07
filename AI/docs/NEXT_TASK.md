# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: 통합 #11의 최신 계약 검토·입력 Adapter 및 Hybrid 관련성 연결
- 담당 영역: AI
- 우선순위: 높음
- 상태: 순수 전처리 테스트 통과, 공개 계약·최종 품질·관련성 운영 연동 미완료

## 작업 목적

Issue #10에서 확정한 Client·Server JSON 필드명과 enum을 Issue #11의 내부 전처리 모델에 명시적으로 변환한다. 외부 계약 변경이 내부 정제·검증 로직에 직접 번지지 않도록 Adapter 경계에서 처리한다.

## 선행 조건

- Issue #10에서 외부 JSON 필드명과 enum을 확정한다.
- Issue #2에서 FastAPI·Pydantic 요청 경계를 구현한다.
- `PreprocessingRequest` 등 Issue #11 내부 모델은 외부 JSON 이름과 직접 결합하지 않는다.
- 문단당 최대 800자와 긴 문단 청크 1,000~1,500자 중 최종 출력 단위를 계약에서 확정한다.
- 기존 개인정보·실패·중복·stale 단위 테스트를 그대로 통과해야 한다.


### 최신 dev 계약 확인 항목

- 루트 `docs/API_CONTRACT.md` 확인
- `DEVELOPMENT_RULES.md`, `state_model.md`, `data_lifecycle.md`, `evaluation_spec.md` 확인
- `goal_session_spec.md`의 식별자·회차·멱등·시간 집계 경계 확인
- `content_acquisition_spec.md`의 AI 입력과 비분석 상태 계약 확인
- `focus_session_agent_spec.md`, `feedback_personalization_spec.md`, `session_note_spec.md`는 후속 구현 경계로 사용
- Desktop·Extension에서 전달할 최소 데이터 확인
- Server의 AI 요청·결과 저장 범위 확인
- 원본 화면·카메라 영상 미저장 원칙 확인
- Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로, AI 서버의 원본 이미지 비수신 경계 확인
- Chrome Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로를 분리 확인
- 목표 보조 6개 `clarityStatus`, 추천·질문·직접 입력 장애 대안 확인
- `ANALYSIS_ONLY`와 `ANALYSIS_AND_TIME`, `sessionStatus`, `resumeRequired` 계약 확인
- AI 내부 라벨과 제품 표시 라벨 매핑 확인
- 개인화 휴식의 최근 7일 규칙과 카메라 비필수 경로 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 관련 기능의 PLAN·ERROR·REPORT 파일 준비


## 예상 작업 순서

1. Issue #10의 확정 JSON 예시와 공유 enum을 확인한다.
2. 외부 요청 Schema에서 내부 `PreprocessingRequest`로 변환하는 Adapter를 구현한다.
3. 알 수 없는 필드·enum·잘못된 식별자와 범위를 안전하게 거부한다.
4. Adapter가 원문 값을 오류 메시지나 로그에 포함하지 않는지 확인한다.
5. Issue #2의 API 경계에 연결하고 계약 테스트를 실행한다.

### Server·Client 공통 계약 작업


1. `/api/v1/analyze/relevance`, `/api/v1/feedback`, `/api/v1/goals/profile`의 AI 경계를 Server·Client 담당자와 확정한다.
2. AI 내부 라벨과 제품 표시 라벨, 기존 `FocusState` 호환 전략을 공유 타입 담당자와 확정한다.
3. `goalId`, `sessionId`, `runId`, `eventId`, `navigationId`와 `sessionStatus`, `exclusionMode`, `resumeRequired`의 필수 범위를 확정한다.
4. 목표 보조 6개 상태와 AI 장애 시 직접 목표 시작 경로를 확정한다.
5. FastAPI, Pydantic, pytest와 서비스 인증·설정 주입 방식을 확정한다.
6. Health Check, 공통 성공·실패 envelope, Schema 검증 테스트를 구현한다.
7. 로그에 원문·비밀 값이 남지 않는지 검사한다.
8. 현재 관련성 분석은 통합 #11에서 진행하고, Agent·노트·ColPali 등 범위 밖 작업은 별도 Issue로 관리한다.

## 이후 작업 후보

- Issue #2 FastAPI 기반 AI 분석 서버 기본 구조 구현
- Server 관련성 분석 요청·결과 저장 연동
- Chrome 내부 OCR·Desktop Apple Vision OCR 왕복 PoC와 실패 경로 검증
- ColPali 시각 콘텐츠 기준선 비교 PoC
- 페이지 이동·체류 시간 기반 학습 흐름·목표 이탈 분석
- MediaPipe 얼굴·시선·자세 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동
- 평가 자동화와 모델·Prompt Version 관리
- `AI/evaluation/SIMILARITY_EVALUATION_GUIDE.md`에 따라 실제 임베딩·재정렬 모델과 고정 정답 세트로 1~7번 방식 평가; 임계값·속도·무관 판정 근거 검증
- 로컬 Streamlit 실험 화면의 후보 모델을 RTX A4000에서 실행하고 모델 리비전·GPU 환경을 기록한 뒤, 사람 검수 정답 세트로 방식별 성능과 보류율을 측정

## 완료 조건

- [ ] Issue #10의 JSON 필드명과 enum이 Adapter에만 반영된다.
- [ ] 외부 Schema와 내부 모델의 필수값·범위가 일치한다.
- [ ] 잘못된 입력이 원문을 노출하지 않는 안전한 오류로 반환된다.
- [ ] 기존 Issue #11 단위 테스트와 신규 계약 테스트가 통과한다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DEVELOPMENT_RULES.md`와 적용 Rule ID를 확인했다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] Issue #10의 확정 계약을 확인했다.
- [ ] `content_acquisition_spec.md`의 Issue #11 내부 계약을 확인했다.
- [ ] Desktop·Extension·Server 담당자와 계약을 공유했다.

## 2026-10-07 학교 캡스톤 다음 작업 — 통합 #11

1. [학교 지침 PLAN](features/learning-goal-content-relevance-evaluation-PLAN.md)을 읽고 기존 전처리 구현을 확인·재사용하고 같은 통합 #11의 관련성 모듈로 연결한다. 미커밋 변경을 임의로 게시하지 않는다.
2. 검토된 Goal Graph·키워드/벡터 결합·역할별 근거·3라벨 로직과 Fake Client 테스트를 구현한다.
3. GPU/WSL2/vLLM 호환성·모델 리비전을 확인하고 실제 Qwen3 4B·FP16 임베딩 API를 연결한다.
4. 합성 평가 CLI를 구현해 모델 없는 테스트와 real 측정을 분리하고 개발 세트 조정 후 독립 평가한다.
5. 백엔드 필드 9개·실제 출력 Schema·최근 흐름/추가 LLM의 미합의 항목을 확인하고 결과와 제한을 기록한다.

문서에 제시한 테스트·CLI 명령은 해당 구현 파일을 만든 뒤 검증한다. 이 기록을 기능 구현 완료로 보지 않는다.

앞으로 이 기능의 작업은 #11과 `feat/11-ai-preprocessing-relevance`에서 진행한다. 기존 #14 Issue/Branch를 새 개발 대상으로 사용하지 않는다.

## 2026-10-07 코드 게시 이후 확인

- 기존 전처리/실험 테스트 30개를 유지하면서 최종 qualityScore 계산과 text·ocr 입력 Adapter를 최신 통합 PLAN에 맞춘다.
- Server가 검증한 현재 실행 상태·미합의 필드 9개의 경계를 확인한다. 일부 내부 stale 검사가 전체 API 연동 완료를 뜻하지 않는다.
- 기존 후보 비교 화면은 실험 이력으로 보존하고 새 운영 경로는 Qwen3 4B·FP16·Hybrid 6번을 구현·평가한다. 실행 도구는 이번에 임의 변경하지 않는다.
- 실제 실행 결과와 제한은 [부분 구현 게시 결과](features/learning-goal-content-relevance-evaluation-REPORT.md)에 기록한다.
