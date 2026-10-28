# 학습 목표 저장·조회 오류 및 제한 기록

- 관련 Issue: [#87](https://github.com/What-a-move/FocusOn/issues/87)
- Branch: `feat/87-goal-create-read`

## 미해결 사항

### AI 명확성 확인 이력 검증 불가

- 현상: AI 확정 또는 `clarificationSessionId`가 포함된 목표 생성 요청은 현재 `503 SERVICE_UNAVAILABLE`을 반환한다. 수동 확정에 ID를 함께 보낸 경우도 해당한다.
- 원인: 명확성 확인 기능이 없어 해당 ID의 소유권과 유효성을 확인할 데이터와 로직이 없다.
- 현재 처리: 검증하지 않은 ID나 AI 확인 이력을 신뢰해 저장하지 않는다. 이는 임시 제한이며 정상 저장 계약의 완성을 의미하지 않는다.
- 후속: 명확성 확인 기능과 상세 계약을 확인한 뒤 검증·연동하고 정상 및 오류 경로를 테스트한다.

### 기존 전체 테스트 실패

- 현상: 전체 테스트 실행에서 기존 `UserConfigurationApiTests.concurrentFirstWritesAndNicknameConflicts`가 실패했다. 목표 API 테스트는 별도로 통과했다.
- 확인 필요: H2 테스트 스키마와 PostgreSQL 닉네임 유일 제약의 차이가 원인인지 추가 확인한다. 현재 원인을 확정하지 않았다.
- 후속: 기존 사용자 설정 범위와 분리해 원인을 확인한 뒤 전체 테스트를 다시 실행한다.

## 수동 테스트 기록 보완

- 사용자가 Swagger에서 두 API를 테스트하고 경로·메서드의 Notion 일치 및 `goals` 테이블 생성을 확인했다고 알렸다. 구체적인 요청·응답과 테스트 환경은 전달받지 않아 추가 기록이 필요하다.
