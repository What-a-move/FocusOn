# 첫 이탈 계산 공유 후 Next 빌드 오류

- 재현: 공통 계산을 `report-contract.cts`로 옮기고 Renderer에서 Electron의 `.cjs` 경로로 가져오면 Next 프로덕션 빌드가 모듈을 찾지 못한다.
- 원인: Electron 빌드는 `.cts`를 `.cjs`로 만들지만 Renderer 빌드는 미리 컴파일된 Electron 파일을 참조하지 않는다.
- 수정: 계산을 브라우저에서도 읽을 수 있는 순수 TypeScript 파일에 두었다. 공통 타입만 Electron 계약에서 가져온다.
- 검증: Electron 컴파일·리포트 테스트와 Next 프로덕션 빌드를 모두 실행한다.
