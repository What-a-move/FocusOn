# AI 학습 노트 생성 명세

## 문서 상태

- 상태: 팀 검토용 계약 초안, 미구현
- 버전: `0.1.0`
- 기준일: 2026-09-22
- 관련 Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)
- 관련 규칙: `AI-NOTE-001`, `AI-NOTE-002`, `AI-TIME-001`, `AI-PRIV-002`, `AI-STALE-001`

이 문서는 한 공부 회차가 끝난 뒤 생성하는 학습 노트의 입력, 출력, 근거, 비동기 작업, 재생성과 삭제 계약을 정의한다. 노트 생성기와 Server 작업 Queue는 아직 구현되지 않았다.

## 1. 노트의 목적

학습 노트는 사용자가 다음 회차를 이어 갈 수 있도록 이번 회차에서 관찰된 자료와 흐름을 짧게 정리한다. 노트는 성취 평가서가 아니며 다음을 주장하지 않는다.

- 사용자가 내용을 이해·암기·습득했다.
- 열람한 해결 자료로 실제 오류를 해결했다.
- 브라우저 밖에서 코드를 작성·실행·검증했다.
- 관찰하지 못한 시간에도 계속 공부했다.
- 모델이 제안한 다음 단계가 사용자의 확정 계획이다.

## 2. 생성 시점과 비동기 경계

```text
회차 종료 요청
  → Server가 이벤트 수신 종료
  → 중복·역순 이벤트 정리
  → 시간 집계 확정 및 aggregationVersion 발급
  → 회차 종료 응답 반환
  → 노트 작업 생성
  → AI 노트 후보 생성
  → Schema·근거·버전 검증
  → Server 저장
  → Client 조회·재시도·삭제
```

회차 종료와 시간 저장은 노트 성공에 의존하지 않는다. 노트 생성이 느리거나 실패해도 사용자는 확정된 시간 기록을 볼 수 있어야 한다.

## 3. 작업 상태

| 상태 | 의미 | 허용 전이 |
| --- | --- | --- |
| `PENDING` | 집계가 확정되고 작업이 대기 중 | `GENERATING`, `CANCELLED` |
| `GENERATING` | 유효한 입력 Snapshot으로 생성 중 | `COMPLETED`, `FAILED`, `STALE`, `CANCELLED` |
| `COMPLETED` | Schema·근거·버전 검증 후 저장됨 | `REGENERATION_REQUIRED`, `DELETED` |
| `FAILED` | 제한된 재시도 뒤 생성 실패 | `PENDING`, `CANCELLED` |
| `STALE` | 집계·목표·근거 버전이 바뀜 | `PENDING`, `CANCELLED` |
| `REGENERATION_REQUIRED` | 피드백·삭제로 내용이 최신이 아님 | `PENDING`, `DELETED` |
| `CANCELLED` | 종료·삭제·사용자 취소로 중단 | 종료 |
| `DELETED` | 사용자 요청으로 삭제됨 | 자동 복구 금지 |

상태명과 저장 방식은 Server 계약에서 확정한다. 실패를 빈 `COMPLETED` 노트로 저장하지 않는다.

## 4. 멱등성과 버전

작업의 논리 키는 다음 조합을 제안한다.

```text
runId
+ aggregationVersion
+ goalVersion
+ evidenceSnapshotVersion
+ noteSchemaVersion
+ promptVersion
```

- 같은 키의 중복 요청은 기존 작업을 반환한다.
- 집계나 근거가 바뀌면 새 `noteVersion`을 만든다.
- 이전 노트를 조용히 덮어쓰지 않고 현재 버전과 구버전을 구분한다.
- 삭제된 회차·노트의 재시도는 새 노트를 만들지 않는다.
- Worker는 저장 직전에 최신 버전과 삭제 상태를 다시 확인한다.

## 5. 입력 Snapshot

### 5.1 허용 입력

- 확인된 GoalProfile과 `goalVersion`
- `runId`, `aggregationVersion`, 시작·종료 시각
- Server가 계산한 직접·보조·무관·확인 필요·미관찰·중지 시간
- 기록 저장이 허용된 활동 요약
- `recordOnly`와 기록 저장 동의 상태
- 안전한 `sourceRef`와 노트용 `evidenceId`
- 사용자의 명시적 판정 수정·메모·미해결 표시
- 모델·Prompt·정책·노트 Schema 버전

`recordOnly=true`이거나 기록 저장 동의가 없으면 콘텐츠 기반 근거와 노트를 생성하지 않는다. 허용된 최소 시간·집계만 제공하며, 노트 생성 실패는 회차 종료와 시간 저장을 막지 않는다.

### 5.2 금지 입력

- 원본 화면·카메라 영상
- 전체 DOM·OCR 원문
- 전체 방문 기록
- Authorization Header, Cookie, API Key와 사용자 이메일
- 다른 사용자·목표·회차의 기록
- 모델의 이전 숨은 추론과 원본 응답
- 사용자가 삭제했거나 기록 동의를 철회한 근거

## 6. 입력 예시

```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440004",
  "goal": {
    "goalId": "550e8400-e29b-41d4-a716-446655440000",
    "goalVersion": 2,
    "mainTopic": "Spring Security JWT 인증 구현",
    "purpose": "IMPLEMENTATION"
  },
  "aggregation": {
    "version": 3,
    "relatedSeconds": 1200,
    "supportingSeconds": 540,
    "offTaskSeconds": 90,
    "uncertainSeconds": 60,
    "unavailableSeconds": 30,
    "pausedSeconds": 300
  },
  "activities": [
    {
      "sourceRef": "source-001",
      "label": "RELATED",
      "summary": "JWT 인증 필터 공식 문서 확인",
      "evidenceIds": ["evidence-001"]
    },
    {
      "sourceRef": "source-002",
      "label": "SUPPORTING",
      "summary": "인증 필터 순서 오류 해결 자료 확인",
      "evidenceIds": ["evidence-002"]
    }
  ],
  "userSignals": {
    "notes": ["필터 순서를 다시 검증해야 함"],
    "unresolvedItems": ["통합 테스트 미완료"]
  },
  "versions": {
    "noteSchema": "session-note-v1",
    "prompt": "session-note-prompt-v1"
  }
}
```

모든 시간은 Server가 계산한다. AI는 초를 추정하거나 합계를 수정하지 않는다.

## 7. 출력 구조

```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440004",
  "noteVersion": 1,
  "status": "COMPLETED",
  "goalSummary": "Spring Security JWT 인증 구현",
  "timeSummary": {
    "learningSeconds": 1740,
    "relatedSeconds": 1200,
    "supportingSeconds": 540,
    "offTaskSeconds": 90,
    "uncertainSeconds": 60,
    "unavailableSeconds": 30,
    "pausedSeconds": 300
  },
  "topicsObserved": [
    {
      "text": "JWT 인증 필터 구성 자료를 확인했습니다.",
      "evidenceIds": ["evidence-001"]
    }
  ],
  "learningFlow": [
    {
      "text": "공식 문서 확인 후 인증 필터 순서 오류 자료를 살펴봤습니다.",
      "evidenceIds": ["evidence-001", "evidence-002"]
    }
  ],
  "resolvedItems": [],
  "unresolvedItems": [
    {
      "text": "통합 테스트 완료 여부는 확인되지 않았습니다.",
      "source": "USER_MARKED"
    }
  ],
  "suggestedNextSteps": [
    {
      "text": "인증 필터 통합 테스트를 확인해 볼 수 있습니다.",
      "kind": "AI_SUGGESTION"
    }
  ],
  "sourceRefs": ["source-001", "source-002"],
  "versions": {
    "aggregation": 3,
    "goal": 2,
    "noteSchema": "session-note-v1",
    "prompt": "session-note-prompt-v1"
  }
}
```

필드명과 구조는 제안이다. Markdown 문자열 하나보다 구조화된 항목을 저장해 근거·삭제·렌더링을 검증할 수 있게 한다.

## 8. 주장과 근거 규칙

| 허용 표현 | 금지 표현 | 필요한 근거 |
| --- | --- | --- |
| “공식 문서를 확인했습니다.” | “공식 문서를 이해했습니다.” | 활동 요약·출처 |
| “오류 해결 자료를 살펴봤습니다.” | “오류를 해결했습니다.” | 활동 요약; 해결은 사용자 확인 필요 |
| “JWT 필터 구성을 다룬 자료입니다.” | “JWT 필터를 구현했습니다.” | 자료 내용 근거 |
| “다음에 테스트를 확인해 볼 수 있습니다.” | “다음에는 반드시 테스트합니다.” | AI 제안으로 명시 |
| “관찰되지 않은 구간이 있습니다.” | “그 시간에도 공부했습니다.” | 집계 공백 |

### 8.1 근거 검증

- 출력의 모든 `evidenceId`는 입력 Snapshot에 존재해야 한다.
- 하나의 근거가 주장을 충분히 뒷받침하지 않으면 여러 근거를 요구하거나 항목을 생략한다.
- 삭제되거나 동의가 철회된 근거는 표시와 재생성에서 제외한다.
- 근거가 적으면 자료 목록과 시간 중심의 짧은 노트를 생성한다.
- `resolvedItems`는 사용자 확인 또는 관찰 가능한 성공 근거가 있을 때만 채운다.

## 9. 시간 표시

- `learningSeconds = relatedSeconds + supportingSeconds` 계산은 Server가 제공하거나 Server가 출력 후 검증한다.
- `uncertainSeconds`와 `unavailableSeconds`를 학습 또는 무관 시간에 숨겨 합산하지 않는다.
- 반올림한 표시값과 원본 초 단위 집계를 구분한다.
- 집계 버전이 바뀌면 노트의 시간 요약도 최신이 아님을 표시한다.
- 노트 모델은 활동 시간의 원인을 상상해 설명하지 않는다.

## 10. 생성 Prompt와 출력 검증

- System 지시, 구조화 Goal, 집계, 활동 근거와 사용자 메모를 별도 필드로 분리한다.
- 활동 요약·메모·제목은 비신뢰 데이터로 취급한다.
- 모델이 Markdown·HTML·Script·링크를 임의 생성하지 않게 한다.
- 출력은 엄격한 Schema, 길이, enum, 근거 ID와 합계 조건을 검증한다.
- 사용자 표시 문구는 HTML을 실행하지 않도록 안전하게 렌더링한다.
- 모델 원본 출력은 로그·캐시·Checkpoint에 저장하지 않는다.
- 형식 교정 재시도는 전체 예산 안에서 제한한다.

## 11. 재생성

다음 이벤트는 재생성 필요 여부를 판단한다.

- 사용자 피드백으로 활동 라벨·구간이 바뀜
- 집계 버전이 증가함
- 목표 버전이 바뀌고 회차 해석에 영향을 줌
- 사용자가 메모·미해결 항목을 수정함
- 근거·출처가 삭제됨
- 노트 Schema나 Prompt 의미가 바뀜

자동 재생성 여부와 사용자 선택 방식은 제품 합의가 필요하다. 기존 노트가 최신이 아니면 구버전 표시 없이 계속 보여주지 않는다.

## 12. 실패와 재시도

| 실패 | 시간 기록 영향 | 노트 처리 | 재시도 |
| --- | --- | --- | --- |
| 모델 Timeout | 없음 | `FAILED` | 지수 지연·상한 검토 |
| 출력 Schema 오류 | 없음 | 결과 미사용 | 교정 1회 후보 |
| 근거 ID 불일치 | 없음 | 결과 미사용 | 입력 수정 뒤만 |
| 집계 버전 변경 | 없음 | `STALE` | 최신 Snapshot으로 새 작업 |
| 기록 동의 철회 | 없음 | `CANCELLED` 또는 삭제 | 금지 |
| 회차 삭제 | 없음 | `DELETED` | 금지 |
| Queue 중복 전달 | 없음 | 기존 작업 반환 | 새 작업 생성 금지 |

실패 메시지는 안전한 오류 코드와 재시도 가능 여부를 제공하고 입력 원문·모델 출력을 포함하지 않는다.

## 13. 저장과 삭제

- Server가 사용자 소유 노트와 버전을 저장한다.
- AI는 영구 저장과 삭제 정책을 직접 결정하지 않는다.
- 기록 동의가 꺼져 있으면 유지 가능한 집계만 반환하고 콘텐츠 기반 노트를 보장하지 않는다.
- 목표·회차·근거 삭제는 현재 노트와 구버전·캐시·Queue에 전파한다.
- 삭제 요청 중인 노트를 Worker가 다시 저장하지 않게 한다.
- 안전한 링크는 저장 허용 범위와 URL 정제 정책을 통과해야 한다.

## 14. 관측 지표

- 작업 상태별 건수와 대기 시간
- 생성·검증·저장 P50·P95
- 중복 작업 방지 수와 stale 폐기 수
- Schema·근거 검증 실패율
- 근거 없는 주장 검수 건수
- 재생성 원인과 성공·실패 수
- 삭제 전파 지연과 잔존 참조 수
- 노트당 모델 호출·토큰·비용

노트 길이 또는 생성 성공률만으로 품질을 판단하지 않는다.

## 15. 검증 시나리오

- 노트 실패와 지연이 회차 종료·시간 저장을 막지 않는다.
- 같은 종료 요청이 중복 노트를 만들지 않는다.
- 자료 열람만으로 이해·구현·해결 완료를 주장하지 않는다.
- 출력의 모든 근거 ID가 입력에 존재한다.
- 확인 필요·미관찰 시간이 학습 시간에 합산되지 않는다.
- 피드백 뒤 집계가 바뀌면 기존 노트가 최신으로 표시되지 않는다.
- 삭제된 근거와 회차를 재시도가 복구하지 않는다.
- 기록 동의를 끄면 콘텐츠 기반 노트 입력을 만들지 않는다.
- 악성 사용자 메모가 Prompt·도구·출력 형식을 바꾸지 못한다.
- Markdown·링크 표시에서 Script가 실행되지 않는다.

## 16. 팀 합의 필요

- 노트 생성의 최초 출시 포함 여부와 사용자 고지
- 저장할 구조화 필드와 사용자 편집 가능 범위
- 기록 동의가 없을 때 제공할 최소 요약
- 비동기 Queue, 재시도, Deadline과 최대 비용
- 재생성 자동 실행 여부와 구버전 보존 기간
- 안전한 `sourceRef`와 링크 보존 정책
- 노트의 평가 기준과 사람 검수 절차

## 관련 문서

- [목표·세션·회차 모델 명세](goal_session_spec.md)
- [사용자 피드백·개인화 명세](feedback_personalization_spec.md)
- [AI API 계약](api_spec.md)
- [AI Prompt와 Agent 신뢰 경계](prompt_guide.md)
- [AI 평가와 회귀 검증 명세](evaluation_spec.md)
- [데이터 수명과 개인정보 경계](data_lifecycle.md)
