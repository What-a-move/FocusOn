# Frontend Logging 규칙

## 목적

Log는 프로그램이 어떤 일을 했는지 남기는 개발 기록이다. 문제를 찾는 데 필요한 정보만 남기고 사용자 개인정보와 원문 데이터는 기록하지 않는다.

## Log Level

| Level | 사용 기준 | 예시 |
| --- | --- | --- |
| `debug` | 개발 중 세부 흐름 확인 | Message 처리 단계 |
| `info` | 정상적인 주요 상태 변화 | 세션 시작·종료 |
| `warn` | 복구 가능하지만 확인이 필요한 상태 | 재연결, 권한 미허용 |
| `error` | 기능이 실패해 조치가 필요한 상태 | API 실패, IPC 예외 |

## 허용하는 정보

- 이벤트 이름과 결과 Code
- 성공·실패 여부
- 민감 정보를 제거한 Error 종류
- 본문 길이와 수집 여부
- 사용자 원문을 포함하지 않는 성능 시간
- 개인을 직접 식별하지 않는 요청 추적 ID

## 금지하는 정보

- Access Token, Refresh Token, Cookie, 연결 Code
- 비밀번호, 결제 정보, 개인 메시지
- 원본 Camera 영상과 Screen Image
- Page 본문 원문
- 전체 URL Query와 개인 식별값
- Server 응답 객체 전체

## 작성 규칙

- `console.log(response)`처럼 객체 전체를 출력하지 않는다.
- Error를 기록하기 전에 민감 정보를 제거한다.
- 같은 오류를 반복 Loop에서 계속 출력하지 않는다.
- 사용자가 보는 오류 문구와 개발자 Log를 분리한다.
- Log만 남기고 오류를 무시하지 않는다.

```ts
logger.warn('extension-connection-failed', {
  reasonCode: 'DESKTOP_OFFLINE',
  retryable: true,
})
```

위 코드는 형식 예시이며 Logger 도구가 확정되었다는 뜻이 아니다.

## 환경별 기준

- 개발 환경은 필요한 `debug` Log를 사용할 수 있다.
- Production에서는 `debug` Log를 기본 비활성화한다.
- Production Error Log에도 사용자 원문은 포함하지 않는다.
- Logging 도구와 원격 수집 서비스는 사용자와 보안 범위를 확인한 뒤 도입한다.
