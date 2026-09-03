# Backend 작업 컨텍스트

> Backend 담당자가 작업 시작 시 가장 먼저 읽는 문서다.
> 백엔드 구조가 정해지거나 변경되면 이 문서를 갱신한다.

## 담당 영역

- Spring Boot 백엔드
- REST API
- 사용자·목표·학습 세션 데이터
- 분석 이벤트와 리포트 데이터
- Desktop·Extension·AI 연동

## 현재 상태

### 완료된 작업

- Spring Boot 프로젝트 기본 구조가 `server/`에 생성되어 있다.
- Backend 담당 영역의 문서 구조를 생성했다.
- 공통 API 응답 형식은 루트 `docs/API_CONTRACT.md`에서 초안으로 정의했다.

### 진행 중인 작업

- 데이터베이스와 Entity 구조 설계
- 클라이언트와 AI 서버의 API 계약 확정

### 아직 진행하지 않은 작업

- 인증 및 사용자 API
- 학습 목표·세션 API
- 앱·페이지 이벤트 저장 API
- 분석 결과 저장 API
- 리포트 조회 API
- 배포 환경 구성

## 현재 기술

- 프레임워크: Spring Boot
- 언어: Java
- API: REST
- 빌드: Gradle
- 데이터베이스: 검토 필요

## 예상 데이터 흐름

```text
Desktop / Extension
  → Spring Boot API
  → 검증·인증
  → Service
  → Repository / Database
  → 응답
```

## 반드시 지켜야 하는 조건

- Controller에 비즈니스 로직을 작성하지 않는다.
- 요청 DTO와 응답 DTO를 분리한다.
- 사용자 식별자와 세션 식별자를 검증한다.
- 비밀번호·토큰·API Key를 로그에 남기지 않는다.
- 원본 화면·카메라 영상은 기본 저장하지 않는다.
- API 변경 시 Desktop·Extension·AI 담당자에게 공유한다.

## 참고 문서

- 루트: `docs/API_CONTRACT.md`
- 루트: `docs/DATA_PRIVACY.md`
- 루트: `docs/ARCHITECTURE.md`
- 루트: `docs/WORKFLOW.md`
