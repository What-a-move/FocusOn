# 할 일 추가 경로의 로거 이벤트 타입 오류

- 발견: 2026-11-24, Desktop Electron TypeScript 검사
- 재현: `pnpm --filter @focuson/desktop exec tsc -p tsconfig.electron.json`
- 증상: `request.fail`이 공용 `LogEvent`에 없어 컴파일 실패
- 해결: 공용 이벤트 `api.request.fail`을 사용하고 화면 원문이나 예외 전문은 기록하지 않음
