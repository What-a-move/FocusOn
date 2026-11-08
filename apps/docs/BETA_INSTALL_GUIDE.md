# 베타 설치 안내

베타 테스터에게 나눠 줄 설치 방법과, 설치 파일을 만드는 방법을 적는다.

## 테스터용

준비물: Apple 실리콘(M1 이상) Mac, Chrome, Google 계정, 팀이 보낸 `FocusOn-0.1.0-arm64.dmg`와 `focuson-extension.zip`

### 1. Desktop 앱 설치

1. `FocusOn-0.1.0-arm64.dmg`를 열고 FocusOn을 `응용 프로그램` 폴더로 끌어다 놓아요.
2. `응용 프로그램`에서 FocusOn을 **우클릭 → 열기**를 누르고, 경고 창에서 다시 `열기`를 눌러요.
   - 그냥 더블클릭하면 "확인되지 않은 개발자" 창이 뜨고 열리지 않아요.
   - 막히면 `시스템 설정 → 개인정보 보호 및 보안` 아래쪽의 `그래도 열기`를 눌러요.
3. Google로 로그인하고 온보딩 안내대로 화면 기록·알림 권한을 허용해요.

### 2. Chrome 확장 설치

1. `focuson-extension.zip`의 압축을 풀어 지우지 않을 폴더에 둬요. 폴더를 지우면 확장도 사라져요.
2. Chrome 주소창에 `chrome://extensions`를 열고 오른쪽 위 `개발자 모드`를 켜요.
3. `압축해제된 확장 프로그램 로드` → 압축을 푼 폴더를 골라요.
4. 확장 아이콘을 눌러 Desktop 앱에 나온 연결 코드를 입력하고, 사이트 접근을 허용해요.

### 알아 두기

- 새 버전을 받으면 dmg로 다시 덮어써요. 이때 키체인 접근을 묻는 창이 뜨면 `항상 허용`을 눌러요.
- 확장 업데이트는 새 zip 내용으로 같은 폴더를 바꾼 뒤 `chrome://extensions`에서 새로고침을 눌러요.
- 문제가 생기면 `~/Library/Application Support/FocusOn/logs/focuson.log`를 팀에 보내 주세요. 화면 내용이나 토큰은 이 파일에 남지 않아요.

## 팀용: 설치 파일 만들기

베타 서버 주소는 `https`여야 한다(내 컴퓨터 주소만 예외).

```bash
cd apps/desktop
FOCUSON_API_BASE_URL=https://<베타 서버> FOCUSON_GOOGLE_CLIENT_ID=<클라이언트 ID> pnpm dist
```

결과: `apps/desktop/release/FocusOn-<버전>-arm64.dmg`

```bash
cd apps/extension
NEXT_PUBLIC_FOCUSON_API_BASE_URL=https://<베타 서버> pnpm dist
```

결과: `apps/extension/release/focuson-extension.zip` (서버 주소는 빌드할 때 들어간다. 주소 없이 빌드하면 `.env`의 로컬 주소가 들어가므로 베타용은 꼭 지정한다)

- 서명은 ad-hoc이라 Gatekeeper 우회(우클릭 → 열기)가 필요하고, 빌드마다 서명이 바뀌어 업데이트 때 키체인 창이 다시 뜰 수 있다. Apple Developer ID 서명·공증을 하면 둘 다 없어진다.
- 설치 앱은 개발 실행과 저장 위치(`Application Support/FocusOn`), 기기 ID, 키체인 항목이 모두 따로라 같은 Mac에서 함께 써도 서로 로그인을 끊지 않는다.
