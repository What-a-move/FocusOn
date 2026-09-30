# DOM·OCR 입력 텍스트 검증·전처리 결과

- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)
- 관련 PR: [#13](https://github.com/What-a-move/FocusOn/pull/13)
- PLAN: [input-text-validation-preprocessing-PLAN.md](input-text-validation-preprocessing-PLAN.md)
- 작업 Branch: `feat/11-ai-input-text-preprocessing`
- 작성일: 2026-09-30

## 구현 결과

- DOM·OCR 문단을 같은 내부 구조로 검증하고 `kind`, `id`, `order`를 보존한다.
- 요청 식별값을 호출자가 제공한 현재 이벤트 문맥과 비교해 stale 요청을 폐기한다.
- 제외·실패·미지원, 민감정보 후보, 품질 부족 입력은 분석 가능 상태로 내보내지 않는다.
- 본문형 텍스트의 HTML 잔여 태그·공백을 정리하며 코드와 표의 줄·들여쓰기를 보존한다. 정확히 같은 종류·텍스트의 문단만 중복 제거한다.
- 정제된 제목·문단 순서와 전처리 버전으로 SHA-256 해시를 만든다. 입력 ID는 해시에서 제외한다.
- 외부 API 상태명과 OCR 품질 경계값은 확정하지 않았다. 내부 모델은 추가 런타임 의존성 없이 구현했다.

## 검증

- `python3 -m pytest -q AI/tests/test_content_parser.py AI/tests/test_text_cleaner.py AI/tests/test_preprocessing_validator.py`: **37 passed**.
- 정상 DOM·OCR, 구조·순서, 빈/깨진/중복 문단, 민감정보 후보, 제외·부분·실패, stale 이벤트, OCR 인식 점수, 결정적 해시를 확인했다.
- 테스트 중 발견한 정제 공백 오류 1건을 수정하고 같은 테스트로 재검증했다. 상세 내용은 [ERROR](input-text-validation-preprocessing-ERROR.md)에 기록했다.

## 남은 제한사항

- 문자열 패턴 검사는 모든 개인정보를 탐지할 수 없다. 제외 화면·폼·개인 메시지 차단은 입력 수집 단계에서도 유지해야 한다.
- 본문만으로 메뉴 여부를 안전하게 판별할 수 없어 동일 문단 중복 외의 메뉴 삭제는 수행하지 않는다.
- 호출자가 비동기 결과를 적용하기 전에 현재 이벤트 문맥을 다시 검사해야 한다.
- OCR 인식 점수는 명시적으로 주입한 기준으로만 품질을 제한하며, 그 값은 관련성 확률이 아니다.
- 문단 수 64개, 개별 텍스트 8,000자, 전체 128,000자 제한은 자원 보호를 위한 임시 상한이다. 실제 입력 분포와 지연 평가 후 조정이 필요하다.
