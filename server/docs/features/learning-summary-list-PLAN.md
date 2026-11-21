# Backend 기능 기획서 — 학습 노트 목록

## 기본 정보

- 기능명: 학습 노트 목록 (`GET /api/v1/learning-summaries`)
- 기능 ID: learning-summary-list
- 작성자: 박진욱
- 작성일: 2026-11-19
- 우선순위: MVP - 기본
- 관련 Issue: #289 (상위 #29, 생성 파이프라인 #288)
- 예정 Branch: `feat/289-server-learning-summary-list`
- 관련 문서: Notion `집중 기록 · 학습 정리 · 리포트` 2.4·8.1 (REVIEW)

## 기능 목적

- Desktop 사이드바 `학습 노트`가 여러 회차의 정리를 회차별 반복 조회 없이 한 번에 가져온다.

## API 정의

- `GET /api/v1/learning-summaries?cursor=&limit=`, 인증 필요, Desktop만.
- 성공 200: `{ items: [{ sessionId, goalSnapshot, studyDate, startedAt, endedAt, accumulatedActiveMs, overview, keyConcepts }], nextCursor }`
- 실패: 401 무인증, 403 `SESSION_QUERY_FORBIDDEN`(Extension), 400 `VALIDATION_ERROR`(limit 1~50 밖·숫자 아님, cursor 형식 오류)

## 데이터·처리

- `learning_summaries`와 `learning_sessions`를 세션 ID로 조인해 본인 소유·`READY`·`ended_at` 있는 행만 읽는다. 새 테이블·마이그레이션 없음.
- 정렬 `ended_at DESC, id DESC`, 키셋 커서 `종료시각(epoch 마이크로초)_세션ID`, `limit + 1`로 다음 페이지 여부를 판단한다.
- 각 항목은 단건 조회와 같은 READY 검증을 거친다(불량 READY는 503).
- 원문·URL·카메라 정보는 저장·응답하지 않는다.

## 완료 조건

- [x] 본인 READY만 최근 종료순, 같은 종료 시각은 ID 내림차순.
- [x] 커서로 끝까지 누락·중복 없이 넘긴다.
- [x] 잘못된 limit·cursor 400, Extension 403, 무인증 401.
- [x] 테스트·문서 갱신.
