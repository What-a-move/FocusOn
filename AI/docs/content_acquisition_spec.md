# 콘텐츠 수집·추출 계약

## 문서 상태

- 상태: 팀 검토용 계약 초안, 미구현
- 버전: `0.1.0`
- 기준일: 2026-09-12
- 관련 Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)
- 관련 규칙: `AI-PRIV-001`, `AI-PRIV-002`, `AI-PIPE-001`, `AI-FAIL-001`, `AI-STALE-001`, `AI-VISION-001`, `AI-VISION-002`, `AI-VISION-003`

이 문서는 Chrome Extension, macOS 네이티브 OCR 프로그램, Server와 AI 사이의 콘텐츠 수집 경계를 정의한다. 현재 Extension의 Content Script와 Service Worker는 진입 파일만 있고 이 계약은 아직 구현되지 않았다.

### 현재 저장소에서 확인한 사실

- `apps/extension/public/content-script.js`와 `service-worker.js`는 설명 주석만 있는 진입 파일이다.
- 현재 Manifest는 `tabs`, `storage`, `webNavigation`과 HTTP(S) Host Permission을 선언한다.
- `activeTab`, `nativeMessaging`과 네이티브 호스트 Manifest는 아직 추가되지 않았다.
- 따라서 캡처 권한 방식, Native Messaging 설치와 실제 메시지 크기는 구현 완료가 아니라 PoC 대상이다.

## 1. 책임 경계

| 구성요소 | 담당 | 담당하지 않는 것 |
| --- | --- | --- |
| Content Script | 허용된 DOM 영역 탐색, 구조 보존 추출, 변경 신호 생성 | 세션 인증, 최종 관련성 판단 |
| Extension Service Worker | 탭·탐색 식별, 제외·권한 검사, 이벤트 중복 제거, Native Messaging 중계 | OCR 실행, AI 직접 호출 |
| macOS 네이티브 모듈 | Apple Vision OCR, 이미지 메모리 처리, OCR 품질 메타데이터 | 외부 이미지 업로드, 학습 관련성 판단 |
| Server | 인증·소유권·최신 버전·크기 제한 검증, AI 중계 | 브라우저 DOM 직접 수집 |
| AI | 정제 문단 검증, 품질 2차 확인, 관련성 분석 | 원본 화면·전체 DOM 수신, 브라우저 제어 |

## 2. 전체 흐름

```text
탭·페이지 변화 감지
  → 세션·일시정지·제외·민감 페이지 검사
  → 안정화와 이벤트 중복 제거
  → 콘텐츠 유형 식별
  → DOM 전용 추출기 또는 일반 추출기
  → 로컬 개인정보·품질 검사
  ├─ 충분함: 정제 문단 생성
  ├─ 부족함: 캡처 권한과 OCR 조건 재검사
  │           → 현재 탭 가시 영역 캡처
  │           → 로컬 Apple Vision OCR
  │           → 이미지 즉시 폐기
  │           → 개인정보·품질 재검사
  └─ 금지·실패·미지원: AI 미호출 또는 비분석 상태
  → Server 인증·소유권·버전 검사
  → AI 분석
  → navigationId 재검사 후 결과 적용
```

제외 여부를 확인하기 전에 DOM 추출, 캡처, OCR, ColPali 또는 Server 전송을 병렬로 시작하지 않는다.

## 3. 감지 이벤트

### 3.1 분석 후보 이벤트

- 활성 탭 전환
- 최상위 URL 또는 SPA 경로 변경
- 영상·문서 ID 변경
- H1·본문·코드·표 등 주요 영역 변경
- 사용자가 의미 있는 새 구간으로 이동한 경우
- 같은 페이지의 체류 정책 재평가 시점
- 목표·피드백·제외 설정 변경

광고 애니메이션, 댓글 수, 시계, 작은 배지와 반복 렌더링은 기본 재분석 트리거에서 제외한다.

### 3.2 안정화와 중복 제거

- 탐색마다 증가하는 `navigationId`를 발급한다.
- DOM 변경은 짧은 안정화 구간으로 묶되 대기 시간은 설정으로 관리한다.
- 같은 정제 콘텐츠 해시와 추출기 버전이면 콘텐츠 분석을 재사용할 수 있다.
- 체류 시간 변화는 콘텐츠 임베딩 재생성 없이 흐름 정책만 재계산한다.
- 새 이벤트가 생기면 이전 탐색의 대기 작업을 취소하거나 결과를 무시한다.

기획서의 2~3초 안정화 값은 실험 가설이며 확정 상수가 아니다.

## 4. 추출 전 Gate

모든 조건을 통과해야 수집을 시작한다.

1. 활성 `runId`가 있고 세션이 `RUNNING`이다.
2. 브라우저 창과 대상 탭이 실제 활성 상태다.
3. 사용자 제외 도메인·앱에 해당하지 않는다.
4. 로그인·결제·인증·메신저 등 민감 화면이 아니다.
5. URL Scheme과 콘텐츠 유형이 허용 목록에 있다.
6. 필요한 Chrome 권한이 현재 탭에 유효하다.
7. `tabId`, `windowId`, `navigationId`를 읽을 수 있다.

안전 여부를 판별할 수 없으면 허용으로 추정하지 않는다. `chrome://`, 확장 내부 페이지, 로컬 파일과 브라우저 보호 페이지는 명시적으로 지원하기 전까지 `UNSUPPORTED`로 처리한다.

## 5. 콘텐츠 유형과 추출기

| 유형 | 우선 추출 | 제외 | Fallback |
| --- | --- | --- | --- |
| 일반 문서 | 제목, H1~H3, 주요 문단, 표, 코드, 대체 텍스트 | 메뉴, 광고, 추천, Footer | 일반 Readability 계열 추출 검토 |
| 검색 결과 | 허용된 검색어, 상위 결과 제목·설명 | 입력 폼, 광고, 전체 결과 | 제목·도메인만으로 `PARTIAL` |
| YouTube | 영상 ID, 제목, 설명, 재생 상태, 허용된 자막 구간 | 댓글, 추천, 계정 정보 | DOM 메타데이터 후 OCR 여부 검토 |
| GitHub | 저장소 설명, README, 현재 파일·Issue의 핵심 내용 | Token, Secrets, 전체 저장소 | 현재 보이는 코드 영역만 제한 |
| Q&A | 질문, 태그, 오류, 핵심 답변·코드 | 댓글 전체, 추천 질문 | 일반 문서 추출 |
| PDF | 접근 가능한 텍스트, 현재 페이지 번호·가시 영역 | 보이지 않는 전체 문서 일괄 캡처 | 로컬 OCR, ColPali PoC 후보 |
| 이미지·Canvas·슬라이드 | 대체 텍스트와 주변 설명 | 배경 이미지·숨김 프레임 | 허용된 가시 영역 OCR |

전용 추출기가 실패하면 일반 추출기를 시도할 수 있지만 제외·민감정보 기준은 완화하지 않는다.

## 6. 정제 콘텐츠 계약

AI로 전달하는 최소 단위는 전체 HTML이나 평문 덩어리가 아니라 구조를 보존한 문단이다.

```json
{
  "contentHash": "sanitized-content-hash",
  "pageType": "QA",
  "title": "JWT 인증 필터 오류 해결",
  "passages": [
    {
      "id": "passage-001",
      "kind": "CODE",
      "text": "인증 필터 순서를 확인하는 공개 예제",
      "order": 1
    }
  ],
  "extractionMethod": "DOM",
  "extractionStatus": "SUCCESS",
  "extractorVersion": "proposal-1"
}
```

### 6.1 문단 규칙

- `id`는 현재 요청 안에서 고유하고 응답 근거 검증에 사용한다.
- `kind`는 제목·본문·코드·표·자막·OCR 등 제한 enum으로 관리한다.
- 문장·코드·표의 순서와 부정 표현을 임의로 고치지 않는다.
- 목표 키워드가 있는 부분만 남기지 않고 대표 문단을 일부 포함한다.
- 제목과 Domain은 개인정보 검사 후 필요한 최소 형태만 사용한다.
- 전체 URL, 쿼리, Fragment, 폼 입력값과 `contenteditable` 값은 제외한다.
- 문단 수·개별 길이·전체 요청 크기는 평가 후 설정으로 확정한다.

## 7. 개인정보와 민감정보 검사

### 7.1 수집 단계 제외

- `input`, `textarea`, 선택된 폼 값과 비밀번호 필드
- 인증 코드, 결제 카드, 계정 복구와 비밀 설정 영역
- 개인 메시지와 사용자 지정 제외 영역
- 화면 밖 숨김 요소, Script·Style·ARIA 비가시 데이터
- Authorization Header, Cookie, Local Storage와 Session Storage

### 7.2 문자열 검사

이메일·전화번호·주소·JWT·Bearer Token·알려진 API Key Prefix·결제 카드 후보를 제거하거나 전송을 중지한다. 마스킹만으로 안전하다고 단정하지 않는다. 안전하게 분리할 수 없으면 `PRIVACY_BLOCKED`로 종료하고 발견한 문자열은 로그에 남기지 않는다.

## 8. Apple Vision OCR 계약

### 8.1 호출 구조

```text
Content Script
  → Service Worker
  → captureVisibleTab
  → Native Messaging
  → macOS OCR 프로그램
  → OCR 결과
  → Service Worker의 개인정보·품질 검사
```

Content Script는 네이티브 호스트를 직접 호출하지 않는다. 네이티브 호스트는 허용된 Extension ID, 메시지 타입, 이미지 크기와 요청 버전을 검사하고 파일 경로나 시스템 명령을 입력받지 않는다.

### 8.2 요청·응답 제안

```json
{
  "type": "RECOGNIZE_VISIBLE_TAB",
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "navigationId": "navigation-003",
  "imageFormat": "PNG",
  "imageData": "<JSON-compatible encoded image placeholder>"
}
```

```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "navigationId": "navigation-003",
  "status": "SUCCESS",
  "languageHints": ["ko", "en"],
  "blocks": [
    {
      "id": "ocr-block-001",
      "text": "정제 전 OCR 결과",
      "recognitionConfidence": 0.91,
      "order": 1
    }
  ]
}
```

예시의 필드명과 인코딩은 제안이다. Chrome Native Messaging에서 사용할 JSON 호환 인코딩, 최대 크기와 Timeout은 PoC로 결정한다. `recognitionConfidence`는 OCR 품질이며 목표 관련성 Confidence가 아니다.

Chrome Native Messaging은 UTF-8 JSON 메시지를 길이 Prefix와 함께 전달한다. Chrome 공식 제한은 Extension에서 Native Host로 보내는 한 메시지가 최대 64 MiB, Native Host에서 Chrome으로 보내는 한 메시지가 최대 1 MiB다. FocusOn은 이 상한을 허용 크기로 사용하지 않고, 캡처 해상도·메모리·지연 평가를 거쳐 더 작은 제품 상한을 정한다.

### 8.3 이미지 수명

- 캡처 직전과 OCR 응답 직후 `tabId`, `windowId`, `navigationId`를 비교한다.
- 캡처 이미지는 사용자 기기 메모리에서만 처리한다.
- 파일, 로그, Crash Dump, Trace와 Agent 체크포인트에 저장하지 않는다.
- 요청 취소·탭 이동·Timeout·오류 시 참조를 즉시 해제한다.
- Windows·Linux 또는 네이티브 프로그램 미설치 환경은 DOM 경로를 유지하고 OCR을 `UNSUPPORTED`로 표시한다.
- 외부 OCR 서비스로 자동 우회하지 않는다.

## 9. ColPali 실험 경로

ColPali는 PDF·표·수식·슬라이드처럼 시각적 배치가 의미를 갖는 콘텐츠에서 관련 페이지나 영역을 찾는 비교 실험이다.

```text
허용된 시각 콘텐츠
  → 개인정보 Gate
  → DOM + Apple Vision OCR 기준선
  → ColPali 후보 검색
  → 근거 영역 검증
  → 동일 평가 세트 비교
```

- 일반 DOM 페이지의 기본 경로로 사용하지 않는다.
- OCR 텍스트를 무조건 대체하지 않는다.
- 원본 이미지를 Server나 외부 서비스로 보내려면 현재 개인정보 결정을 먼저 변경해야 한다.
- 로컬 실행에서도 모델 다운로드, 메모리, GPU, 배터리와 P95 지연을 측정한다.
- 시각 임베딩은 원본 복원이 불가능하다고 가정하지 않는다.
- 결과만으로 `OFF_TASK`, `DRIFT_RISK`, 알림 또는 차단을 확정하지 않는다.
- 실패하면 DOM/OCR 경로가 계속 동작해야 한다.

편입 기준은 `evaluation_spec.md`가 담당하며 이 문서는 입력과 실행 경계만 정의한다.

## 10. 품질 판정

| 상태 | 조건 | 허용 처리 |
| --- | --- | --- |
| `SUCCESS` | 유형별 핵심 정보와 검증 가능한 근거 확보 | 관련성 분석 |
| `PARTIAL` | 제목·메타데이터 등 일부 근거만 확보 | 제한적 분석 또는 확인 요청 |
| `FAILED` | 빈 결과, 접근 실패, Timeout | `UNAVAILABLE`, 무알림 |
| `EXCLUDED` | 정책상 수집 금지 | 캡처·전송·AI 미호출 |
| `UNSUPPORTED` | 안전한 추출 경로가 없음 | 미지원 표시, 외부 우회 금지 |

문자 수 하나로 품질을 정하지 않는다. 유형별 필수 영역, 메뉴 비율, 중복률, 언어, OCR 품질, 코드·표 순서 보존과 대표 문단 존재를 함께 평가한다.

## 11. 취소와 오래된 결과

다음 이벤트는 진행 중인 추출·OCR·AI 요청을 취소하거나 결과 적용을 금지한다.

- 탭·창·탐색 변경
- 목표 버전 변경
- 세션 일시정지·종료
- 제외 설정 변경
- 사용자 피드백으로 현재 판정 변경
- 개인정보 Gate 실패

취소가 실제 연산을 즉시 멈추지 못하더라도 결과 적용 전 버전을 다시 확인한다. 늦은 결과는 캐시에 넣기 전에도 유효 범위를 검사한다.

## 12. 오류와 Fallback

| 오류 | 가능한 Fallback | 금지 동작 |
| --- | --- | --- |
| DOM 접근 거부 | 권한 안내, 허용 시 로컬 OCR | 무조건 캡처 |
| iframe 접근 거부 | 상위 문서의 허용된 정보로 `PARTIAL` | Cross-origin 우회 |
| OCR 권한 없음 | DOM 전용 유지 | 외부 OCR 자동 전송 |
| 네이티브 호스트 없음 | 설치 안내, `UNSUPPORTED` | 반복 실행·무한 재시도 |
| OCR Timeout | 제한된 1회 재시도 검토 | 이전 페이지 판정 재사용 |
| 개인정보 잔존 | 요청 중지 | 마스킹 실패 텍스트 전송 |
| 모델 실패 | 추출 결과는 상태로 유지 | 실패를 `OFF_TASK`로 변환 |

## 13. 관측 항목

원문 없이 다음을 측정한다.

- 콘텐츠 유형과 추출 방법별 성공·부분·실패 비율
- DOM 추출, 캡처, OCR, 정제와 전체 P50·P95
- OCR Fallback 호출률과 미지원 비율
- navigation 불일치 폐기 수
- 개인정보 차단 수와 단계
- 문단 수·정제 후 바이트 구간
- ColPali 실험의 호출률·지연·자원 사용량

## 14. 검증 시나리오

- 제외 페이지에서는 Content Script 추출·캡처·OCR·AI 호출이 모두 발생하지 않는다.
- DOM 성공 시 OCR을 호출하지 않는다.
- DOM 부족과 권한 허용 시에만 현재 탭 OCR을 호출한다.
- 탭 전환 중 완성된 캡처와 OCR 결과를 폐기한다.
- 폼·메신저·인증 영역의 값이 정제 문단과 로그에 남지 않는다.
- 같은 콘텐츠의 작은 DOM 변화가 모델 호출 폭증을 만들지 않는다.
- 체류 재평가는 임베딩을 다시 만들지 않고 흐름만 갱신한다.
- 네이티브 호스트 미설치에서도 DOM 분석이 계속 동작한다.
- ColPali 실패가 기본 경로와 현재 세션을 중단시키지 않는다.

## 15. 팀 합의 필요

- Chrome 권한 요청 방식과 `activeTab` 사용 범위
- 지원할 URL Scheme·페이지 유형·macOS 최소 버전
- 콘텐츠 유형별 필수 영역과 정제 크기 상한
- DOM 안정화·Debounce·Timeout 설정
- Native Messaging 설치·업데이트·서명 담당
- 이미지 전송 형식과 최대 크기
- ColPali 실행 위치와 목표 하드웨어 자원 예산

## 16. 기술 근거

- Chrome의 [`captureVisibleTab`](https://developer.chrome.com/docs/extensions/reference/api/tabs#method-captureVisibleTab)은 지정 창의 현재 활성 탭에서 보이는 영역을 캡처하며 `activeTab` 또는 `<all_urls>` 권한이 필요하다. Chrome이 공개한 호출 상한이 있더라도 FocusOn은 이벤트 중복 제거와 자체 호출 제한을 둔다.
- [Chrome Native Messaging](https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging)은 JSON 기반 통신, Native Host Manifest의 `allowed_origins`, 메시지 방향별 크기 제한을 정의한다. 네이티브 프로그램은 호출 Extension Origin과 메시지 Schema를 함께 검증한다.
- Apple의 [Vision 텍스트 인식 문서](https://developer.apple.com/documentation/vision/recognizing-text-in-images)는 이미지에서 텍스트를 찾는 `VNRecognizeTextRequest` 계열과 기기 내 처리를 설명한다. 실제 API와 지원 OS는 목표 macOS 버전으로 PoC한다.
- [ColPali 원 논문](https://arxiv.org/abs/2407.01449)은 문서 페이지 이미지를 다중 벡터로 표현하는 시각 문서 검색 방법을 제안한다. 따라서 FocusOn에서는 일반 브라우저 분류기보다 시각 구조 문서의 페이지·영역 검색 후보로 평가한다.

## 관련 문서

- [AI 분석 규칙](analysis_rules.md)
- [AI 상태 모델](state_model.md)
- [AI API 계약](api_spec.md)
- [데이터 수명과 개인정보 경계](data_lifecycle.md)
- [AI 평가와 회귀 검증 명세](evaluation_spec.md)
