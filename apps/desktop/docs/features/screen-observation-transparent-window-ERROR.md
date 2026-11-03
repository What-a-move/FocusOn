# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: screen-observation-002
- 오류 식별명: transparent-window
- 기능명: 화면 관측 기반과 분석 제외 앱 (화면 분석 1단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 보통 (Electron 앱에서 창 제목·OCR 글자가 비어 판단 근거가 사라짐)
- 상태: 해결
- 관련 Issue·PR: #140 (발견: #54 실제 기기 QA)

## 오류 제목

- VS Code·Claude 같은 Electron 앱이 맨 앞일 때 창 제목이 null, OCR 글자가 빈 값으로 나옴

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44.1.0
- Node.js·pnpm: 도우미 단독 실행으로 재현
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. VS Code를 맨 앞에 둔다.
2. 도우미에 `frontmost`를 보내고, 받은 `windowId`로 `ocr`을 보낸다.

## 기대 결과

- 보이는 편집기 창의 제목과 화면 글자가 나온다.

## 실제 결과

- `windowTitle: null`, OCR `text: ""`. 텍스트 편집기처럼 창이 하나인 앱은 정상이었다.

## 오류 메시지

```text
VS Code 창 목록 (CGWindowListCopyWindowInfo, 맨 앞부터)
layer 0  alpha 0  1512×44   title 없음   ← 도우미가 고른 창
layer 0  alpha 1  1512×949  title 16자   ← 실제 편집기 창
```

## 원인 및 해결

- 추정 원인: 화면 기록 권한이 없어 제목을 못 읽는다.
- 실제 원인: 추정과 달랐다(권한은 허용 상태). Electron 앱은 실제 창 앞에 투명(alpha 0)하고 납작한 보조 창을 같은 layer 0에 둔다. 도우미가 "그 앱의 첫 layer 0 창"을 맨 앞 창으로 골라 이 보조 창의 빈 제목과 빈 화면을 읽었다.
- 해결 방법: 맨 앞 창을 고를 때 `kCGWindowAlpha > 0`이고 너비·높이가 100pt 이상인 창만 본다.
- 변경 파일: `native/observer/main.swift`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (Claude 창 제목 확인, 한글·영어 OCR 글자 정상)
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (텍스트 편집기 창 제목·OCR 그대로, `pnpm test:electron`)

## 재발 방지 및 남은 작업

- 새 앱 유형에서 제목이 비어 나오면 권한보다 창 목록(alpha·크기·layer)을 먼저 확인한다.
