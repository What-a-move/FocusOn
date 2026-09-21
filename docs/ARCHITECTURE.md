# FocusOn 시스템 아키텍처

## 구성

```text
사용자
  ↓
  Google 로그인
  ↓
  Spring Boot Server (사용자·기기·학습 세션 기준)
       │
       ├──────── Desktop App (Electron + Next.js + React)
       │             ├─ 학습 세션 시작·일시정지·재개·종료
       │             ├─ macOS 활성 앱 확인
       │             ├─ 화면 캡처 및 학습 분석 연결
       │             ├─ MediaPipe 카메라 분석 결과 수신
       │             └─ 화면 부유형 두더지·알림·리포트 표시
       │
       └──────── Chrome Extension (Next.js + React)
                     ├─ Desktop 세션 조회
                     ├─ 타이머 표시·일시정지·재개·종료 요청
                     ├─ 현재 탭·페이지 제목 확인
                     ├─ 페이지 이동 흐름 기록
                     └─ 콘텐츠 분석용 데이터 전달
       │
       └──────── AI Service
                     ├─ 화면·페이지 관련성 판단
                     ├─ 집중 상태 보조 판단
                     └─ 분석 결과 반환

공통 데이터 계약
  └─ packages/shared-types
      ├─ 페이지 정보·분석 결과 타입
      └─ 학습 세션·집중 상태 타입
```

## 영역별 책임

### Desktop

- 학습 세션 시작·중지·종료
- 타이머와 휴식 알림
- 화면 부유형 두더지 마스코트와 응원·복귀 피드백
- Google 로그인 및 Extension 연결 UI
- macOS 활성 앱 확인
- 분석 제외 앱 처리
- 화면 캡처 권한과 캡처 생명주기 관리
- 카메라 권한 요청 및 MediaPipe 결과 표시
- 서버 세션 상태 변경 시 부유형 창과 화면 상태 갱신

### Chrome Extension

- 활성 학습 세션 중 현재 활성 탭, URL, 페이지 제목 자동 추적
- `tabs`와 `webNavigation`을 이용한 페이지 이동 이벤트 수집
- 일정 시간 머문 페이지의 재분석 요청
- 분석 제외 도메인에서는 Page·Screen·OCR·AI·Camera 분석 중지
- Desktop 세션 조회와 타이머 표시
- 일시정지·재개·종료 명령을 Server에 요청
- Desktop 연결·인증 상태 안내

### Spring Boot Server

- 사용자, 목표, 설정, 세션 데이터 관리
- Desktop·Extension·AI 사이의 인증 및 API 제공
- Google 계정 검증과 FocusOn 사용자 식별
- Desktop·Extension 기기 연결과 세션 동기화
- 이벤트 저장과 리포트 조회
- 개인정보 및 보존 기간 정책의 서버 측 적용

### AI Service

- 화면 또는 페이지에서 전달된 텍스트·메타데이터 분석
- 사용자의 학습 목표와 현재 활동의 관련성 판단
- 판단 결과와 신뢰도 반환
- 원본 화면이나 카메라 영상을 기본 저장하지 않음

### `packages/shared-types`

- Desktop·Extension·Server 사이에서 공유하는 TypeScript 데이터 계약을 관리한다.
- 페이지 정보, 분석 결과, 학습 세션 상태처럼 여러 영역이 함께 사용하는 구조만 둔다.
- API 호출이나 런타임 검증을 대신하지 않으므로 Server DTO 검증은 별도로 구현한다.

## 활동 관련성

| 상태 | 의미 |
| --- | --- |
| `RELATED` | 목표와 관련된 활동이 확인됨 |
| `UNRELATED` | 목표와 관련성이 낮은 활동이 확인됨 |
| `UNCERTAIN` | 판단할 정보가 부족함 |

관련성은 공개 API의 `relation` 필드로 표현한다. `PRIVACY_BLOCKED`, `EXCLUDED`, `UNCERTAIN`은 정상적인 `analysisStatus`이며 오류 코드가 아니다. 개인정보 차단·제외처럼 관련성 판정을 하지 않은 상태와 `RELATED`·`UNRELATED` 관계값을 한 enum으로 합치지 않는다.

## 분석 작동 상태 (`AnalysisActivityState`)

| 상태 | 의미 |
| --- | --- |
| `RUNNING` | 분석이 동작 중임 |
| `PAUSED` | 사용자가 타이머 또는 분석을 일시정지함 |
| `EXCLUDED` | 사용자가 지정한 제외 대상이라 분석하지 않음 |

`AnalysisActivityState`는 `FocusState`와 분리해 관리하고, 실제 공유 Type 추가는 세션 API 계약을 구현할 때 진행한다.

## 학습 세션 동기화 원칙

- Server가 학습 세션 상태와 기준 시간을 관리한다.
- Desktop에서 시작한 세션을 Extension이 조회해 타이머를 표시한다.
- Extension의 일시정지·재개·종료 요청은 Server가 검증한 뒤 상태를 변경한다.
- Desktop과 Extension은 상태 변경 응답 또는 동기화 이벤트를 받아 화면을 갱신한다.
- 매초 서버에 시간을 요청하지 않고 `targetDurationMs`와 Server가 확정한 `accumulatedActiveMs`를 이용해 클라이언트에서 남은 시간을 계산한다.

## 화면 부유형 두더지 피드백 원칙

- 학습 시작 시 Desktop 화면 위에 떠 있는 투명·테두리 없는 부유형 창에 두더지 마스코트를 표시한다.
- 부유형 창은 학습 화면을 가리지 않는 위치와 크기를 사용하고, 상태가 바뀔 때만 말풍선을 노출한다.
- 집중 상태에서는 팻말과 응원 문구를 보여준다.
- 집중 이탈 상태에서는 표정·자세·말풍선을 바꿔 학습 복귀를 안내한다.
- 같은 사이트 재방문이나 반복 이탈은 상황을 설명하는 문구를 사용할 수 있지만, 모욕·비난·과도한 압박 표현은 사용하지 않는다.
- 사용자는 팻말에 남은 시간을 표시할지, 매번 바뀌는 응원 문구를 표시할지 선택할 수 있다.
- macOS 상단 메뉴 막대 아이콘은 상태 확인·설정·부유형 창 표시 전환·앱 종료를 위한 보조 진입점으로 둔다.
