# Node 22.14에서 TypeScript 테스트 입력 인식 실패

- 발견: 2026-11-24, Desktop 전체 Node 테스트
- 증상: `.ts`를 직접 읽는 기존 테스트 두 곳에서 확장자 또는 구문 오류.
- 재현: Desktop 디렉터리에서 `node --test tests/electron/*.test.cjs`.
- 해결: 이 환경에서 `node --experimental-strip-types --test tests/electron/*.test.cjs`로 실행했다.
