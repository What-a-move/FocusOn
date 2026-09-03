# Backend 기능 기획서

> Backend 기능 개발 전에 작성하고, API·DB·보안 범위를 확인한 뒤 개발을 시작한다.
> 오류 보고서와 결과 리포트 파일도 작업 시작 시 함께 만든다.

## 기본 정보

- 기능명:
- 기능 ID:
- 작성자:
- 작성일:
- 우선순위: MVP - 핵심 / MVP - 기본 / 2차 / 검토 필요
- 관련 Issue:
- 예정 Branch:
- 관련 Client·AI 문서:

## 기능 목적

- 해결할 문제:
- Backend가 담당할 역할:

## API 정의

- Endpoint:
- HTTP Method:
- 인증 필요 여부:
- 요청 권한:

### Request

```json
{}
```

### Success Response

```json
{}
```

### Failure Response

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "오류 안내 문구"
  }
}
```

## 데이터 설계

- 관련 Entity:
- 저장할 필드:
- 필수·선택 값:
- 관계와 제약 조건:
- 저장하지 않을 민감 데이터:

## 처리 흐름

1.
2.
3.

```text
Controller → Service → Repository → Database
```

## 예외 처리

| 상황 | HTTP 상태 | 오류 코드 | 처리 방법 |
| --- | --- | --- | --- |
| 인증 실패 |  |  |  |
| 입력값 오류 |  |  |  |
| 데이터 없음 |  |  |  |

## 연동 범위

- Desktop:
- Chrome Extension:
- AI:
- 영향받는 API·Entity:

## 완료 조건

- [ ] API 계약을 공유했다.
- [ ] 정상·실패 응답을 구현했다.
- [ ] 데이터 저장·조회가 동작한다.
- [ ] 테스트를 작성했다.
- [ ] `*-ERROR.md`를 갱신했다.
- [ ] `*-REPORT.md`를 작성했다.
- [ ] `CONTEXT.md`, `NEXT_TASK.md`를 갱신했다.

