# 학습 목표 설정 보조 개발 오류 기록

## TypeSafe SDK 예외 모듈 Import 불일치

- 발생일: 2026-09-27
- 증상: `from typesafe_sdk import errors` 실행 시 `ImportError`가 발생해 FastAPI 앱을 Import하지 못했다.
- 원인: 설치된 `typesafe-sdk==0.7.2`는 예외 클래스를 Package 최상위에 공개하지만 `errors` 모듈 객체는 최상위 Export에 포함하지 않는다.
- 해결: `TypeSafeError`, `TypeSafeAPIResponseValidationError`를 `typesafe_sdk` 최상위에서 직접 Import하도록 수정했다.
- 재발 방지: 실제 설치 버전에서 앱 Import와 JEV Adapter 단위 테스트를 실행한다.

## 요청 검증 전 모델 Client 생성

- 발생일: 2026-09-27
- 증상: API Key가 없는 환경에서 공백 요청이 422로 끝나기 전에 Dependency 생성이 실패했다.
- 원인: FastAPI가 Body 검증과 Dependency 해석 과정에서 모델 Client를 먼저 만들 수 있는데 생성자가 즉시 API Key를 요구했다.
- 해결: Client 생성은 부작용 없이 허용하고 실제 모델 호출 시 설정 누락을 `AI_UNAVAILABLE`로 반환하도록 지연했다.
- 재발 방지: API Key 없는 상태의 422 Schema 테스트와 503 모델 장애 테스트를 유지한다.

## 평가 Script 직접 실행 시 Package 경로 누락

- 발생일: 2026-09-27
- 증상: `python evaluation/evaluate.py`에서 `src` Package를 찾지 못했다.
- 원인: 파일 경로로 직접 실행하면 Python 검색 경로가 `AI/evaluation`부터 시작한다.
- 해결: `evaluation`을 Package로 만들고 `python -m evaluation.evaluate`로 실행하도록 문서화했다.
- 재발 방지: 결과 검증 명령에 Offline 평가 Module 실행을 포함한다.

## API Key가 있는 환경에서 단위 테스트가 외부 호출을 시도함

- 발생일: 2026-09-27
- 증상: `.env`에 실제 TypeSafe Key를 넣자 설정 오류 API 테스트가 실제 JEV Client를 생성해 외부 요청 경로로 진입했다.
- 원인: 테스트가 “개발 환경에 API Key가 없다”는 조건에 의존하고 Dependency를 Fake로 교체하지 않았다.
- 해결: 설정 오류를 반환하는 `MisconfiguredJev`를 테스트에 주입해 환경변수와 무관하게 결정적으로 검증한다.
- 재발 방지: 기본 pytest의 모든 모델 경로는 Fake Client 또는 Dependency Override만 사용하고 실제 호출은 명시적 Live 평가와 Streamlit 수동 테스트로 제한한다.

## Vercel AI Gateway 무료 등급의 JEV 호출이 Key 오류로 표시됨

- 발생일: 2026-09-27
- 증상: 유효한 `AI_GATEWAY_API_KEY`와 `typesafe-ai/jev` 설정을 사용해도 JEV 분석 요청이 403으로 실패하고, Streamlit에는 API Key 또는 모델 설정 오류로 표시됐다.
- 원인: 모델 목록 조회와 인증은 성공했지만 실제 추론 요청에서 Vercel이 `RestrictedModelsError`와 함께 무료 등급의 JEV 접근을 거부했다. 제공자를 `typesafe-ai`로 고정해도 같은 응답이 발생해 Provider 자동 선택 문제는 아닌 것으로 확인했다.
- 해결: 해당 Vercel 응답을 별도로 식별해 API Key 오류 대신 유료 크레딧이 필요하다는 안내를 표시하고, 사이드바 문구도 “설정됨”이 아니라 “API Key 감지됨”으로 변경했다.
- 후속 조치: Vercel AI Gateway 연동과 해당 회귀 테스트는 직접 TypeSafe API 전환과 함께 제거했다. 이 기록은 Gateway 재도입 시 모델 목록 조회 성공을 실제 추론 권한으로 간주하지 않기 위한 이력으로 보존한다.

## TypeSafe 직접 API Key 인증 실패

- 발생일: 2026-09-27
- 증상: Vercel AI Gateway를 제거한 뒤 TypeSafe 공식 API의 JEV 판단 요청과 모델 목록 조회가 401로 실패했다.
- 원인: 현재 `AI/.env`에서 읽은 `TYPESAFE_API_KEY`가 TypeSafe 공식 API 인증을 통과하지 못했다. 잔액 부족은 402로 반환되는 별도 경우이므로 이번 실패 원인은 충전 상태가 아니라 Key 값·복사 범위·활성화 상태다.
- 해결: 코드가 `TYPESAFE_API_KEY`를 직접 사용하도록 전환했으며, 사용자가 새로 발급한 공식 Key를 `AI/.env`의 해당 항목에 입력한 뒤 Streamlit 서버를 재시작해야 한다.
- 재발 방지: Key 값은 로그나 문서에 기록하지 않고, Live 확인은 모델 목록 조회와 최소 JEV 판단 요청으로 분리한다.
