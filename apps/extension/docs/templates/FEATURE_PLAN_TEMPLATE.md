# Extension 기능 기획서

> Chrome Extension 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> 기획서와 함께 오류 보고서·결과 리포트 파일도 미리 생성한다.

## 기본 정보

- 기능명:
- 기능 ID:
- 작성자:
- 작성일:
- 우선순위: MVP - 핵심 / MVP - 기본 / 2차 / 검토 필요
- 관련 Issue:
- 예정 Branch:
- 관련 Notion·Figma:

## 기능 목적

- 사용자의 문제:
- Extension에서 제공할 가치:

## 사용자 흐름

1.
2.
3.

## 수집 범위

- 수집할 값: 탭 / URL / 도메인 / 페이지 제목 / 본문 텍스트 / 기타
- 수집하지 않을 값:
- 수집 시작 조건:
- 수집 중지 조건:
- 분석 제외 도메인 처리:

## Extension 상세 동작

- Popup 동작:
- Content Script 동작:
- Service Worker 동작:
- 페이지 이동 처리:
- 15초 체류 재분석 처리:
- 상태 변화: `IDLE → TRACKING → ANALYZING → EXCLUDED`

## Chrome 권한

- `permissions`:
- `host_permissions`:
- 권한이 필요한 이유:
- 권한 거부 시:

## Desktop·Server·AI 연결

- 전달 대상:
- 전달 시점:
- 전달 데이터:
- 연결 실패 시:
- 재전송 필요 여부:

```json
{
  "tabId": "tab-id",
  "domain": "example.com",
  "title": "page title"
}
```

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Extension 처리 |
| --- | --- | --- |
| 권한 거부 |  |  |
| 제외 도메인 |  |  |
| Desktop 연결 실패 |  |  |
| 페이지 접근 불가 |  |  |

## 보안·개인정보

- 민감 정보 차단 방법:
- 원본 본문 저장 여부:
- Server·AI 전송 데이터:
- 로그에서 제거할 값:

## 완료 조건

- [ ] 기획 내용 확인
- [ ] 필요한 권한만 선언
- [ ] 정상 흐름 구현
- [ ] 제외 도메인 구현
- [ ] 연결 실패 처리
- [ ] 테스트 완료
- [ ] `*-ERROR.md` 갱신
- [ ] `*-REPORT.md` 작성
- [ ] `CONTEXT.md`, `NEXT_TASK.md` 갱신

