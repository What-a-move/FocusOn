# Frontend 의존성 관리 규칙

## 목적

Dependency는 프로젝트가 빌려 쓰는 외부 코드다. 필요한 기능을 빠르게 만들 수 있지만, 많이 추가하면 Bundle·보안·업데이트 관리 비용이 커진다.

## 추가 전 확인

- 현재 코드와 설치된 도구로 해결할 수 있는지 확인한다.
- 실제 기능 범위에 필요한지 확인한다.
- 최근 유지보수 여부와 공식 문서를 확인한다.
- License와 알려진 보안 문제를 확인한다.
- Desktop·Extension의 실행 환경을 지원하는지 확인한다.
- Bundle 크기와 대체 도구를 비교한다.
- 새 라이브러리를 사용하는 이유를 Issue 또는 PLAN에 기록한다.

학습만을 목적으로 같은 역할의 라이브러리를 Production 코드에 동시에 추가하지 않는다.

## 설치 위치

| 사용 범위 | 설치 위치 |
| --- | --- |
| Desktop에서만 사용 | `apps/desktop/package.json` |
| Extension에서만 사용 | `apps/extension/package.json` |
| 두 앱이 각각 Runtime에서 사용 | 각 앱의 `package.json` |
| Monorepo 작업 도구 | Root `package.json` |
| 공통 Type Package 전용 | `packages/shared-types/package.json` |

Dependency를 공유한다는 이유만으로 모든 패키지를 Root에 설치하지 않는다.

## 설치 규칙

- Package Manager는 Root `packageManager`에 지정된 pnpm을 사용한다.
- 사용자가 도구 선택을 확인한 뒤 설치한다.
- 정확한 Version은 Lockfile에 기록한다.
- 설치 후 Type Check, Lint, 영향받는 앱의 Build를 확인한다.
- Package 추가와 기능 구현 범위가 크면 Commit을 분리한다.

## 현재 방향

아래 상태 관리·API 도구는 기술 방향만 확정했으며 아직 설치하지 않았다. 전체 문서 검토가 끝난 뒤 각 앱의 실제 사용 범위를 확인하고 별도 작업으로 설치한다.

- 클라이언트 공유 상태: Zustand — 확정·미설치
- Server 상태: TanStack Query — 확정·미설치
- HTTP Client: Axios — 확정·미설치
- Redux: 현재 Production 코드에는 추가하지 않으며 필요성이 생기면 Redux Toolkit으로 재검토
- 테스트·폼 검증·Mock·Logger 도구: 사용자와 비교 후 결정

## 제거와 업데이트

- 사용하지 않는 Package는 Import가 없는지 확인한 뒤 제거한다.
- Major Version 업데이트는 변경점과 Migration 문서를 먼저 확인한다.
- 보안 업데이트는 영향 범위와 회귀 테스트를 기록한다.
- Lockfile만 임의로 삭제하거나 다시 생성하지 않는다.
