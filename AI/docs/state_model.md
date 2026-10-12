# AI 상태 모델

## 문서 상태

- 상태: 팀 검토용 계약 초안
- 버전: `0.2.0-draft`
- 기준일: 2026-09-22
- 관련 Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)
- 관련 규칙: `AI-STATE-001`, `AI-FAIL-001`, `AI-DRIFT-001`, `AI-ACTION-001`
- 관련 문서: [목표·세션 모델](goal_session_spec.md), [피드백 명세](feedback_personalization_spec.md), [AI API 계약](api_spec.md)

이 문서는 콘텐츠 확보 상태, AI 분석 실행 결과, 목표 관련성, 시간 기반 이탈 흐름과 사용자에게 제안할 행동을 분리한다. 공개 API의 관련성 값은 Notion 기준 5개 상태(`RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`)를 사용한다. `SUPPORTING`, `OFF_TASK`, `UNAVAILABLE`은 내부 분석·정책용 세부 상태이며 API 경계에서 공개 상태로 변환한다.

## 1. 상태 계층

| 계층 | 필드 | 답하는 질문 | 담당 |
| --- | --- | --- | --- |
| 추출 | `extractionStatus` | 판단 가능한 콘텐츠를 확보했는가? | Extension·로컬 OCR·입력 검증 |
| 분석 | `analysisStatus` | AI 분석이 정상적으로 실행됐는가? | AI |
| 관련성 | `relevanceLabel` | 현재 콘텐츠가 목표와 어떤 관계인가? | AI |
| 흐름 | `driftState` | 시간과 최근 이동을 고려할 때 학습 흐름이 어떤가? | AI 정책 + Server 문맥 |
| 행동 | `recommendedAction` | 사용자에게 어떤 제안을 보여줄 수 있는가? | 정책 코드·Client |

한 계층의 값을 다른 계층의 값으로 직접 추론하지 않는다. 예를 들어 추출 실패는 `OFF_TASK`가 아니고, `OFF_TASK`는 곧바로 `DRIFT_RISK`가 아니다.

## 2. 추출 상태

| 값 | 의미 | 허용되는 다음 처리 |
| --- | --- | --- |
| `SUCCESS` | 콘텐츠 유형에 필요한 핵심 정보와 근거를 확보했다. | 관련성 분석 가능 |
| `PARTIAL` | 제목 등 일부 근거만 확보했다. | 확보한 범위만 분석하거나 확인 요청 |
| `FAILED` | 빈 결과, 접근 실패, 시간 초과 등으로 추출하지 못했다. | 분석 보류, 실패 표시 |
| `EXCLUDED` | 정책상 수집·분석이 금지된 대상이다. | AI 호출 없음 |
| `UNSUPPORTED` | OS, 콘텐츠 유형 또는 네이티브 경로를 지원하지 않는다. | 미지원 표시, 외부 전송 우회 금지 |

`FAILED`, `EXCLUDED`, `UNSUPPORTED`에서는 목표 무관이나 이탈 판정을 생성하지 않는다.

## 3. 분석 상태

| 값 | 의미 |
| --- | --- |
| `COMPLETED` | 허용된 입력으로 Schema 검증까지 완료했다. |
| `NEEDS_CONFIRMATION` | 근거가 충돌하거나 사용자 확인이 필요하다. |
| `SKIPPED` | 제외, 중지, 캐시 정책 또는 불필요한 호출 방지로 분석하지 않았다. |
| `ERROR` | 모델, 출력 검증, 내부 처리 또는 의존 서비스 오류가 발생했다. |

모델 오류를 `NEEDS_CONFIRMATION`으로 숨기지 않는다. 사용자 확인이 필요한 의미적 모호함과 시스템 오류를 구분한다.

## 4. 관련성 상태

| 값 | 의미 | 예시 |
| --- | --- | --- |
| `RELATED` | 목표를 직접 학습하거나 수행하는 콘텐츠다. | 목표 주제 강의, 공식 문서, 직접 작성 중인 코드 |
| `SUPPORTING` | 목표 달성에 필요한 보조 활동이다. | 오류 검색, 선수 지식, 도구 사용법, 관련 Q&A |
| `UNCERTAIN` | 근거가 부족하거나 충돌해 관계를 확정하기 어렵다. | 제목만 있는 페이지, 혼합 주제 콘텐츠 |
| `OFF_TASK` | 충분한 콘텐츠 근거에서 현재 목표와 무관하다. | 목표와 다른 주제의 기사나 영상 |
| `UNAVAILABLE` | 추출·권한·모델 실패로 관련성을 판단할 수 없다. | DOM·OCR 실패, 모델 출력 오류 |

`SUPPORTING`은 `RELATED`와 동일한 정상 학습 흐름이 될 수 있다. `UNAVAILABLE`은 낮은 관련성 점수가 아니다.

## 5. 이탈 흐름 상태

| 값 | 의미 |
| --- | --- |
| `LEARNING` | 직접 또는 보조 학습 흐름이 이어지고 있다. |
| `OBSERVING` | 무관 가능성이 있으나 체류가 짧거나 근거가 부족하다. |
| `POSSIBLE_DRIFT` | 무관 콘텐츠의 체류 또는 반복 신호가 누적되고 있다. |
| `DRIFT_RISK` | 충분한 지속·반복 근거로 복귀 제안을 검토할 수 있다. |
| `RECOVERED` | 관련 자료 복귀로 정상 흐름을 회복했다. |
| `UNKNOWN` | 추출 실패, 미관찰 구간 또는 문맥 부족으로 판단할 수 없다. |

기본 상태 전이는 다음과 같다.

```text
LEARNING
   ↓ 무관 가능성 관찰
OBSERVING
   ↓ 체류·반복 근거 누적
POSSIBLE_DRIFT
   ↓ 정책 기준 충족
DRIFT_RISK

OBSERVING / POSSIBLE_DRIFT / DRIFT_RISK
   └─ 관련 자료 복귀 → RECOVERED → LEARNING

추출·모델 실패 또는 미관찰 → UNKNOWN
제외·휴식·일시정지 → 흐름 판단 중지
```

MVP의 화면 이탈 정책은 `analysis_rules.md`의 15초 미만 전환 제외, `GENTLE` 120초 연속, `NORMAL` 최근 10분 내 30초 이상 2회, `ACTIVE` 30초 연속을 따른다. `RELATED`는 현재 이탈 구간을 초기화하고 `UNCERTAIN`은 누적하지 않는다. 구버전 기획서의 5초·60초 화면 이탈값은 적용하지 않는다.

## 6. 권장 행동

| 값 | 의미 | 실행 조건 |
| --- | --- | --- |
| `NO_ACTION` | 사용자 개입이 필요하지 않다. | 정상 흐름, 짧은 방문, 실패·정보 부족 |
| `ASK_USER` | 관련성 또는 휴식 의도를 확인한다. | 충분한 품질의 모호한 콘텐츠 |
| `SUGGEST_RETURN` | 목표 자료로 돌아갈 것을 제안한다. | `DRIFT_RISK`와 알림 정책 충족 |
| `SUGGEST_BREAK` | 휴식을 제안한다. | 사용자 설정과 장시간 활동 근거 |
| `SUGGEST_PAUSE` | 타이머 일시정지를 제안한다. | 자리 비움 등 선택 기능의 지속 근거 |
| `SUGGEST_TEMPORARY_BLOCK` | 후속 기능에서 제한 시간의 임시 차단을 제안한다. | 현재 MVP 상태 계약에는 포함하지 않음 |

권장 행동은 실행 명령이 아니다. 실제 일시정지나 차단은 Client가 대상과 시간을 보여주고 사용자가 선택한 경우에만 수행한다. 사전 설정된 자동 정지는 별도 합의와 검증이 필요하다.

## 7. 세션·분석 제외 상태 투영

세션의 최종 상태는 Server가 소유한다. AI에는 현재 상태의 투영값만 전달한다.

| 필드 | 값 | 의미 |
| --- | --- | --- |
| `sessionStatus` | `RUNNING` | 타이머가 진행 중이며 분석 조건 확인 |
| `sessionStatus` | `PAUSED`·`AUTO_PAUSED` | 타이머·분석 중지 |
| `sessionStatus` | `AWAITING_END_CONFIRMATION`·`ENDING` | 종료 확인·처리 중; 타이머·분석 중지 |
| `sessionStatus` | `ENDED`·`ABANDONED` | 최종 상태; 결과를 현재 상태에 적용하지 않음 |
| `analysisStatus` | `EXCLUDED` | 화면 콘텐츠 분석 중지, 타이머·동의된 카메라 유지 |
| `analysisStatus` | `PRIVACY_BLOCKED` | 화면·카메라 분석 중지, 타이머 유지 |

`EXCLUDED`와 `PRIVACY_BLOCKED`는 세션 상태를 바꾸지 않는다. 필수 권한 유실로 `AUTO_PAUSED`가 되면 사용자가 복구 후 명시적으로 재개한다. 두 비분석 상태 모두 콘텐츠 payload와 원문을 AI에 보내지 않는다.

## 8. 조합 불변 조건

| 조건 | 강제 결과 |
| --- | --- |
| `extractionStatus = EXCLUDED` | AI 미호출, `analysisStatus = SKIPPED` |
| `extractionStatus = FAILED/UNSUPPORTED` | `relevanceLabel = UNAVAILABLE`, `driftState = UNKNOWN`, `recommendedAction = NO_ACTION` |
| `analysisStatus = ERROR` | 현재 판정 적용 금지, `recommendedAction = NO_ACTION` |
| `relevanceLabel = RELATED/SUPPORTING` | 기본 `driftState = LEARNING`; 현재 이탈 구간·대기 중 카메라 후보 초기화 |
| `relevanceLabel = UNCERTAIN` | `ASK_USER` 가능, 이탈 확정 금지 |
| `relevanceLabel = OFF_TASK` 단독 | 최대 `OBSERVING`; 즉시 차단·이탈 알림 금지 |
| 화면 관련성 판정 수정·삭제 | MVP 미지원; 목표 문장 확인·수정과 별도 기능 |
| 목표·탭·회차 버전 불일치 | 결과 폐기, 현재 상태 변경 금지 |
| 민감정보 2차 검사 실패 | `PRIVACY_BLOCKED`, AI 미호출, 원문 미저장 |

## 9. 공개 상태 매핑

`packages/shared-types`의 `FocusState`와 Server 공개 응답은 Notion 기준 5개 상태만 사용한다. 내부 AI 상태를 그대로 Client에 전달하지 않는다.

| 내부 상태 | 공개 `FocusState` 매핑 |
| --- | --- |
| `RELATED` 또는 `SUPPORTING` + `LEARNING/RECOVERED` | `RELATED` |
| `OFF_TASK` + 충분한 관련성 근거·confidence `≥ 0.75` | `UNRELATED` (`driftState`와 무관) |
| `UNCERTAIN`, confidence 미달, 의미 판단 보류 | `UNCERTAIN` |
| 정책 제외 | `EXCLUDED` |
| 개인정보·권한 차단 또는 분석 입력 차단 | `PRIVACY_BLOCKED` |

내부 세부 상태와 공개 상태를 함께 기록할 수 있지만, Client는 공개 상태와 별도 `analysisStatus`를 사용해 의미적 불확실성과 시스템 실패를 구분해야 한다.

`UNRELATED`는 현재 콘텐츠와 목표의 관계다. 15초 미만 전환에서도 충분한 관련성 근거가 있으면 유지하며, 개입 임계값 충족 전에는 `driftState`와 알림만 보류한다. OCR 품질·임베딩 유사도는 최종 `relevanceLabel`·`confidence`를 다시 쓰지 않는다.

## 10. 응답 적용 전 확인

결과를 현재 화면에 적용하기 전에 다음 값이 요청 당시와 같은지 확인한다.

- `sessionId`
- `runId`
- `eventId`
- `goalId`
- `goalVersion`
- `navigationId`
- 세션의 시작·일시정지·종료 상태

하나라도 불일치하면 `STALE_EVENT`로 폐기한다. 오래된 결과를 새 탭이나 수정된 목표의 상태로 재사용하지 않는다.
