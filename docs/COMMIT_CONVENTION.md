# Commit 규칙

## 형식

```text
type: 작업 내용
```

Commit 메시지는 한국어로 작성하고, 한 Commit에는 하나의 작업 단위만 포함한다.

## Type

| Type | 의미 | 예시 |
| --- | --- | --- |
| `feat` | 기능 추가 | `feat: 학습 세션 타이머 추가` |
| `fix` | 버그 수정 | `fix: 탭 변경 이벤트 중복 기록 수정` |
| `style` | UI·CSS 수정 | `style: 집중 상태 카드 스타일 수정` |
| `refactor` | 코드 구조 개선 | `refactor: 분석 상태 계산 로직 분리` |
| `docs` | 문서 수정 | `docs: 브랜치 전략 문서 작성` |
| `test` | 테스트 추가·수정 | `test: 세션 종료 테스트 추가` |
| `chore` | 설정·패키지·기타 | `chore: 모노레포 스크립트 정리` |

## 좋은 Commit

```text
feat: Chrome 탭 정보 수집 연결
fix: 제외 도메인에서 분석 중지 안 되는 문제 수정
docs: 작업 워크플로우 작성
```

## 피해야 할 Commit

```text
update
작업함
수정
final
```

