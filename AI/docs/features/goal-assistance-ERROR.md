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
