# AI 기능 기획서: E5 ∥ JEV 병렬 할 일 매칭 및 `topic-bank` 제거

## 기본 정보

- 기능명: E5 ∥ JEV 병렬 할 일 매칭 재설계 (`topic-bank.json` 제거)
- 기능 ID: AI-GOAL-MATCH-JEV-E5
- 작성자: AI 엔지니어
- 작성일: 2026-11-27
- 우선순위: MVP - 핵심
- 관련 Issue: -
- 예정 Branch: `feat/ai-goal-match-jev-e5` (기준: `origin/dev` `8a1e31e`)
- 관련 Desktop·Extension·Server 문서: `docs/API_CONTRACT.md`, `AI/docs/api_spec.md`

## 기능 목적

- 해결할 문제:
  1. 기존 `dev`의 `goal_match.py`는 2,700개 고정 합성 공부 목록(`topic-bank.json`)을 대조군으로 사용해 오매칭을 막았으나, 데이터셋 자체가 불안정하고 범위가 제한적이다.
  2. `topic-bank.json`을 제거하면 E5 임베딩만으로는 같은 도메인의 다른 세부 주제를 구별하지 못해 오매칭이 급증한다(44건 기준 4건 → 21건).
  3. 기존 `dev`에서는 도메인 유사도 때문에 다른 공부 화면(`FAR`)을 거의 감지하지 못했다(44건 기준 0/14).
- AI가 담당할 역할:
  - `topic-bank.json`을 완전히 제거한다.
  - 로컬 E5(`multilingual-e5-small`)와 JEV(`AsyncTypeSafeClient.system_one`)를 **완전 병렬 실행**해, 할 일 매칭 점수와 FAR(다른 주제 학습) 판정을 결합한다.
  - 가중합 + JEV 비토(Gate) 결합으로 오매칭을 차단하고, 타임아웃(3초)/일시 오류 시 1회 재실행 후 실패하면 `NOT_ENOUGH`로 안전하게 처리한다.
- AI가 담당하지 않는 범위:
  - 공개 응답 계약(`ScreenAnalysisResponse`, `matchedGoalId`, `matchStatus`) 스키마 변경
  - Server 세션/개입 정책 변경
  - `review_concept`의 별도 LLM 생성(단, `topic_bank` 제거 시 `review_concept` 호환 경로는 유지 또는 안전하게 None 반환)

## 입력 데이터

- 입력 출처: Extension(`CHROME_DOM`, `TAB_META`) / macOS Desktop(`DESKTOP_APP_OCR`, `APP_META`) → Server 검증 후 `AnalysisReception`
- 필수 필드: `contentSource`, `extractionStatus`, `goals[]` (`goalId`, `text`, `profile`)
- 선택 필드: `metadata` (`pageTitle`, `windowTitle`, `appName`, `urlHost`), `contentPayload.text` (최대 2,000자), `ocr` (`confidence`, `captureStatus`)
- 전처리 방법: `clean_text`로 UI 파편 제거, `MIN_BODY_CHARS(80)` 미만 본문 및 `APP_META`는 `NOT_ENOUGH` 조기 반환
- 제외할 민감 데이터: `contains_sensitive_data`로 비밀번호·결제 정보·개인 메시지 차단, 로그에 원문·API 키·Authorization 헤더 남기지 않음

## 분석 기준

- 비교 대상 목표: 세션의 확정 목표 목록 `goals` (`goal.text`, `main_topic`, `core_topics`)
- E5 역할:
  - 화면 활동 분류(`STUDY` / `OFF_TASK` / `UNCERTAIN`)
  - 각 할 일과 화면/본문 청크(550자, 겹침 100자) 간 코사인 유사도(`e5_score`)
- JEV 역할 (`AsyncTypeSafeClient.system_one`, 병렬 실행):
  - 입력(`state`): `title`, `body`(최대 2,000자), `goals`(할 일 ID + 텍스트 + 핵심 주제 목록)
  - 질문(`questions`):
    1. 각 할 일별 직접 관련 여부(`Noul` 또는 `Score`): 이 화면이 해당 할 일의 직접적인 학습 내용인지 평가
    2. `is_different_study_topic` (`Noul`): 이 화면이 학습 활동이지만 제시된 모든 할 일과 다른 세부 주제를 다루는지 평가
- 결합 및 판정 규칙 (**가중합 + 비토 결합**):
  - `combined_score = (1 - w_jev) * e5_score + w_jev * jev_score`
  - **비토(Gate)**: JEV 해당 할 일 점수가 `jev_veto_threshold` 미만이면 E5가 높아도 `MATCHED` 차단
  - **MATCHED**: 최고 후보가 `combined_match_floor` 이상이고, `jev_score >= jev_match_min`이며, JEV 비토에 걸리지 않을 때
  - **FAR**: 메타데이터 전용이 아니고 `extractionStatus == SUCCESS`이며 `classifier == STUDY`일 때, 모든 할 일이 `MATCHED`를 통과하지 못하고 `is_different_study_topic >= jev_far_min` (또는 JEV+E5 모두 낮은 기준 충족)일 때
  - **NOT_ENOUGH**: 그 외 애매한 구간, 본문 부족, 메타데이터 전용, 또는 재실행 포함 JEV 실패 시
- 44건 평가셋 목표 기준:
  - 잘못된 연결 `≤ 2건`
  - 올바른 할 일 연결 `≥ 13/14`
  - 올바른 FAR `≥ 10/14`
  - 잘못된 FAR `≤ 1건`

## 모델·외부 연동

- 로컬 임베딩/분류: `intfloat/multilingual-e5-small` + LogisticRegression head (`ActivityClassifier`)
- 외부 판별기: TypeSafe JEV (`AsyncTypeSafeClient.system_one`)
- Timeout · 재시도 · Fallback:
  - 정상 경로: `E5 ∥ JEV` 병렬 실행, JEV 타임아웃 `3.0초`
  - 예외 경로: JEV 타임아웃 또는 일시적 API/응답 오류 시 **1회 재실행**
  - 최종 실패: 재실행까지 실패 시 오매칭 방지를 위해 `NOT_ENOUGH` 반환 (`reviewEvidence` 생략)

## 예외 처리

| 상황 | 반환 상태·오류 | 처리 방법 |
| --- | --- | --- |
| `APP_META` 또는 본문 80자 미만 | `matchedGoalId=None`, `matchStatus="NOT_ENOUGH"` | JEV 호출 생략, 조기 반환 |
| JEV 1차 타임아웃(3초) / 일시 오류 | - | 동일 입력으로 JEV 1회 재실행 |
| JEV 재실행까지 실패 또는 설정 누락 | `matchedGoalId=None`, `matchStatus="NOT_ENOUGH"` | 로그(`request.fail` WARN, 민감정보 제외) 기록 후 보류 |
| JEV 비토 발동 (E5 높음 + JEV 낮음) | `FAR` 또는 `NOT_ENOUGH` | `is_different_study_topic` 및 `STUDY` 여부 확인 후 분기 |
