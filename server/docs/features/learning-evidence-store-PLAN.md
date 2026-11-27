# Backend 기능 기획서 — 화면 분석 학습 정리 근거 저장 연결

## 기본 정보

- 기능명: 화면 분석 완료 시 학습 정리 근거 저장
- 기능 ID: learning-evidence-store
- 작성자: 박진욱
- 작성일: 2026-11-28
- 우선순위: MVP - 핵심
- 관련 Issue: #379 (상위 #29, 선행 #364)
- 예정 Branch: `feat/379-learning-evidence-store`
- 관련 문서: Notion 「집중 기록 · 학습 정리 · 리포트」 설계서, 개인정보 정책, `docs/DATA_PRIVACY.md`

## 기능 목적

- 해결할 문제: `LearningSummaryWorker`는 `learning_review_evidences`를 읽지만 쓰는 곳이 없어 학습 정리가 항상 `INSUFFICIENT_DATA`로 끝난다.
- Backend가 담당할 역할: #364가 검증해 내부 판정에 담은 개념 근거를 완료 재검증 뒤 저장하고 90일 뒤 지운다.

## 전제

- 동의: 별도 항목 없이 `SCREEN_ANALYSIS` 동의 문구에 복습 노트 목적·보관 기간을 넣었다(Desktop b676ac8). 문구 버전 확인은 Desktop이 한다.
- 완료 transaction의 기존 재검증(약관·개인정보·화면 분석 동의, 제외 목록, 민감 정보, 세션 RUNNING, 실행 구간, 목표 버전, 매칭 목표 스냅샷)을 통과한 판정에만 근거가 남는다.

## 처리 흐름

1. `AnalysisRunStore.complete`가 최종 판정을 확정한 뒤 `reviewEvidence`가 남아 있으면 `LearningEvidenceRecorder.record`를 호출한다.
2. 라벨은 앞뒤 공백 제거·연속 공백 1칸으로 정리하고, `SHA-256(유형 + 소문자 라벨)`로 세션 안 중복을 막는다(사용자 잠금 아래 존재 확인 + 기존 UNIQUE).
3. 관측 시각·출처는 분석 실행 행 값을 쓴다.
4. `AnalysisRunRetention`이 종료·중단 세션의 근거를 종료 후 90일에 지운다.

## API·스키마

- 공개 API 변경 없음. 마이그레이션 없음(V23 테이블 사용).

## 범위 밖

- 종료 뒤 완료한 분석의 노트 전용 30초 예외 경로(#364 후속, 현재는 버림).
- 동의 철회 시 이미 저장한 근거의 즉시 삭제(현재는 새 저장만 막음).

## 완료 조건

- [x] 유효 근거 저장, 같은 개념 중복 미저장
- [x] 세션 정지·매칭 실패·보호 상태·동의 철회·AI 실패면 미저장
- [x] 90일 정리
- [x] 공개 응답·이력·로그 비노출 유지
