# 화면 분석의 학습 정리 근거 수신

## 기본 정보

- 기능 ID `analysis-review-evidence`, MVP 기본
- 작성일 2026-11-27
- 관련 이슈 [#364](https://github.com/What-a-move/FocusOn/issues/364), 상위 [#29](https://github.com/What-a-move/FocusOn/issues/29)
- 작업 브랜치 `feat/364-server-analysis-review-evidence`

## 내부 응답과 처리

AI [PR #362](https://github.com/What-a-move/FocusOn/pull/362)의 `POST /internal/v1/analysis-runs` 응답에 `reviewEvidence`가 들어온다. `AnalysisAiClient`가 DOM·OCR 본문이 있는 관측에서 `CONCEPT`와 최대 120자의 유효한 개념 라벨로 이루어진 배열 1건을 읽는다. 구버전의 누락, 빈 배열, 잘못된 값은 근거만 버린다. 기존 HTTP 상태, 오류 코드와 응답 스키마를 사용한다.

`AnalysisDecision`은 확정 `RELATED + MATCHED`이고 목표 ID가 유효한 형식일 때만 근거를 담는다. 서버 완료 트랜잭션에서 보호 상태, 세션 실행 구간, 목표 버전과 목표 사본을 재검증한다. 기존 판정이나 매칭이 무효화되면 근거도 사라진다. 라벨은 한 줄로 제한하고 URL·알려진 민감 형식을 거절한다. 개념의 의미 확인은 AI가 담당한다.

공개 분석 응답·이력은 기존 DTO를 사용하며 근거는 분석 중 메모리에서만 다룬다. 학습 정리 근거 DB, Worker, 개입, 시간 계산을 연결하는 변경은 후속이다.

## 문서와 실제 동작 대조

- 시작 시 `origin/dev`의 `8a1e31e`에는 AI PR #362의 병합 커밋 `d52fb83`이 포함돼 있었다. `AI/docs/api_spec.md`, `AI/src/api/reception.py`, `AI/src/api/routes.py` 및 `review_concept`의 실행 조건을 대조했다. GitHub 배포 기록은 비어 있었고 설정된 AI 주소에서 HTTP 응답을 받지 못해 실행 버전은 미확인이다.
- Backend [학습 정리 파이프라인 기획](learning-summary-pipeline-PLAN.md)의 근거 응답이 없다는 문구는 작성 당시 상태다. AI 응답은 추가됐으며 Worker 입력을 채우는 제품 저장 경로는 별도다.
- Notion [집중 기록 · 학습 정리 · 리포트](https://app.notion.com/p/3de0b6e5e64c808683b0e9a4a79fccca) 5.1은 원문과 민감 화면을 제외하고 최소 개념을 남긴다고 명시한다. 5.2는 진행 중인 결과를 종료 후 30초까지 기다리고 늦은 결과의 집중 시간 반영 가능성을 언급한다. 현재 서버는 종료 뒤 완료한 결과를 `UNCERTAIN`으로 처리한다. 인계 범위의 30초 예외는 종료 전에 접수한 분석의 노트용 근거에 한정되며 이번 수신에는 적용하지 않는다.
- Notion 데스크톱에서 리포트 설계의 5.1~5.3과 개인정보 정책의 문서 상태 `REVIEW`를 확인했다. 리포트의 후반 절·8.2와 현재 문서 상태, 개인정보 정책 세부 조항, AI 분석 계약의 본문·상태는 화면 탐색 문제로 확인하지 못했다. 팀의 이후 정책 확정 여부는 미확인이다.
- 저장 연동 전 선택 동의, 실제 표시 문구 버전과 구버전 앱 처리, 철회 전 근거·완성 노트의 처리 범위를 팀과 확정한다. 저장과 생성 직전에 유효 동의를 다시 확인하는 경로가 필요하다. 저장소의 보관 기준은 근거 90일, 복습 노트 1년이다.

## 검증 계획

- 합성 AI 응답으로 정상 DOM·OCR 근거, 구버전, 빈 배열, 잘못된 형식·타입·길이·민감 라벨, 메타데이터 및 빈 본문을 확인한다.
- 낮은 신뢰도, 일치 상태 변경, 목표 ID·버전 불일치, 종료 및 보호 전환 시 근거가 사라지는지 확인한다.
- 공개 응답·이력·안전 로그의 비노출을 확인하고 독립 리뷰 뒤 백엔드 빌드·Checkstyle·격리 테스트를 실행한다.
