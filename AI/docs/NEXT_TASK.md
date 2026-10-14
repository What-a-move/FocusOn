# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: MVP 분석 계약 리뷰 및 Issue #12 코드와 후속 관련성 구현의 정합성 확인
- 담당 영역: AI
- 우선순위: 높음
- 상태: Issue #16 문서 계약 반영, 팀 승인·구현 검증 전

## 병행 진행: 학습 정리 Issue #29

- Branch: `feat/29-ai-learning-summary` (최신 `origin/dev`에서 분리; #12의 미커밋 변경과 격리)
- 현재 상태: 선행 AI 코드·테스트·Prompt는 복구 가능한 커밋으로 되돌렸다. Desktop·Streamlit의 분리된 합성 데이터 시연과 고정 합성 근거 전용 OpenAI 구조화 생성·검증 단위를 구현했다. API 키가 없어 실제 OpenAI 호출은 아직 하지 않았고, 이는 제품 생성·저장이 아니다.
- 다음 단계: 키가 있는 안전한 로컬 환경에서 **고정 합성 데이터만** 실제 OpenAI로 호출해 결과·시간·비용을 확인한다. 이후 Server·AI 내부 요청 Schema와 서비스 인증을 담당자와 확정한 뒤 FastAPI 어댑터를 연결한다. Server 세션 `ENDED`/소유권/동의·30초 입력 마감·단일 Worker·20초 Timeout·1분/5분 재시도·보존/삭제를 구현하고 Desktop 조회 UI를 연결한다.
- 선행: #11 최종 관련성 결과, Server 세션/인증, Extension/Desktop 제외·추출 경로. Notion 기획은 `REVIEW`이며 미합의 API/ERD를 확정으로 취급하지 않는다.
- 후속 모델 선택: DOM 추출·실제 화면 데이터 연동 시 외부 제공자·지역·보존·학습 이용·사용자 고지를 논의하고 승인하기 전에는 제품 데이터를 외부 모델에 보내지 않는다. 현재는 고정 합성 데이터만 시험한다.

## 작업 목적

Server와 AI 사이 호출 경로·Schema·상태 투영을 확정하고 Issue #12의 목표 보조 Branch와 문서 계약을 비교한다. 관련성·카메라 분석은 아직 구현되지 않았으므로 문서의 MVP 정책을 코드 완료로 표현하지 않는다.

## 선행 조건

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
- 목표 보조 4개 `decision`, 최대 2회 질문·수동 확정 경로 확인
- `EXCLUDED`와 `PRIVACY_BLOCKED`, `sessionStatus` 계약 확인
- AI 내부 라벨과 제품 표시 라벨 매핑 확인
- 개인화 휴식의 최근 7일 규칙과 카메라 비필수 경로 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 목표 입력 2~40자·대화당 질문 최대 2회·복수 목표 단일 선택을 #12 구현과 비교
- `text`·`ocr` 전달, AI 전처리 `qualityScore`·`contentHash`, Backend 검토 9개 컬럼 합의

## 예상 작업 순서

1. 현재 공개 관련성 경로 `/api/v1/sessions/{sessionId}/analysis-runs`와 내부 AI 경계를 Server·Client 담당자와 확정한다. Notion `POST /api/v1/goals/clarify`의 네 판정값·최대 2회 질문·장애 시 수동 확정도 확인한다. `/api/v1/feedback`은 공개 MVP 경로가 없는 후속 제안으로 유지한다.
2. AI 내부 라벨과 제품 표시 라벨, 기존 `FocusState` 호환 전략을 공유 타입 담당자와 확정한다.
3. `goalId`, `sessionId`, `eventId`, `navigationId`와 `sessionStatus`, `analysisStatus`의 필수 범위를 확인한다.
4. 목표 보조 4개 판정값과 AI 장애 시 직접 목표 확정 경로를 확인한다.
5. FastAPI, Pydantic, pytest와 서비스 인증·설정 주입 방식을 확정한다.
6. Health Check, 공통 성공·실패 envelope, Schema 검증 테스트를 구현한다.
7. 로그에 원문·비밀 값이 남지 않는지 검사한다.
8. 관련성 분석, Agent, 화면 판정 피드백, 노트, ColPali PoC를 별도 Issue로 진행한다. 화면 판정 피드백과 ColPali는 MVP에 포함하지 않는다.

## 이후 작업 후보

- 팀원 보고 120건 Hybrid 평가의 사례·정답·예측·클래스별 지표·결합식·재현 코드를 받아 독립 holdout과 지연·비용을 검증한다. 현재 결과를 배포 성능으로 표현하지 않는다.
- 화면 `UNRELATED`와 시간 기반 `driftState`를 분리하고, `RELATED` 초기화·`UNCERTAIN` 비누적·15초 미만 제외 및 120초/10분 내 30초 2회/30초 임계값을 계약 테스트한다.
- 카메라 60초 후보·90초 최종 게이트, 최신 화면 `RELATED` 억제, `EXCLUDED`/`PRIVACY_BLOCKED` 분기를 통합 테스트한다.

- Server 관련성 분석 요청·결과 저장 연동
- Chrome 내부 OCR·Desktop Apple Vision OCR 왕복 PoC와 실패 경로 검증
- ColPali 시각 콘텐츠 기준선 비교 PoC
- 페이지 이동·체류 시간 기반 학습 흐름·목표 이탈 분석
- MediaPipe 얼굴·시선·자세 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동
- 평가 자동화와 모델·Prompt Version 관리

## 완료 조건

- [ ] 기능 기획서를 작성했다.
- [ ] 입력·출력 계약을 공유했다.
- [ ] Health Check와 공통 응답 Schema가 동작한다.
- [ ] 다중 상태 또는 호환 상태 계약이 문서와 Schema에서 일치한다.
- [ ] 상세 분석기·모델 없이도 테스트가 실행된다.
- [ ] 개인정보·민감 데이터 제외 기준을 확인했다.
- [ ] 실행 명령과 의존성을 문서화했다.
- [ ] 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DEVELOPMENT_RULES.md`와 적용 Rule ID를 확인했다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] Desktop·Extension·Server 담당자와 계약을 공유했다.
