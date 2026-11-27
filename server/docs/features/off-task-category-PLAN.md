# Backend 기능 기획서 — 분석 기록 딴짓 종류(offTaskCategory)

## 기본 정보

- 기능명: UNRELATED 판정의 딴짓 종류 분류
- 기능 ID: off-task-category
- 작성자: 박진욱
- 작성일: 2026-11-28
- 우선순위: MVP - 기본
- 관련 Issue: #381
- 예정 Branch: `feat/381-off-task-category`
- 관련 문서: Notion API 명세 `GET /sessions/{sessionId}/analysis-runs`(재설계 1차), 리포트 설계서 1.1 "딴짓 종류 비율 B"

## 기능 목적

- 해결할 문제: Desktop 세션 리포트의 "영상 45% · SNS 30% …"를 계산할 값이 분석 기록에 없다.
- Backend가 담당할 역할: UNRELATED 판정에 서버 규칙으로 카테고리 하나를 붙여 저장·반환한다.

## API·데이터

- 응답 `offTaskCategory`: `VIDEO | SNS | COMMUNITY | SHOPPING | GAME | MESSENGER | NEWS | OTHER | null`(POST 응답·GET 이력).
- V37 `analysis_runs.off_task_category VARCHAR(16)`, CHECK(고정 목록 + `relation = 'UNRELATED'`, `NOT VALID`).

## 처리 흐름

1. 분석 완료 transaction에서 최종 판정을 저장한 뒤 relation이 UNRELATED면 `OffTaskClassifier.classify(request)`.
2. host가 있으면 `www.`를 떼고 가장 구체적인 상위 도메인부터 목록과 맞춘다(`news.naver.com`→NEWS, `naver.com`→OTHER). host가 없으면(OCR) 앱 이름 소문자 정확히 일치.
3. 그 밖의 relation·보호 상태·AI 실패는 null. 판정이 바뀌면(`complete`) 카테고리를 먼저 지운다.

## 범위 밖

- 학습 메신저(`learningMessengers`)를 딴짓에서 빼는 규칙(분석 판정 쪽 별도 작업).
- 목록 운영 도구(코드 상수로 관리).

## 완료 조건

- [x] 규칙 분류·미분류 OTHER, UNRELATED 외 null
- [x] 응답·이력·DB, 계정 삭제·90일 정리는 기존 analysis_runs 경로
