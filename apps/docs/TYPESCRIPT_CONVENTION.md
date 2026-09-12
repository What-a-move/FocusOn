# TypeScript 규칙

## 필수 규칙

- `any`는 원칙적으로 사용하지 않는다. 외부 값은 `unknown`으로 받은 뒤 검사한다.
- API, IPC, Chrome Message처럼 경계를 통과하는 데이터에는 타입을 작성한다.
- 가능한 값이 정해져 있으면 문자열 대신 유니온 타입을 사용한다.
- `as`로 타입 오류를 숨기지 않는다.
- `null`과 `undefined` 가능성을 사용하는 위치에서 확인한다.

## `any` 예외

외부 라이브러리의 타입이 없거나 잘못되어 다른 방법으로 해결할 수 없을 때만 예외를 허용한다.

- 사용 이유를 바로 위 한국어 주석으로 남긴다.
- 한 함수 또는 한 Adapter 안으로 사용 범위를 제한한다.
- 앱 내부 타입과 `packages/shared-types`로 `any`가 퍼지지 않게 변환한다.
- 라이브러리 타입이 제공되면 제거할 수 있도록 관련 Issue 또는 TODO를 남긴다.

## `type`과 `interface`

- 유니온, 매핑 타입, 함수 타입은 `type`을 우선한다.
- 확장 가능한 객체 계약은 `interface`를 사용할 수 있다.
- 같은 목적의 타입 선언 방식을 한 기능 안에서 섞지 않는다.

```ts
type StudySessionStatus = 'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED'

interface StudySession {
  id: string
  status: StudySessionStatus
  startedAt: string
}
```

## 타입 위치

- 컴포넌트 Props: 컴포넌트 파일 가까이 둔다.
- 기능 내부에서만 사용: 해당 `features/기능명/types/`에 둔다.
- Desktop과 Extension이 함께 사용: `packages/shared-types`에 둔다.
- Server 응답과 이름이 같아도 실제 데이터 모양을 확인한 뒤 공유한다.

## 시간과 식별자

- API 시간은 ISO 8601 문자열을 기본으로 사용한다.
- 화면 계산이 필요할 때만 `Date`로 변환한다.
- 사용자 ID, 세션 ID, 탭 ID처럼 의미가 다른 값을 같은 변수 이름 `id`로 섞지 않는다.
