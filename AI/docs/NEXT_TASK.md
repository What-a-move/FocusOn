# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: MVP 분석 계약 리뷰 및 Issue #12 코드와 후속 관련성 구현의 정합성 확인
- 담당 영역: AI
- 우선순위: 높음
- 상태: Issue #16 문서 계약 반영, 팀 승인·구현 검증 전

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
- 화면 `UNRELATED`와 시간 기반 `driftState`를 분리하고, `RELATED` 초기화·`UNCERTAIN` 비누적 및 연속 구간 종료·15초 미만 제외와 `analysis_rules.md` 9.1의 정책 `v1` 연속/반복 OR 조건을 계약 테스트한다. 임계값 숫자와 실제 개입 판정은 Server가 관리한다.
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
