# Extension OCR 완료와 정리 순서 오류

## 오류 기본 정보

- 오류 식별명: screen-acquisition-cleanup
- 발생일: 2026-10-19
- 담당 영역: Extension, 관련 #11 연동 작업
- 환경: Node 22.14.0·Chrome API 대역, 실제 Chrome 권한·Desktop 연결은 미사용
- 심각도: 보통, 상태: 해결

## 재현과 실제 결과

합성 OCR 성공 경로에서 `try/finally` 안의 Promise를 await 없이 반환하면 관측 최종 검증이 끝나기 전에 offscreen 정리가 시작됐다. 기대하는 작업 완료→정리→호출자 반환 순서와 달라 테스트가 실패했다.

## 원인 및 해결

- OCR 관측 생성은 `return await`로 기다린 후 finally에서 offscreen을 닫는다.
- 정책·화면 변경의 오류 경로에서는 추가 수집 취소도 수행하므로 정리가 마지막 Chrome API 호출이라고 가정하지 않고 OCR context가 닫혔는지를 검사한다.
- 변경: `public/capture/controller.js`, `tests/screen-acquisition.test.mjs`.
- 확인: 정상 OCR, 탭 변경, 진행 중 정책 철회 시 원문이 반환되지 않고 offscreen이 닫히는 합성 검사 통과. 실제 Chrome 검사 결과로 확대 해석하지 않는다.
