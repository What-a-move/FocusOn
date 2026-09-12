# Backend 코드 스타일 규칙 (Google Java Style)

> 2026-09-12부로 `server/build.gradle`에 `checkstyle` 플러그인을 새로 추가했다. 이전에는 이 프로젝트에 정적 분석 도구가 없었다.

## Checkstyle 설정

- `server/build.gradle`의 `checkstyle` 플러그인이 Checkstyle 배포판에 내장된 `google_checks.xml`(Google Java Style)을 그대로 사용한다.
- `maxWarnings = 0`으로 설정되어 있다. 경고가 하나라도 있으면 `checkstyleMain`/`checkstyleTest` 태스크가 실패한다.
- 커밋 전 반드시 다음을 실행해 통과를 확인한다.
  ```bash
  ./server/gradlew -p server checkstyleMain
  ./server/gradlew -p server checkstyleTest   # 테스트 코드를 변경했을 때
  ```
- ⚠️ **확인 필요**: 이 실행 결과는 아직 검증하지 못했다 — 이 개발 환경(device_bash 샌드박스)에서 Gradle 배포판(`gradle-9.7.1-bin.zip`) 다운로드에 필요한 외부 네트워크 접근이 막혀 있어 `./gradlew checkstyleMain`을 끝까지 실행해보지 못했다. 실제 개발 환경에서 처음 실행할 때 아래를 함께 확인한다.
  - Checkstyle이 Gradle 9.7.1 + Java 25 조합에서 정상 동작하는지.
  - `google_checks.xml` 규칙이 이 프로젝트의 Lombok 사용 패턴(`@Getter`, `@Builder` 등 생성 코드)과 충돌해 불필요한 경고를 내지 않는지 — 충돌하면 `server/build.gradle`의 `checkstyle { }` 블록에 예외(suppression) 설정을 추가하고 이 문서를 갱신한다.
  - 특정 Checkstyle 버전을 고정해야 하면(`toolVersion`) 그때 실제 최신 안정 버전을 확인해서 지정한다 — 현재는 버전을 고정하지 않고 플러그인 기본값을 쓴다.

## 주요 규칙 (Google Java Style 기준)

- 들여쓰기 2칸, 탭 사용 금지.
- 한 줄 100자 제한.
- import는 static import와 일반 import를 각각 알파벳 순으로 정렬한다(와일드카드 import 금지).
- 중괄호는 K&R 스타일(Egyptian brackets), 한 줄짜리 `if`라도 생략하지 않는다.

## 주석 / Javadoc

- Javadoc은 공개 API(Controller/Service의 public 메서드, 클래스)에만 작성한다.
- 코드만 읽어도 알 수 있는 내용은 주석으로 남기지 않는다(WHY만, WHAT은 지양).
- 예: `// count를 1 증가시킨다` (X) → `// 동시 요청 시 락 경합을 줄이기 위해 원자적 증가 연산을 사용한다` (O)
- TODO/FIXME에는 담당자 또는 조건을 명시한다: `// TODO(담당자): DB 스키마 확정 후 인덱스 추가`

## 패키지 구조

- 현재 `server/src/main/java/com/capstone/backend/`에는 `BackendApplication.java` 하나만 있다. 도메인 코드가 추가되면 `controller`/`dto`/`service`/`exception`/`entity`/`repository` 레이어 구조를 기본으로 따른다(`server/docs/DEVELOPMENT_RULES.md` BE-003).
- 이 목록에 없는 폴더가 필요하면(레이어 중 어디에도 자연스럽게 안 맞는 경우), 기획서나 PR에서 "왜 새 폴더가 필요한지" 명시해 검토받고 추가한다.

## 관련 문서

- `server/docs/DEVELOPMENT_RULES.md`
- `server/docs/rules/test-convention.md`
