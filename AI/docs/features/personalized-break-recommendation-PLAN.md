# AI 기능 기획서: 개인화 휴식 추천

> 상태: **MVP 제안 — 사용자·개인정보·Server 담당자 승인 후 구현**
> 이 문서는 카메라 기반 건강·의료 판단 기능이 아니라, 사용자가 선택한 FocusOn 사용 패턴을 바탕으로 휴식 선택지를 제안하는 기능의 계약이다.

## 기본 정보

- 기능명: 개인화 휴식 추천
- 기능 ID: `personalized-break-recommendation`
- 작성일: 2026-09-22
- 우선순위: MVP 후보 — 개인정보·사용자 동의·최근 7일 데이터 계약 확정 후
- 관련 Issue: 미생성
- 예정 Branch: Issue 생성 후 결정
- 관련 문서:
  - `AI/docs/state_model.md`
  - `AI/docs/feedback_personalization_spec.md`
  - `AI/docs/data_lifecycle.md`
  - `AI/docs/analysis_rules.md`
  - `docs/DATA_PRIVACY.md`

## 1. 기능 목적과 원칙

### 해결할 문제

모든 사용자에게 동일한 시간 간격으로 휴식을 권하지 않는다. 사용자의 **최근 7일 FocusOn 사용 방식**(세션 진행 시간, 실제 휴식 선택, 이탈 위험 시점)을 규칙 기반으로 집계해 현재 세션의 휴식 제안 기준으로 삼는다. 카메라 신호는 선택적 보조 분기이며 추천의 필수 조건이 아니다.

예: 최근 7일에 35~45분가량 집중한 뒤 사용자가 자주 휴식을 선택했고, 현재도 충분히 긴 세션에서 안정적으로 관측된 이탈 신호가 반복되면, “잠깐 쉬고 다시 시작할까요?”라는 선택지를 한 번 제안한다.

### 반드시 지킬 원칙

- 이 기능은 **기본 OFF**다. 사용자가 별도 안내를 읽고 ON을 선택하기 전에는 카메라 개인화 데이터 수집·업로드·프로필 생성·추천을 모두 하지 않는다.
- 첫 ON 뒤 충분한 기록이 쌓이기 전까지는 **학습 기간**이다. 유효 기록이 부족하면 개인화 추천을 하지 않는다.
- 최근 7일의 최소 집계값만 사용하며, 현재 세션의 한 번의 반응이 즉시 개인화 기준을 확정하지 않도록 버전과 쿨다운을 적용한다.
- 원본 영상, 카메라 프레임, 얼굴·자세 랜드마크, blendshape, 얼굴 변환 행렬, 시선 좌표를 서버로 보내거나 저장하지 않는다.
- 카메라 미감지, 조명 부족, 가림, 카메라 OFF, 권한 거부는 집중 이탈이나 휴식 필요의 근거가 아니다.
- 추천은 강제 동작이 아니다. 자동 일시정지·차단·불이익·집중도 점수화는 하지 않는다.
- “피로”, “건강 이상”, “집중력 저하를 진단했다”와 같은 의료·심리적 표현을 사용하지 않는다.
- LLM은 추천 여부와 문구를 결정하지 않는다. 정해진 규칙과 정해진 문구를 사용한다.

### AI가 담당하는 역할

1. 최소화된 최근 7일 집계값의 유효성을 검증한다.
2. 유효한 기록으로 `PersonalBreakProfile`을 생성하거나 갱신한다.
3. 현재 세션의 요약값과 프로필을 비교해 `SUGGEST_BREAK` 또는 `NO_ACTION`을 반환한다.
4. 부족한 근거, 오래된 결과, 동의 철회, 신호 품질 저하에서는 보수적으로 `NO_ACTION`을 반환한다.
5. 프로필 버전·알고리즘 버전·평가 지표를 관리한다.

### AI가 담당하지 않는 범위

- 카메라 출시 결정, 권한 요청, ON/OFF UI, MediaPipe 실행, 15초 프레임 집계
- 알림 표시와 사용자의 휴식 시작·나중에·닫기 동작 처리
- 원본 카메라·화면·OCR 데이터 수집
- 집중 이탈 확정, 건강·피로·감정 상태 추론
- LLM을 이용한 개인별 알림 문구 생성

## 2. 사용자 동의와 최근 7일 동작 모델

### 동의 화면에 명시할 내용

ON 전 사용자에게 다음을 명확히 알린다.

- 목적: 최근 7일 FocusOn 사용 패턴을 기준으로 현재 세션에 짧은 휴식 선택지를 제안한다.
- 서버 전송값: 원본 영상이 아닌 세션 길이, 휴식 선택, 카메라 신호의 **시간 구간별 집계값**이다.
- 수집하지 않는 값: 영상·스크린샷·음성·얼굴/자세 랜드마크·정확한 시선 좌표·개별 프레임이다.
- 충분한 유효 기록이 쌓이기 전에는 데이터를 모으기만 하며 개인화 추천을 하지 않는다.
- ON/OFF와 개인화 데이터 삭제 경로를 설정에서 언제든 제공한다.

### ON/OFF 상태

| 상태 | 클라이언트 처리 | 서버·AI 처리 |
| --- | --- | --- |
| 기본 OFF | MediaPipe 개인화 집계·전송 없음 | 프로필 생성·추천 없음 |
| ON, 기록 부족 | 로컬 15초 신호를 5분/세션 단위로 요약해 전송 | 최근 7일 집계만 보관, 추천 없음 |
| ON, 프로필 준비됨 | 동일한 최소 요약값 전송 | 최근 7일 프로필로 현재 세션 추천 후보 계산 |
| OFF 전환 | 즉시 수집·전송·추천 중지, 메모리 버퍼 폐기 | 대기 작업 취소, 프로필·집계·캐시 삭제 요청 |

**기본 제안:** OFF는 단순 알림 숨김이 아니라 개인화 기능의 철회로 처리한다. 즉시 서버 전송을 멈추고 이미 저장된 개인화 프로필·행동 집계·대기 배치 작업·캐시를 삭제한다. 이 삭제 범위와 보존 기간은 개인정보 책임자의 최종 승인이 필요하다.

### 최근 7일 집계 흐름

Server는 계정 시간대 기준으로 최근 7일 분석 창을 계산한다. 시간대 변경으로 동일 이벤트가 중복 집계되지 않도록 분석 창과 `profileVersion`을 고정한다.

```text
사용자가 개인화 휴식 추천 ON
  → 최근 7일: 로컬 신호를 최소 집계하여 수집
  → 분석 창 갱신: 유효 데이터 충분성 검사
  → PersonalBreakProfile 생성 또는 INSUFFICIENT_DATA
  → 현재 세션: 최근 7일 프로필 + 현재 세션 요약으로 추천 여부 판단
  → 새 분석 창: 새 프로필 버전으로 교체
```

- 최근 7일 분석은 Server의 결정적 통계·규칙 작업이며, 키는 `userId + analysisWindow + profileAlgorithmVersion`으로 멱등 처리한다.
- 늦게 도착한 이벤트는 현재 분석 창의 허용 범위 안에서만 반영한다. 확정된 `profileVersion`을 조용히 변경하지 않는다.
- 최근 7일 데이터가 부족하면 `INSUFFICIENT_DATA` 프로필을 남기거나 생성하지 않고 개인화 추천을 하지 않는다.
- 추천 결과에는 항상 `analysisWindow`와 `profileVersion`을 포함한다.

## 3. 데이터 최소화와 입력 계약

### 로컬 처리 계층

MediaPipe는 Desktop의 Worker에서 실행한다. 프레임마다 나온 결과는 기기 메모리에서만 사용하고 15초 창으로 정제한다. UI 메인 스레드를 막지 않으며, 프레임 드롭은 정상 동작으로 취급한다.

```text
카메라 프레임
  → Desktop Worker의 MediaPipe
  → 15초 로컬 신호 집계·품질 판정
  → 5분 버킷 또는 세션 종료 시 최소 요약 생성
  → (ON·유효 세션·비제외 상태일 때만) Server 전송
  → 주 종료 배치 프로필 / 현재 세션 추천 판단
```

15초 집계는 로컬 즉시 UI 규칙에도 쓸 수 있으나, 개인화 서버에는 15초 단위 원본 결과를 계속 보내지 않는다. 서버 전송 단위는 **5분 버킷 또는 세션 종료 요약**이다. 이로써 전송 빈도·비용·행동 추적 정밀도를 낮춘다.

### 전송 가능한 최소 데이터

```json
{
  "runId": "opaque-run-id",
  "bucketId": "opaque-5-minute-bucket-id",
  "startedAt": "2026-09-14T01:00:00Z",
  "endedAt": "2026-09-14T01:05:00Z",
  "activeStudySeconds": 300,
  "cameraEnabledSeconds": 300,
  "validSignalSeconds": 246,
  "sustainedAwayEventCount": 1,
  "localNudgeShownCount": 0,
  "cameraState": "AVAILABLE",
  "signalQuality": "SUFFICIENT",
  "consentVersion": "v1",
  "sourceVersion": "desktop-1"
}
```

| 필드 | 사용 목적 | 제한 |
| --- | --- | --- |
| `runId`, `bucketId` | 세션 연결·멱등 처리 | 사용자 식별자는 인증 토큰에서만 결합 |
| 시간 구간, `activeStudySeconds` | 최근 7일 세션 길이와 현재 지속 시간 | 초 단위 정밀 원본 타임라인을 장기 보관하지 않음 |
| `validSignalSeconds` | 카메라 신호 품질의 분모 | 미감지를 부정 신호로 바꾸지 않음 |
| `sustainedAwayEventCount` | 반복적·유효 관측 변화의 보조 근거 | 방향·좌표·랜드마크·개별 지속시간 전송 금지 |
| `localNudgeShownCount` | 같은 세션 중 중복 제안 방지 | 행동 점수 또는 성과 평가에 사용 금지 |
| 동의·소스 버전 | 동의 검증·회귀 분석 | 화면/OCR/앱 제목/URL 포함 금지 |

### 절대 전송·저장 금지

- 카메라 영상, 프레임, 스크린샷, 음성
- 얼굴·손·몸 랜드마크, blendshape, 변환 행렬, 시선·얼굴 방향의 연속 좌표
- OCR 원문, 페이지 본문, URL, 앱 이름, 창 제목
- `NO_FACE`, 카메라 OFF, 조명 부족이 발생한 개별 시각과 그 이유를 세밀한 행동 로그로 저장하는 데이터

### 입력 검증

Server와 AI는 다음을 모두 확인한다.

- 활성 동의의 사용자·기기·세션 소유권이 일치하는가
- `bucketId`가 중복이 아닌가. 중복이면 동일 응답을 반환하고 다시 계산하지 않는다.
- `endedAt > startedAt`, 구간 길이와 초 단위 값이 논리적으로 일치하는가
- `0 <= validSignalSeconds <= cameraEnabledSeconds <= activeStudySeconds`인가
- `sustainedAwayEventCount`가 프로토콜 상한을 넘지 않는가
- 현재 세션이 종료·제외·동의 철회·오래된 `runId` 상태가 아닌가
- `consentVersion`, `sourceVersion`, `profileAlgorithmVersion`을 해석할 수 있는가

검증 실패는 사용자 행동의 이상이 아니라 `INVALID_INPUT`이며, 추천 없이 폐기·감사 가능한 최소 오류 코드만 남긴다.

## 4. 최근 7일 프로필 생성 기준

### 프로필 생성 전제

개인화는 한두 번의 카메라 신호로 만들지 않는다. 다음은 초기 **실험값**이며, 실제 사용자 동의 데이터가 아닌 합성 데이터와 제한된 옵트인 평가를 통과한 후 조정한다.

| 조건 | 초기 실험값 | 처리 |
| --- | ---: | --- |
| 최근 7일 내 유효 학습 세션 | 3회 이상 | 미만이면 `INSUFFICIENT_DATA` |
| 총 활성 학습 시간 | 120분 이상 | 미만이면 `INSUFFICIENT_DATA` |
| 신호 품질 충분 세션 | 2회 이상 | 카메라 기반 보조 기준을 만들지 않음 |
| 사용자 휴식 피드백 | 1회 이상 권장 | 없으면 시간 기준만 보수적으로 사용 |

카메라가 없어도 세션·휴식 선택 데이터만 충분하면 **시간 기반 개인화**는 가능하다. 다만 카메라 신호에 의존하는 추천 분기는 만들지 않는다.

### 생성하는 프로필

```json
{
  "profileStatus": "READY",
  "analysisWindow": "2026-09-07/2026-09-14",
  "profileVersion": "pbr-v1-opaque",
  "algorithmVersion": "pbr-rules-v1",
  "typicalFocusIntervalMinutes": 40,
  "minimumRecommendationMinutes": 25,
  "maximumRecommendationsPerSession": 1,
  "cameraSignalBranchEnabled": true,
  "evidence": {
    "validSessionCount": 4,
    "activeStudyMinutes": 185,
    "breakFeedbackCount": 3
  }
}
```

프로필에는 원본 버킷·세션의 배열이나 상세 시계열을 넣지 않는다. 추천에 필요한 집계 통계와 버전만 보관한다.

### 개인화 기준 계산

1. **활성 세션 시간**은 사용자가 시작한 FocusOn 세션에서 일시정지·제외·종료 구간을 뺀 시간이다. 카메라가 보이지 않은 시간을 자동으로 빼거나 벌점으로 두지 않는다.
2. 사용자가 `휴식 시작`을 선택했거나 3분 이상 명시적으로 일시정지한 시점을 “자발적 휴식”으로 본다. 단순 앱 전환·카메라 미감지는 휴식으로 간주하지 않는다.
3. 최근 7일의 “자발적 휴식 직전 활성 시간” 중앙값을 기본 개인 간격 후보로 삼는다. 이상값 영향 방지를 위해 사분위 범위를 이용한다.
4. 후보는 `25~60분`의 안전한 제품 범위 안으로 제한한다. 데이터가 한쪽에 치우쳐도 25분보다 이른 제안이나 60분을 초과한 지연을 개인화하지 않는다.
5. 자발적 휴식 피드백이 부족하면 최근 7일 유효 세션 길이 중앙값의 80%를 보수적 후보로 사용한다. 이 대체값도 조건을 충족할 때만 사용한다.
6. 신호 품질이 충분한 세션에서만 `sustainedAwayEventCount`의 중앙 경향을 계산한다. `NO_FACE`, 저조도, 가림, 카메라 OFF 구간은 분자·분모 모두에서 제외한다.
7. `typicalFocusIntervalMinutes`는 현재 분석 창의 `profileVersion`에 고정한다. 한 번의 반응으로 즉시 수정하지 않는다.

## 5. 휴식 추천 판단 기준

### 결과 상태

| 결과 | 의미 | 클라이언트 동작 |
| --- | --- | --- |
| `SUGGEST_BREAK` | 충분한 개인화 근거가 있어 휴식 선택지를 한 번 제안 | 비강제 카드 표시 |
| `NO_ACTION` | 제안 근거가 부족하거나 쿨다운·한도·동의 조건에 걸림 | 아무 것도 표시하지 않음 |
| `INSUFFICIENT_DATA` | 최근 7일 프로필을 만들 데이터가 부족함 | 아무 것도 표시하지 않음 |
| `UNAVAILABLE` | 서버·프로필·입력·세션 상태를 신뢰할 수 없음 | 아무 것도 표시하지 않음 |
| `PRIVACY_BLOCKED` | OFF, 동의 철회, 제외 상태 | 아무 것도 표시하지 않음 |
| `STALE` | 추천 계산 중 세션·프로필 버전이 바뀜 | 늦은 결과 폐기 |

### 공통 Gate

아래 조건을 **모두** 만족해야 다음 판단으로 간다.

```text
personalizationEnabled = true
AND active consent is valid
AND session is active and not excluded
AND recent analysis profileStatus = READY
AND profile version matches the request
AND current recommendation count < 1 per logical session
AND no user "later" cooldown is active
AND no break is already active
```

- `나중에` 선택 시: 현재 논리 세션에서는 다시 제안하지 않는다.
- `닫기` 선택 시: 같은 세션에서는 다시 제안하지 않는다.
- `휴식 시작` 선택 시: 휴식이 끝나고 새 집중 구간이 시작되어야 다음 판단이 가능하다.
- 네트워크 실패로 추천 결과를 못 받으면 로컬에서 임의의 개인화 추천을 만들지 않는다.

### 추천 분기 A — 사용 습관 기반

아래는 카메라가 없어도 가능한 기본 분기다.

```text
현재 연속 활성 시간 >= typicalFocusIntervalMinutes
AND 현재 연속 활성 시간 >= minimumRecommendationMinutes
AND 이번 세션에서 추천한 적 없음
→ SUGGEST_BREAK (reasonCode: PERSONAL_INTERVAL_REACHED)
```

이 분기는 “집중을 못한다”는 판정이 아니다. 사용자가 이전 주에 선택한 휴식 리듬을 현재 시점에 다시 알려 주는 기능이다.

### 추천 분기 B — 반복된 유효 신호의 보조 근거

아래 분기는 A의 시간 조건을 만족한 뒤에만 적용한다. 카메라 결과 하나만으로 추천하지 않는다.

```text
분기 A 시간 조건 충족
AND 최근 10분의 validSignalSeconds 비율 >= 60%
AND 최근 10분의 sustainedAwayEventCount >= 2
AND 최근 7일 유효 세션에서 같은 신호가 반복적으로 관측됨
→ SUGGEST_BREAK (reasonCode: PERSONAL_INTERVAL_AND_SUSTAINED_SIGNAL)
```

- `60%`, 최근 10분, 2회는 **초기 실험값**이다. 이 값은 사람의 집중력이나 시선을 측정하는 기준이 아니며, 모델 평가 후 변경할 수 있다.
- 자세 변화, 표정, 한 번의 고개 회전은 휴식 추천의 근거가 아니다.
- 신호가 부족하면 분기 B를 포기하고 분기 A만 적용하거나 `NO_ACTION`으로 끝낸다. 결측을 음성 신호로 대체하지 않는다.

### 중복·과다 추천 방지

- 논리 세션당 최대 1회만 `SUGGEST_BREAK`을 생성한다.
- 동일 `runId + profileVersion + reasonCode`의 결과는 멱등이다.
- 세션 종료·새 세션 시작·사용자 동의 철회·목표/세션 무효화가 발생하면 미표시 결과와 캐시를 폐기한다.
- 추천을 수락하지 않았다는 사실을 이후 세션에 불이익·더 강한 알림의 근거로 사용하지 않는다.
- 배너나 모달을 반복 노출하지 않고, 사용자가 명시적으로 선택한 뒤에만 다음 기회로 넘어간다.

### 사용자 메시지와 액션

문구는 고정 템플릿으로 제공한다.

> 최근 7일 FocusOn 사용 흐름을 기준으로, 지금은 잠깐 쉬고 이어가는 선택을 제안해요.

- `5분 쉬기`: 사용자가 직접 휴식 타이머를 시작한다.
- `나중에`: 현재 세션 재추천을 중지한다.
- `닫기`: 현재 세션 재추천을 중지한다.
- 자동 일시정지, 점수 차감, 보호자·제3자 통보는 없다.

## 6. AI/Server API 계약 초안

### 최근 7일 프로필 생성 요청 (Server 규칙 작업 → AI)

```json
{
  "userId": "server-internal-id",
  "analysisWindowStart": "2026-09-07",
  "analysisWindowEnd": "2026-09-14",
  "timezone": "Asia/Seoul",
  "consentVersion": "v1",
  "algorithmVersion": "pbr-rules-v1",
  "recentAggregate": {
    "validSessionCount": 4,
    "activeStudyMinutes": 185,
    "voluntaryBreakIntervalsMinutes": [38, 42, 35],
    "sufficientSignalSessionCount": 3,
    "sustainedAwayEventCounts": [1, 0, 2],
    "breakFeedback": { "accepted": 2, "later": 1, "dismissed": 0 }
  }
}
```

`recentAggregate`는 Server가 원본 버킷을 집계해 만든 최소 입력이다. AI는 개별 5분 버킷·카메라 원본을 받지 않아도 프로필을 만들 수 있게 한다.

### 현재 세션 추천 판단 요청 (Server → AI)

```json
{
  "runId": "opaque-run-id",
  "profile": {
    "profileStatus": "READY",
    "analysisWindow": "2026-09-07/2026-09-14",
    "profileVersion": "pbr-v1-opaque",
    "typicalFocusIntervalMinutes": 40,
    "minimumRecommendationMinutes": 25,
    "cameraSignalBranchEnabled": true
  },
  "currentSession": {
    "continuousActiveMinutes": 42,
    "recommendationCount": 0,
    "userResponse": "NONE",
    "recentSignal": {
      "validSignalRatio": 0.72,
      "sustainedAwayEventCount": 2
    }
  },
  "consentActive": true,
  "excluded": false
}
```

### AI 응답

```json
{
  "analysisStatus": "SUCCESS",
  "recommendedAction": "SUGGEST_BREAK",
  "reasonCodes": [
    "PERSONAL_INTERVAL_REACHED",
    "PERSONAL_INTERVAL_AND_SUSTAINED_SIGNAL"
  ],
  "suggestedBreakMinutes": 5,
  "analysisWindow": "2026-09-07/2026-09-14",
  "profileVersion": "pbr-v1-opaque",
  "algorithmVersion": "pbr-rules-v1",
  "expiresAt": "2026-09-16T03:10:00Z"
}
```

- `reasonCodes`는 내부 품질 측정·설명 템플릿 선택용이다. 클라이언트는 이를 사용자에게 “집중 실패”로 번역하지 않는다.
- 응답은 `runId`, `profileVersion`, 동의 상태를 다시 검증한 뒤에만 표시한다.
- API 경로, 인증 방식, 오류 enum은 실제 구현 전에 `AI/docs/api_spec.md`와 Server 계약으로 확정한다.

## 7. 성능·비용 최적화

| 단계 | 비용/지연 전략 |
| --- | --- |
| MediaPipe | Desktop Worker에서 제한된 FPS로 처리. 프레임 전체를 네트워크로 전송하지 않음 |
| 로컬 15초 집계 | 메모리에서 상태값으로 축약. 프레임·랜드마크 즉시 폐기 |
| 네트워크 | 15초마다가 아니라 5분 버킷 또는 세션 종료 시 전송. 중복 `bucketId` 제거 |
| 최근 7일 분석 | 사용자·분석 창·알고리즘 버전 키의 단일 멱등 규칙 작업. LLM·GPU 호출 없음 |
| 세션 추천 | 프로필을 세션 시작 시 1회 읽고, 필요 시 작은 규칙 계산만 수행 |
| 캐시 | `userId + analysisWindow + algorithmVersion` 프로필만 짧게 캐시. 동의 철회·분석 창 전환 시 즉시 무효화 |

- 이 기능에는 ColPali, OCR, 화면 이미지, 임베딩 모델, LLM, GPU 추론을 사용하지 않는다.
- 따라서 비용의 대부분은 기존 로컬 MediaPipe 실행과 작은 집계 저장/조회이며, 사용자가 ON인 경우에만 발생한다.
- 추천 판단은 1초 이내 응답을 목표로 하되, 지연될 경우 사용자 경험을 막지 않고 `NO_ACTION`으로 안전하게 종료한다.

## 8. 실패·예외 처리

| 상황 | AI 반환 | 처리 |
| --- | --- | --- |
| 기능 OFF 또는 동의 철회 | `PRIVACY_BLOCKED` | 업로드·프로필·추천·캐시 중단/삭제 |
| 첫 주 또는 데이터 부족 | `INSUFFICIENT_DATA` | 일반 알림으로 대체하지 않고 무추천 |
| 카메라 OFF·권한 거부·NO_FACE·저조도 | `NO_ACTION` | 행동 부정 판정 금지, 시간 기반 분기만 별도 가능 |
| 제외 앱·도메인·민감 상태 | `PRIVACY_BLOCKED` | 카메라 집계·전송·추천 중지 |
| 버킷 중복 | 이전 결과 재사용 | 재집계·재추천 금지 |
| 잘못된 시간/카운트 | `INVALID_INPUT` | 해당 입력 폐기, 사용자 알림 없음 |
| 배치 재시도 | 동일 멱등 키 결과 반환 | 프로필 중복 생성 금지 |
| 추천 중 세션/프로필 변경 | `STALE` | 응답 폐기 |
| AI/Server timeout | `UNAVAILABLE` | 추천하지 않음, 재시도 Queue로 늦은 알림 금지 |
| OFF 전환 중 작업 실행 | `PRIVACY_BLOCKED` | 작업 취소 및 출력·캐시 삭제 |

## 9. 보안·개인정보·보존

- 전송 전 Desktop이 동의·제외 상태를 확인하고, Server가 동의·소유권·제외 상태를 다시 검증한다.
- 로그에는 `runId` 해시, 결과 코드, 알고리즘 버전, 지연 시간만 남긴다. 카메라 값·상세 시간대·휴식 이유·원본 데이터는 로그에 남기지 않는다.
- 최근 7일 집계, 프로필, 사용자 피드백은 새 데이터 항목이므로 구현 전 `AI/docs/data_lifecycle.md`에 **목적·저장 위치·최소 보존 기간·삭제 전파·백업 삭제 방식**을 확정한다.
- 기본 제안의 삭제 전파 대상은 최근 집계, `PersonalBreakProfile`, Redis/메모리 캐시, 진행 중인 규칙 작업, 재시도 작업이다.
- 운영·오류 분석은 합성 데이터 또는 복원 불가능한 집계 지표로 한다. 실제 사용자의 원본 카메라 데이터로 디버그하지 않는다.

## 10. 평가 계획과 출시 차단 조건

### 합성 평가 데이터

- 첫 주 ON, 둘째 주 프로필 생성·추천
- 세션 1~2회 또는 120분 미만으로 인한 `INSUFFICIENT_DATA`
- 카메라 없이 충분한 세션·자발적 휴식 데이터가 있는 경우
- 카메라 ON이지만 `NO_FACE`, 저조도, 가림이 많은 경우
- 신호 한 번만 발생, 반복 신호 발생, 신호는 많지만 활성 시간이 짧은 경우
- 동일 버킷 재전송, 순서가 뒤바뀐 버킷, 시간대 변경, 주 경계 통과
- `나중에`, `닫기`, `휴식 시작`, 세션 종료, OFF 전환 중 응답 도착
- Server/AI timeout, 배치 재시도, 오래된 프로필 응답

### 품질 지표

- ON 사용자 중 프로필 생성 가능 비율과 `INSUFFICIENT_DATA` 비율
- 세션당 추천 수(반드시 1 이하)
- 추천 수락·나중에·닫기 비율과 반복 노출 여부
- OFF 전환 후 업로드·추천·프로필 접근이 0건인지
- 카메라 결측 상태에서 신호 기반 추천이 0건인지
- 최근 7일 규칙 작업 성공률·중복 프로필 생성 0건·P95 처리 시간

수락률만 최대화하면 사용자를 과도하게 방해할 수 있으므로, `세션당 추천 수`, `닫기/나중에 비율`, `OFF 전환 비율`을 동등한 보호 지표로 본다.

### 출시 차단 조건

- ON 전 개인화 데이터가 전송·저장되거나, OFF 후 추천이 발생한다.
- 원본 영상/프레임/랜드마크 또는 이를 복원 가능한 세밀한 시계열이 전송·로그·캐시에 남는다.
- 카메라 미감지·저품질이 집중 이탈 또는 휴식 필요로 판정된다.
- 개인화 프로필 없이 첫 주에 개인화 추천을 한다.
- 한 논리 세션에 두 번 이상 추천하거나, `나중에/닫기` 뒤 다시 추천한다.
- LLM·ColPali·OCR 결과가 이 기능의 추천 기준으로 들어간다.

## 11. 구현 순서와 완료 조건

1. 개인정보·제품 담당자가 ON 동의 문구, OFF 시 삭제 범위, 보존 기간을 확정한다.
2. Desktop은 Worker 기반 로컬 15초 집계와 5분/세션 요약을 구현한다.
3. Server는 동의 검증, 버킷 멱등 저장, 최근 7일 분석 창 계산, 삭제 전파, 규칙 작업 scheduler를 구현한다.
4. AI는 입력 검증, `PersonalBreakProfile` 생성, 세션 추천 규칙, 상태·버전 검증을 구현한다.
5. 클라이언트는 고정 문구와 `5분 쉬기/나중에/닫기` 피드백을 구현한다.
6. 합성 데이터 회귀 평가와 개인정보 삭제·중복 방지·주 경계 테스트를 통과한 뒤 제한된 옵트인으로 실험한다.

### AI 완료 조건

- [ ] 입력·출력 계약과 상태 enum을 Server·Desktop과 확정했다.
- [ ] ON 전 무수집, OFF 후 즉시 중단·삭제를 자동 테스트했다.
- [ ] 첫 주 무추천과 직전 완료 주 프로필만 사용하는 규칙을 테스트했다.
- [ ] `INSUFFICIENT_DATA`, `UNAVAILABLE`, `STALE`, `PRIVACY_BLOCKED`를 구분했다.
- [ ] 카메라 결측이 부정 신호가 되지 않음을 테스트했다.
- [ ] 세션당 1회 한도와 사용자 응답 후 재추천 금지를 테스트했다.
- [ ] 모델 없이 규칙 기반으로 비용·지연 목표를 만족함을 측정했다.
- [ ] `data_lifecycle.md`, `state_model.md`, `api_spec.md`, `evaluation_spec.md`, `DECISION_RECORD.md`의 확정 변경을 반영했다.
