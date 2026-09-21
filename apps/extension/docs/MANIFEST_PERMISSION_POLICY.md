# Chrome Extension Manifest 권한 정책

> 현재 상태: Manifest에는 `tabs`, `storage`, `webNavigation`, 모든 HTTP·HTTPS Host 권한이 선언되어 있다. 자동 추적과 HTTP 지원은 확정했지만, 전체 Host 권한의 사용자 승인 방식은 아직 확정하지 않았으므로 Manifest는 변경하지 않는다.

## 기본 원칙

- 권한은 구현한 기능에 필요한 최소 범위만 요청한다.
- 새로운 권한은 사용 목적, 대안, 사용자 Warning을 확인한 뒤 추가한다.
- 사용하지 않는 권한은 제거한다.
- 권한 변경은 Manifest, 기능 PLAN, 개인정보 문서를 함께 갱신한다.

## 현재 권한 검토

| 권한 | 예상 목적 | 현재 상태 | 검토 사항 |
| --- | --- | --- | --- |
| `tabs` | 활성 Tab 전환과 URL·Title 확인 | 사용 확정 | `tabs.onActivated` 중심으로 사용 |
| `storage` | 제외 Domain과 Worker 복구 상태 | 사용 확정 | 제외 Domain은 local, 임시 Navigation은 session |
| `webNavigation` | 최상위 Frame의 Page 이동 확인 | 사용 확정 | `onCommitted` 중심으로 사용 |
| `https://*/*` | HTTPS Page Content Script | 범위 검토 중 | 필수 또는 Optional 승인 방식 결정 필요 |
| `http://*/*` | 오래된 HTTP 학습 Page 분석 | Scheme 지원 확정 | 필수 또는 Optional 승인 방식 결정 필요 |

`tabs`, `storage`, `webNavigation` 사용은 확정했다. 현재 Manifest의 전체 Host 권한이 선언되어 있다는 사실은 승인 방식까지 확정했다는 뜻이 아니다.

## `activeTab` 검토

- FocusOn은 활성 학습 세션 중 Page 이동을 자동 추적하므로 사용자 클릭 시점에만 임시 접근을 주는 `activeTab`만으로 구현하지 않는다.
- `activeTab`은 수동 1회 분석 기능을 별도로 추가할 때만 다시 검토한다.

## Host Permission

- 자동 추적은 여러 학습 사이트에서 동작해야 하므로 HTTP·HTTPS Host 범위 자체는 필요하다.
- 다만 설치 즉시 필수 권한으로 요구할지, `optional_host_permissions`로 선언하고 학습 추적 활성화 버튼에서 요청할지는 사용자와 확정한다.
- 추천안은 `https://*/*`와 `http://*/*`를 `optional_host_permissions`로 선언하고, 기능 설명 후 사용자 동작 안에서 `chrome.permissions.request()`로 승인받는 방식이다.
- 추천안을 선택하면 `scripting` 권한을 추가하고 정적 `content_scripts` 등록은 제거한다. Worker가 활성 세션·현재 Tab·권한을 확인한 뒤 필요한 Page에만 Content Script를 주입한다.
- 같은 Page에 중복 주입하지 않도록 Navigation ID를 확인하고, 세션 종료 후 새 Page에는 더 이상 주입하지 않는다.
- 사용자가 전체 사이트를 허용하지 않으면 승인된 사이트에서만 자동 추적하고, 미승인 사이트에서는 분석 중지 상태를 표시한다.
- 제외 Domain은 권한을 가지고 있어도 수집·전송하지 않는다.
- Chrome 내부 Page, Extension Page, Web Store처럼 접근할 수 없는 URL을 정상 예외로 처리한다.

## HTTP Page 기준

- HTTP 학습 Page의 제목과 허용된 본문 분석은 지원한다.
- HTTP Page의 내용은 전송 중 변조될 수 있으므로 Scheme을 분석 요청 메타데이터에 포함한다.
- FocusOn Server API와 인증 통신은 HTTPS만 사용한다.
- HTTP Page에서 내려받은 Script를 Extension 권한으로 실행하지 않는다.

## 지원 Chrome Version

- 최소 지원 Version은 Chrome 120으로 확정한다.
- 구현 시 `manifest.json`에 `"minimum_chrome_version": "120"`을 추가한다.
- `chrome.storage.session`을 사용하며 Content Script가 직접 Storage에 접근하지 못하도록 Access Level을 신뢰된 Extension Context로 제한한다.

## Permission 추가 절차

1. 필요한 사용자 기능을 PLAN에 작성한다.
2. 권한 없이 구현할 수 있는 대안을 확인한다.
3. Chrome이 보여주는 Permission Warning을 확인한다.
4. 최소 Scope로 Manifest를 수정한다.
5. 허용·거부·권한 철회 흐름을 테스트한다.
6. 이 문서와 개인정보 문서를 갱신한다.

## 금지

- 사용 예정이라는 이유만으로 권한을 미리 추가하지 않는다.
- 권한 오류를 피하려고 Host 범위를 무조건 넓히지 않는다.
- Content Script의 `matches`와 Host Permission 차이를 확인하지 않고 변경하지 않는다.
- Runtime에 필요한 JavaScript를 외부 Server에서 내려받아 실행하지 않는다.

## 공식 참고

- [Chrome activeTab](https://developer.chrome.com/docs/extensions/develop/concepts/activeTab)
- [Chrome Permission Warnings](https://developer.chrome.com/docs/extensions/develop/concepts/permission-warnings)
- [Declare Permissions](https://developer.chrome.com/docs/extensions/develop/concepts/declare-permissions)
- [Chrome permissions API](https://developer.chrome.com/docs/extensions/reference/api/permissions)
