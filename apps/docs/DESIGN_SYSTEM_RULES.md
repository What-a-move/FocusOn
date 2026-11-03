# Frontend 디자인 시스템 규칙

## 목적

Desktop과 Chrome Extension에서 같은 의미의 색상·간격·버튼·문구를 일관되게 사용한다. 디자인 시스템은 화면을 똑같이 복사하는 규칙이 아니라, 같은 제품처럼 느껴지게 만드는 공통 기준이다.

## 토큰 우선 원칙

- 색상, 글자 크기, 간격, 모서리, 그림자는 의미가 있는 CSS 변수로 관리한다.
- 같은 값을 세 번 이상 직접 작성하기 전에 공통 Token이나 Component가 필요한지 확인한다.
- Figma 디자인 값이 있으면 임의 값보다 Figma와 합의된 Token을 우선한다.
- 아직 확정되지 않은 Token은 임의로 공통화하지 않고 `검토 필요`로 남긴다.

```css
:root {
  --color-surface: #ffffff;
  --color-text-primary: #111827;
  --color-feedback-success: #15803d;
  --color-feedback-warning: #b45309;
  --color-feedback-error: #b91c1c;
}
```

위 값은 구조 예시이며 실제 브랜드 색상 확정값이 아니다.

## 공통 Component 기준

- Button, Input, Dialog, Status Badge처럼 의미와 상태가 같은 UI부터 공통화를 검토한다.
- Desktop과 Extension의 화면 크기·사용 흐름이 다르면 스타일 일부를 각 앱에서 조정할 수 있다.
- 모양만 비슷하고 행동이 다르면 하나의 거대한 Component로 합치지 않는다.
- 공통 Component에는 기본 상태, Hover, Focus, Disabled, Loading, Error 상태를 정의한다.

## 문구와 상태

- 같은 세션 상태는 두 앱에서 같은 용어를 사용한다.
- 사용자를 비난하거나 집중 상태를 단정하는 문구를 사용하지 않는다.
- 성공·경고·오류는 색상뿐 아니라 Icon이나 Text로도 구분한다.
- 로딩 중인 Button은 중복 실행을 막고 진행 중임을 알린다.

## 접근성 완료 조건

- 키보드만으로 주요 기능을 사용할 수 있어야 한다.
- Focus 표시를 제거하지 않는다.
- Icon만 있는 Button에는 접근 가능한 이름을 제공한다.
- Text와 Background의 대비를 확인한다.
- 움직임이 있는 UI는 사용자의 움직임 줄이기 설정을 고려한다.

## 문서 갱신 시점

- 새로운 공통 UI 상태가 추가될 때
- Figma Token이나 브랜드 색상이 확정될 때
- Desktop과 Extension에서 같은 UI가 반복될 때
- 접근성 검사 기준이 바뀔 때
