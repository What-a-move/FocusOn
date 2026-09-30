# DOM·OCR 입력 텍스트 검증·전처리 PLAN

## 기본 정보

- 기능명: DOM·OCR 입력 텍스트 검증·전처리
- 기능 ID: `input-text-validation-preprocessing`
- 작성자: `lyg123d`
- 작성일: 2026-09-30
- 우선순위: MVP - 핵심
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)
- 작업 Branch: `feat/11-ai-input-text-preprocessing`
- 관련 문서: `AI/docs/content_acquisition_spec.md`, `AI/docs/state_model.md`, `AI/docs/api_spec.md`, `AI/docs/data_lifecycle.md`, `AI/docs/DEVELOPMENT_RULES.md`

## 기능 목적

Chrome DOM 또는 로컬 OCR에서 전달된 최소 텍스트를 관련성 판단에 안전하게 전달할 수 있도록 검증하고 정제한다.

- 공통 내부 문단 구조로 변환한다.
- 제목·본문·코드·표·OCR 문단의 종류와 순서를 보존한다.
- 민감정보, 추출 실패, 제외 상태, 품질 부족, 오래된 이벤트를 관련성 또는 이탈 판정으로 바꾸지 않는다.
- 정제 콘텐츠의 원문 없는 해시를 만들어 이후 중복 감지와 캐시 키에 사용할 수 있게 한다.

이 기능은 DOM 수집, 화면 캡처, Apple Vision OCR 실행, 임베딩·Jev·LLM 관련성 판단, 체류 시간·이탈 판단, Server 연동을 담당하지 않는다.

## 적용 규칙

- `AI-PRIV-001`: 제외·민감 화면은 모델 호출 전에 차단한다.
- `AI-PRIV-002`: DOM/OCR 원문·민감 문자열을 로그·캐시·오류 메시지에 남기지 않는다.
- `AI-FAIL-001`: 추출·검증 실패를 무관 또는 이탈로 변환하지 않는다.
- `AI-STALE-001`: 현재 실행 구간과 맞지 않는 이벤트는 결과를 적용하지 않는다.
- `AI-STATE-001`: 추출·분석·관련성·이탈 상태를 분리한다.

`AI-STALE-001`, `AI-STATE-001`은 현재 제안 규칙이므로 Server·Shared 담당자와 최종 enum·식별자 형식을 확인한 뒤 구현한다.

## 입력 계약

전처리는 Client가 만든 전체 HTML이나 원본 이미지가 아니라, Server 검증 뒤 전달된 최소 메타데이터와 DOM/OCR 문단만 받는다.

```json
{
  "eventId": "uuid",
  "sessionId": "uuid",
  "runId": "uuid",
  "goalVersion": 1,
  "navigationId": "navigation-003",
  "extractionMethod": "DOM",
  "extractionStatus": "SUCCESS",
  "title": "JWT 인증 필터 오류 해결",
  "passages": [
    {
      "id": "passage-001",
      "kind": "BODY",
      "text": "인증 필터 순서와 403 오류 해결 방법",
      "order": 1
    }
  ]
}
```

- `extractionMethod`: 최소 `DOM`, `OCR`을 구분한다.
- `kind`: `TITLE`, `HEADING`, `BODY`, `CODE`, `TABLE`, `CAPTION`, `OCR`처럼 제한된 값만 허용한다. 최종 enum은 공유 계약 확인이 필요하다.
- 식별자, 추출 상태, 문단 ID·순서의 형식과 중복 여부를 먼저 확인한다.
- 전체 URL, URL Query·Fragment, 폼 입력값, 전체 HTML, 원본 캡처 이미지는 받지 않는다.

## 처리 흐름

```text
입력 Schema·식별자 검증
  → 제외·민감·추출 실패 상태 확인
  → 문단 구조·순서·출처 검증
  → 민감 문자열 2차 검사
  → 공백·HTML 잔여 문자·반복 메뉴 정리
  → 빈 입력·본문 부족·깨진 문자·중복 문단 품질 검사
  → 정제 콘텐츠 해시 생성
  → 전처리 결과 또는 비분석 상태 반환
```

1. 제외·민감·실패·미지원 상태면 텍스트를 정제하거나 분석기에 전달하지 않는다.
2. 안전한 문단만 정제한다. 코드·표의 구조와 부정 표현은 임의로 고치지 않는다.
3. 정제 후 `kind`와 텍스트가 완전히 같은 문단만 제거하고 처음 등장한 문단의 `kind`, `id`, `order`를 보존한다. 메뉴 여부를 텍스트만으로 추측해 다른 문단을 삭제하지 않는다.
4. 품질 부족은 `PARTIAL` 또는 `FAILED`와 원문 없는 사유 코드로 반환한다.
5. 정제된 제목과 순서 있는 문단을 정규화한 뒤 `contentHash`를 생성한다. 이벤트·탐색·문단 ID는 해시에 넣지 않고, 해시와 원문을 로그로 남기지 않는다.
6. stale 판정은 요청 필드끼리 비교해서는 불가능하므로 호출자가 전달한 현재 이벤트 문맥과 비교한다. 비동기 결과 적용 시에는 호출자가 현재 문맥을 다시 확인한다.

## 구현 범위

예상 구현 파일은 아래와 같다.

```text
AI/src/preprocessing/models.py
AI/src/preprocessing/content_parser.py
AI/src/preprocessing/text_cleaner.py
AI/src/preprocessing/validator.py
AI/tests/test_content_parser.py
AI/tests/test_text_cleaner.py
AI/tests/test_preprocessing_validator.py
```

- `models.py`: DOM/OCR 공통 내부 문단과 전처리 결과 모델. 순수 모듈 단계에서는 표준 라이브러리 dataclass를 사용하며 외부 API Schema는 별도 계약 확정 후 결정한다.
- `content_parser.py`: 문단 형식, 순서, 중복을 검증하고 공통 구조로 변환
- `text_cleaner.py`: 공백·HTML 잔여 문자·반복 메뉴 정리와 콘텐츠 해시 생성
- `validator.py`: 상태·식별자·민감 문자열·품질 검사와 비분석 결과 생성

이번 기능에는 모델 호출을 넣지 않는다.

## 반환 계약

성공 시 정제 콘텐츠와 품질 상태만 반환한다. 관련성·이탈·알림을 반환하지 않는다.

```json
{
  "extractionStatus": "SUCCESS",
  "preprocessingStatus": "COMPLETED",
  "qualityStatus": "SUFFICIENT",
  "contentHash": "sanitized-content-hash",
  "title": "JWT 인증 필터 오류 해결",
  "passages": [
    {
      "id": "passage-001",
      "kind": "BODY",
      "text": "인증 필터 순서와 403 오류 해결 방법",
      "order": 1
    }
  ],
  "reasonCode": null
}
```

비분석 상태의 예시는 다음과 같다.

| 상황 | 반환 방향 | 관련성·이탈 처리 |
| --- | --- | --- |
| 제외 화면 | `SKIPPED` + `PRIVACY_EXCLUDED` | 생성하지 않음 |
| 민감정보 후보 | `BLOCKED` + `PRIVACY_BLOCKED` | 생성하지 않음 |
| OCR·DOM 추출 실패 | `SKIPPED` + 추출 사유 코드 | 생성하지 않음 |
| 본문 부족·품질 부족 | `PARTIAL` 또는 `FAILED` | 관련성 판단 보류 |
| 현재 실행 구간과 불일치 | `STALE_EVENT` | 결과 폐기 |

최종 enum 이름과 공개 API 표현은 #2, #10 및 Server·Shared 계약 확인 뒤 확정한다.

OCR 인식 점수의 품질 경계값은 제품 기본값으로 고정하지 않고 평가된 설정으로 주입한다. 인식 점수는 관련성 신뢰도가 아니다.

## 테스트 계획

- 정상 DOM 문단이 공통 구조로 변환되고 순서가 유지되는지 확인한다.
- OCR 문단의 공백·줄바꿈 정리 후 문단 종류와 순서가 유지되는지 확인한다.
- HTML 잔여 문자와 반복 메뉴 문단이 정리되는지 확인한다.
- 빈 입력, 본문 부족, 깨진 문자, 중복 문단이 품질 결과로 구분되는지 확인한다.
- 이메일·전화번호·Bearer Token·JWT·API Key·결제 카드 후보가 발견되면 원문 없이 차단되는지 확인한다.
- 제외·민감·추출 실패·미지원 상태가 후속 분석기로 전달되지 않는지 확인한다.
- `eventId`, `runId`, `goalVersion`, `navigationId` 불일치가 stale 처리되는지 확인한다.
- 동일한 정제 콘텐츠에서 같은 해시가 생성되고, 다른 안전한 콘텐츠는 구분되는지 확인한다.
- 로그와 예외 메시지에 DOM/OCR 원문·민감 문자열이 남지 않는지 확인한다.

## 완료 조건

- [ ] DOM·OCR 입력을 동일한 내부 문단 구조로 변환한다.
- [ ] 문단 종류와 순서를 보존한다.
- [ ] 제외·민감·실패·미지원 상태에서 후속 분석을 진행하지 않는다.
- [ ] 품질 부족을 관련성·이탈 결과로 바꾸지 않는다.
- [ ] 정제 콘텐츠 해시를 생성한다.
- [ ] 정상·빈 입력·OCR 품질 부족·중복·민감정보·stale 이벤트 테스트를 작성한다.
- [ ] 구현 중 오류가 있으면 `input-text-validation-preprocessing-ERROR.md`에 기록한다.
- [ ] 구현 완료 후 `input-text-validation-preprocessing-REPORT.md`, `CONTEXT.md`, `NEXT_TASK.md`를 실제 결과에 맞게 갱신한다.
