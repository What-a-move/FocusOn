# Desktop Electron 아키텍처

> 결정 상태: 구조와 Build·Loading·배포 방향은 확정했다. Electron Package와 electron-builder는 설치되어 있지만 Main·Preload 진입점과 Packaging 연결은 아직 구현하지 않았다.

## 목적

Electron의 Main·Preload·Renderer 책임을 분리해 React 화면에 macOS의 강한 권한을 직접 노출하지 않는다.

## Process 구조

```text
macOS
  ↕
Electron Main
  ↕ IPC
Preload / contextBridge
  ↕ 허용된 API
Next.js Renderer
```

| 영역 | 담당 | 금지 |
| --- | --- | --- |
| Main | 앱 생명주기, Window, Menu, 권한, 활성 앱, 화면 캡처 | React UI 상태 직접 관리 |
| Preload | 허용된 IPC 함수를 좁은 API로 노출 | `ipcRenderer` 전체 노출 |
| Renderer | React 화면, 사용자 입력, TanStack Query, UI 상태 | Node.js·File System·Electron API 직접 접근 |

## 권장 파일 구조

```text
apps/desktop/
├── app/                    Next.js Renderer
├── features/               Desktop 기능 UI와 Hook
├── electron/
│   ├── main/               앱·Window·macOS 기능
│   ├── preload/            contextBridge API
│   └── shared/             IPC Channel·Payload Type
└── docs/
```

Main·Preload는 현재 설치된 TypeScript Compiler와 전용 `tsconfig.electron.json`으로 `dist-electron/`에 Build한다. Renderer는 Next.js의 `output: 'export'` 결과인 `out/`을 사용한다. 구현 전에는 빈 폴더를 만들지 않는다.

## 실행 흐름

```text
app.whenReady()
  → Main Window 생성
  → 개발 환경은 http://127.0.0.1:3000 연결
  → Production은 app:// 전용 Protocol로 Package 내부 out/ 연결
  → Preload가 허용된 Desktop API 노출
  → Renderer가 준비 상태 확인
```

- 개발 환경 URL은 Loopback 주소로 제한하고 Production Build에서는 사용하지 않는다.
- Production의 `app://` Scheme은 `standard`와 `secure`만 활성화하고 `bypassCSP`를 사용하지 않는다.
- `app://` Scheme 권한은 `app.whenReady()` 전에 등록하고 실제 Handler는 앱 준비 후 연결한다.
- `app://` Handler는 Package의 `out/` 아래 파일만 반환하고 경로 이탈을 차단한다.
- Production에서 외부 Remote Code를 앱 코드처럼 실행하지 않는다.
- `app.requestSingleInstanceLock()`으로 하나의 Instance만 유지하며 두 번째 실행은 기존 Main Window를 앞으로 가져온다.
- macOS에서 모든 Window가 닫힌 것과 앱 종료를 같은 동작으로 단정하지 않는다.

## 보안 기본값

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`
- `webSecurity` 비활성화 금지
- 외부 Navigation과 새 Window 제한
- 신뢰되지 않은 URL을 `shell.openExternal`로 바로 열지 않음
- IPC Sender와 Payload 검증

## 데이터 경계

- 원본 Screen·Camera Data를 Renderer와 Server에 기본 저장하지 않는다.
- Main은 필요한 최소 결과만 Preload를 통해 전달한다.
- Renderer가 요청하지 않은 Event를 무제한 전송하지 않는다.
- Listener는 Window와 Component 종료 시 해제할 수 있어야 한다.

## 확정한 Build와 배포 기준

- Main·Preload: TypeScript Compiler와 전용 설정을 사용해 `dist-electron/`에 Build
- Renderer: Next.js Static Export 결과인 `out/` 사용
- Packaging: 현재 설치된 electron-builder 사용
- 개발 Loading: `http://127.0.0.1:3000`
- Production Loading: 보안 설정된 `app://` Custom Protocol
- 개발 Build: 서명 없이 로컬 실행 가능
- 배포 Build: `arm64`와 `x64`를 각각 Build하고 `dmg`와 `zip` 제공
- 배포 서명: Developer ID Application 인증서와 Hardened Runtime 사용
- 배포 공증: Apple Notarization과 Stapling 적용
- 자동 Update: MVP 범위에서 제외하고 서명된 수동 Update부터 제공
- Main Process Log: Logger 도구를 정할 때 Electron Log 경로·보존 기간을 함께 확정

Apple Developer 계정, Bundle ID, 인증서와 CI 비밀값은 실제 배포 작업의 선행 조건이다. 인증 정보는 저장소와 문서에 기록하지 않는다.

## 공식 참고

- [Electron Process Model](https://www.electronjs.org/docs/latest/tutorial/process-model)
- [Electron Context Isolation](https://www.electronjs.org/docs/latest/tutorial/context-isolation)
- [Electron Security](https://www.electronjs.org/docs/latest/tutorial/security)
- [Electron Custom Protocol](https://www.electronjs.org/docs/latest/api/protocol)
- [Next.js Static Exports](https://nextjs.org/docs/app/guides/static-exports)
- [electron-builder macOS](https://www.electron.build/v26/docs/mac/)
- [electron-builder Notarization](https://www.electron.build/docs/features/code-signing/notarization/)
