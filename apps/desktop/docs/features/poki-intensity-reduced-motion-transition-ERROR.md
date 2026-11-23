# 동작 줄이기에서 포키 이동 전환이 남음

- 기능: 강도별 포키 딴짓 알림
- 관련 이슈: [#282](https://github.com/What-a-move/FocusOn/issues/282)
- 발생 일시: 2026-11-19
- 환경: macOS 26.6.1, Electron 44.1.0
- 상태: 해결

## 재현과 실제 결과

Electron 검사 도구에서 `prefers-reduced-motion: reduce`를 적용한 뒤 보통 2차 화면을 열었다. 전환 시간이 0초여야 하는 자동 검증에서 `1e-05s`가 나왔다.

## 원인 및 해결

- 추정 원인: Electron 검사 도구가 동작 줄이기 설정을 전달하지 않았다고 봤다.
- 실제 원인: 설정은 전달됐다. `app/globals.css`의 전체 요소 규칙이 `!important`로 전환 시간을 0.01ms로 지정했다.
- 해결: `features/floating/floating.css`에서 포키 이동 요소에 한해 동작 줄이기 상태의 전환을 끈다. 적극 알림의 5초 확인 잠금과 약하게 알림의 읽는 시간은 유지한다.
