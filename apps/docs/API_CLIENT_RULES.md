# Frontend API Client 규칙

> 기술 방향: HTTP Client는 Axios, Server 상태 관리는 TanStack Query로 확정했다. 두 Package는 전체 문서 검토가 끝난 뒤 별도 작업에서 설치한다.

## 호출 구조

```text
Component → TanStack Query Hook → Service → Axios Client → Server
```

위 흐름은 Server 상태를 조회·변경하는 일반적인 기준이다. 재사용되지 않는 단순 조회 화면이며 별도 사용자 입력·Cache 갱신·플랫폼 상태가 없다면 `FRONTEND_ARCHITECTURE.md`의 예외에 따라 `Page → Service → Axios Client`로 단순화할 수 있다.

컴포넌트는 URL, 인증 헤더, HTTP 상태 코드를 직접 관리하지 않는다.

## 도구 선택

- Axios는 HTTP 요청 전송, 공통 Base URL, Header, Timeout, 응답·오류 변환을 담당한다.
- TanStack Query는 Server 상태의 조회, Cache, 중복 요청 방지, 로딩·오류 상태, 갱신을 담당한다.
- 조회는 `useQuery`, 생성·수정·삭제는 `useMutation`을 기본으로 한다.
- TanStack Query의 `queryFn`과 `mutationFn`은 Axios를 사용하는 Service 함수를 호출한다.
- 두 라이브러리의 설치와 공통 설정은 전체 문서 검토가 끝난 뒤 별도 작업으로 진행한다.

## 요청 규칙

- API 기준은 루트 `docs/API_CONTRACT.md`를 따른다.
- Base URL과 환경값은 코드에 직접 작성하지 않는다.
- 운영 환경 요청에는 Axios Timeout을 설정한다.
- 요청과 응답 타입을 정의하고, 외부 응답은 필요한 경우 런타임 검증을 추가한다.
- 인증 토큰, 쿠키, 사용자 식별자를 로그에 출력하지 않는다.
- 사용자의 반복 클릭으로 같은 요청이 중복되지 않게 로딩 상태를 사용한다.
- 화면이 사라졌는데 필요 없는 요청은 취소할 수 있도록 설계한다.

## 응답 처리

| 상황 | 처리 |
| --- | --- |
| 성공 | 화면에서 사용할 데이터 모양으로 변환 |
| 잘못된 요청 | 사용자가 수정할 수 있는 안내 표시 |
| 인증 만료 | 로그인 복구 흐름으로 이동 |
| 권한 없음 | 기능을 숨기지 말고 접근 불가 이유 표시 |
| Server 오류 | 재시도 안내와 오류 기록 |
| 네트워크 끊김 | 연결 상태 표시, 안전한 요청만 재시도 |

## 재시도

- 조회 요청은 짧은 지연 후 제한적으로 재시도할 수 있다.
- 세션 시작·종료처럼 상태를 바꾸는 요청은 중복 실행 위험을 먼저 확인한다.
- 무한 재시도를 하지 않는다.
- 재시도 횟수와 최종 실패 상태를 사용자에게 숨기지 않는다.
- TanStack Query 기본 재시도 값을 그대로 믿지 않고 조회와 변경 요청별 정책을 명시한다.

API 계약이 확정되지 않았으면 임의 필드를 만들지 않고 `검토 필요`로 문서화한다.
