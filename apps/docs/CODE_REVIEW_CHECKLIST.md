# Frontend 코드 리뷰 체크리스트

## 작업 범위

- [ ] Issue 범위와, 새 기능이면 PLAN 문서의 범위만 변경했다.
- [ ] Desktop·Extension 중 영향받는 앱을 PR에 적었다.
- [ ] 범위가 커진 작업은 별도 Issue로 분리했다.

## 구조와 타입

- [ ] Page·Component·Hook·Service·Platform 책임이 섞이지 않았다.
- [ ] 새 파일이 `FOLDER_STRUCTURE.md` 기준 위치에 있다.
- [ ] `any`와 불필요한 타입 단언을 사용하지 않았다.
- [ ] 두 앱이 공유하는 타입의 중복 여부를 확인했다.
- [ ] 새 Dependency가 필요하며 올바른 Package에 설치되었는지 확인했다.
- [ ] Desktop 작업은 Electron·IPC·권한·Window 문서의 경계를 지켰다.
- [ ] Extension 작업은 구조·Message·권한·Service Worker 문서의 경계를 지켰다.

## 사용자 경험

- [ ] 로딩·성공·빈 상태·오류 상태를 확인했다.
- [ ] 권한 거부와 연결 끊김 상태를 확인했다.
- [ ] 사용자를 비난하거나 단정하는 문구가 없다.
- [ ] 기본 완료 조건으로 키보드 사용, 접근 가능한 이름, 색상 대비, Focus 표시를 확인했다.
- [ ] 공통 UI가 디자인 Token과 Component 규칙을 따르는지 확인했다.

## 보안과 개인정보

- [ ] 토큰·원본 화면·카메라·페이지 본문을 로그에 남기지 않았다.
- [ ] 제외 앱·도메인 상태에서 Page·Screen·OCR·AI·Camera 분석이 모두 중지된다.
- [ ] Chrome과 macOS 권한을 필요한 범위로 제한했다.
- [ ] Renderer 또는 페이지 코드에 시스템 API를 직접 노출하지 않았다.
- [ ] 인증·사용자별 Cache가 로그아웃과 계정 변경 시 정리되는지 확인했다.
- [ ] 개발용 Mock과 인증 우회가 Production에서 비활성화되는지 확인했다.

## 검증과 문서

- [ ] 관련 타입 검사·lint·build가 성공한다.
- [ ] 정상 흐름과 예외 흐름을 테스트했다.
- [ ] 새 기능을 완료했다면 `기능명-REPORT.md`를 작성했다.
- [ ] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md`를 작성했다.
- [ ] `CONTEXT.md`, `NEXT_TASK.md`를 현재 상태에 맞게 갱신했다.
- [ ] 새 결정이나 변경된 설계를 `DECISION_RECORD.md`에 기록했다.
- [ ] PR 대상이 `dev`이고 관련 Issue를 연결했다.
