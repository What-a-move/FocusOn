# Desktop native 입출력 API 오류

## 오류 기본 정보

- 오류 식별명: screen-acquisition-filehandle
- 발생일: 2026-10-19
- 담당 영역: Desktop, 관련 #11 연동 작업
- 심각도: 보통, 상태: 해결

## 재현 및 실제 결과

Objective-C helper 빌드에서 Swift 스타일 `NSFileHandle.standardInput/standardOutput`을 사용해 `property ... not found`가 발생했다. 화면 권한·캡처 데이터와 관계없는 compile 오류다.

## 기대 결과·원인·해결

- stdin/stdout JSON 프로토콜 빌드가 기대값이다.
- Objective-C의 `fileHandleWithStandardInput/fileHandleWithStandardOutput`으로 변경했다.
- 변경 파일: `electron/native/ScreenCapture.m`.
- 확인: native helper 빌드 통과. 원문 로그·이미지 저장은 추가하지 않았다.
