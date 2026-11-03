# FastAPI 내부 입력 수신 결과와 실행법

- 2026-10-29, 기존 #11 사용자 추가 요청. Notion은 권한 오류로 조회 실패, 저장소/사용자 제공 10/26 계약 기준이다.
- `POST /internal/v1/analysis-runs`: 서비스 Bearer 인증 → 최대64KiB JSON → 엄격 Schema → 목표/관측 대조 → 현재 상태·활성 대상·제외·민감정보·출처 검사 → HTTP200 수신 확인.
- `/health`는 수신 서버 생존만 확인한다. `/docs`와 `/openapi.json`은 내부 입력을 문서화한다.
- `FOCUSON_AI_API_KEY`는 32~1024 UTF-8 바이트이며 누락 시 시작 실패. 사용자 Access JWT와 다른 Backend 전용 서비스 키다. Client에 배포하지 않는다.
- 저장·Queue·Qwen 호출·키워드 분석·판정은 이번 범위 밖이다. `RECEIVED`, `analysisExecuted: false`는 수신/검증 완료만 의미한다. 작업을 예약하지 않으므로 HTTP202를 사용하지 않는다. 재전송마다 검증하며 운영 멱등성을 보장하지 않는다.
- 오류에 원문·키·검증 input을 포함하지 않는다. DOM/OCR 본문은 기존 2,000자 제한을 따른다.

## 실행

저장소 루트 기준. 기존 `AI/.env`에 Backend와 공유할 새 `FOCUSON_AI_API_KEY`를 직접 설정한다. `.env`는 커밋하지 않는다. 키 생성 예: `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'`.

```bash
python3 -m pip install -r AI/requirements.txt
python3 -m uvicorn AI.src.main:app --env-file AI/.env --host 127.0.0.1 --port 8001 --no-access-log
```

`http://127.0.0.1:8001/docs`에서 Authorize에 같은 서비스 키를 입력한다. 기본 bind는 로컬 Mac만 허용하며, 외부 연결의 TLS·배포·네트워크 구성은 별도다. 기존 학교 임베딩 API 8000번과 구분한다.

별도 터미널의 저장소 루트에서 합성 입력을 보낸다:

```bash
python3 - <<'PY'
import json
from pathlib import Path
import httpx
from dotenv import dotenv_values

key = dotenv_values('AI/.env').get('FOCUSON_AI_API_KEY')
if not key:
    raise SystemExit('AI/.env에 FOCUSON_AI_API_KEY를 설정하세요.')
payload = json.loads(Path('AI/examples/analysis-reception.json').read_text())
with httpx.Client(trust_env=False, timeout=10) as client:
    response = client.post('http://127.0.0.1:8001/internal/v1/analysis-runs',
                           headers={'Authorization': f'Bearer {key}'}, json=payload)
    print('HTTP:', response.status_code)
    print(response.json())
PY
```

정상 응답:

```json
{"status":"RECEIVED","sessionId":"session-demo","clientRunId":"cc3a11f9-3d96-42de-bc10-b8fcaaea4e37","analysisExecuted":false}
```

## Backend 내부 입력과 오류

- `request`: 기존 ScreenAnalysisRequest. Client에 없던 path sessionId와 확정 goalSnapshot은 Backend가 조립한다.
- `goal`, `context`: Backend가 인증된 현재 DB/세션 상태로 조립한다. 예제 값은 모두 합성이며 실제 Client context를 그대로 신뢰하면 안 된다.
- `extractionStatus`, 선택 `ocr`: 기존 Adapter 인자. 이 envelope의 HTTP 위치는 내부 구현 제안이며 Backend 합의가 필요하다. Client 공개 필드를 추가하지 않았다.
- OCR 품질 없이 형식 수신은 가능하다. 실제 분석 가능 여부와 품질 보류는 후속 분석 연결에서 검사한다.
- 401 서비스 인증 실패 / 409 종료·비활성·제외·출처 정책 차단 / 413 전체64KiB 초과 / 415 JSON 외 형식·압축 body / 422 Schema·목표/관측 불일치·잔존 민감정보.

## 검증과 다음 작업

- 수신 HTTP + 기존 화면 계약: 69 passed.
- 임시 Uvicorn 실제 loopback: health200, 미인증401, 인증 수신200, OpenAPI200. 확인 후 종료, 모델 요청0회.
- 전체 회귀: 209 passed, 75 failed, 1 skipped, 16 subtests passed. 75건은 기본 Python의 kiwipiepy 미설치로 기존 Graphless 평가 검사에 실패했다. 수신 API에는 Kiwi 의존성이 없다.
- 실제 Backend 왕복·최종 판정 실행·confidence 보정·중복/역순 처리·공개 배포는 미완료. Git 커밋·푸시·PR은 수행하지 않았다.
