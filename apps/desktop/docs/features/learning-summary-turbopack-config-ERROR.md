# Desktop 오류 보고서: 개발 서버 번들러 설정 충돌

## 오류 기본 정보

- 오류 번호: #29-1
- 오류 식별명: `learning-summary-turbopack-config`
- 기능명: 학습 정리 시연
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-14
- 심각도: 보통
- 상태: 해결
- 관련 Issue: [#29](https://github.com/What-a-move/FocusOn/issues/29)

## 재현 방법과 결과

1. 운영 번들에서 개발 Fixture를 제거하기 위해 `next.config.ts`에 Webpack alias를 추가한다.
2. Next 16 기본 `next dev`를 실행한다.
3. Turbopack 기본 개발 빌드와 사용자 Webpack 설정이 함께 있어 개발 서버가 중단된다.

기대 결과는 개발 전용 시연을 열 수 있는 것이며, 실제 메시지는 `This build is using Turbopack, with a webpack config and no turbopack config`였다. 세션·화면 원문은 오류 로그에 포함되지 않았다.

## 원인 및 해결

- 원인: Next 16은 개발 시 Turbopack을 기본 사용하며 별도 Webpack 설정을 가진 프로젝트에는 명시적인 Turbopack 설정을 요구한다.
- 해결: `next.config.ts`에 빈 `turbopack` 설정을 추가했다. 개발 서버는 Turbopack으로, 기존 운영 빌드는 `next build --webpack`으로 실행한다.
- 검증: 개발 서버 기동과 시연 Route 접근, 운영 빌드 및 운영 번들에서 합성 Fixture 문자열 부재를 확인한다.

## 남은 작업

- 제품 API 연결 전까지 개발 시연을 운영 사용자 기능으로 노출하지 않는다.
