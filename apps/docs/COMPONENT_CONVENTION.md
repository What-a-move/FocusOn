# React 컴포넌트 규칙

## 기본 원칙

- 컴포넌트는 한 가지 주요 화면 책임만 가진다.
- Page는 조립에 집중하고 API·IPC·Chrome API 로직은 Hook이나 Service로 분리한다.
- `useState`, `useEffect`, 클릭 이벤트가 필요할 때만 `'use client'`를 사용한다.
- Props는 필요한 값만 받고 거대한 객체 전체를 전달하지 않는다.
- 사용자가 클릭하는 `button`에는 `type="button"`을 명시한다.

## 이름 규칙

```tsx
type StudyTimerProps = {
  remainingSeconds: number
  onPause: () => void
}

export function StudyTimer({ remainingSeconds, onPause }: StudyTimerProps) {
  // ...
}
```

- 컴포넌트와 Props 타입은 PascalCase를 사용한다.
- 이벤트 Props는 `on동작`, 내부 핸들러는 `handle동작`으로 작성한다.
- Boolean 값은 `is`, `has`, `can`, `should`로 시작한다.

## 분리 기준

다음 중 하나에 해당하면 분리를 검토한다.

- 화면 표시와 데이터 통신을 함께 처리한다.
- 같은 UI가 세 번 이상 반복된다.
- 컴포넌트 이름으로 설명하기 어려운 여러 책임이 섞였다.
- 로딩·성공·오류 상태가 한 파일에서 지나치게 복잡해졌다.

줄 수만으로 분리하지 않는다. 작은 파일이 많아져 흐름을 찾기 더 어려우면 같은 기능 폴더 안에서 유지한다.

## 스타일

- Tailwind CSS v4를 기본으로 사용한다.
- 같은 클래스 묶음이 반복되면 공통 컴포넌트 또는 토큰화를 검토한다.
- 조건부 스타일은 상태의 의미가 보이게 작성한다.
- 임의 색상과 간격을 반복해서 만들지 않는다.
