# FastAPI 내부 입력 및 판정 경로

> 2026-11-08 갱신: 이 문서의 과거 `RECEIVED` 응답 예시는 대체되었다. 현재 단일 경로는 입력 검증 후 관련성 판정을 반환한다. 아래 실행법의 URL과 요청 예시는 유지하되, 응답은 [베타 분석 결과](fastapi-relevance-analysis-REPORT.md)를 기준으로 한다.

- 2026-10-29, 기존 #11 사용자 추가 요청. Notion은 권한 오류로 조회 실패, 저장소/사용자 제공 10/26 계약 기준이다.
- `POST /internal/v1/analysis-runs`: 서비스 Bearer 인증 → 최대64KiB JSON → 엄격 Schema → 목표/관측 대조 → 현재 상태·활성 대상·제외·민감정보·출처 검사 → Kiwi + Qwen Hybrid 판정 반환.
- `/health`는 실제 모델 임베딩과 2560차원 벡터 검증을 수행한다. `/docs`와 `/openapi.json`은 내부 입력·판정 계약을 문서화한다.
- `FOCUSON_AI_API_KEY`는 32~1024 UTF-8 바이트이며 누락 시 시작 실패. 사용자 Access JWT와 다른 Backend 전용 서비스 키다. Client에 배포하지 않는다.
- AI 응답 생성 뒤에도 영구 저장·Queue 처리는 하지 않는다. 분석 호출은 동기 방식이며 운영 멱등성은 Server가 보장해야 한다.
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

아래는 형식만 보이는 합성 응답 예시이며 실제 판정 점수 예측값이 아니다. 베타 candidate 모드:

```json
{"decision":"RELATED","confidence":0.86,"reasonCode":"SEMANTIC_GOAL_MATCH","modelVersion":"qwen3-embedding-4b-fp16","promptVersion":"body-context-hybrid-v3-uncalibrated"}
```

## Backend 내부 입력과 오류

- `request`: 기존 ScreenAnalysisRequest. Client에 없던 path sessionId와 확정 goalSnapshot은 Backend가 조립한다.
- `goal`, `context`: Backend가 인증된 현재 DB/세션 상태로 조립한다. 예제 값은 모두 합성이며 실제 Client context를 그대로 신뢰하면 안 된다.
- `extractionStatus`, 선택 `ocr`: 기존 Adapter 입력. Desktop OCR 베타는 정제된 `request.contentPayload.text`만 보내도 된다. 누락된 OCR confidence/capture status는 생성하지 않는다.
- 401 서비스 인증 실패 / 409 종료·비활성·제외·출처 정책 차단 / 413 전체64KiB 초과 / 415 JSON 외 형식·압축 body / 422 Schema·목표/관측 불일치·잔존 민감정보.

AI는 사용자 요청에 따라 0.75 미만과 점수 누락을 `UNCERTAIN`으로 처리한다.

## 검증과 다음 작업

- 현재 관련 회귀 명령과 결과는 [FastAPI 관련성 분석 베타 결과](fastapi-relevance-analysis-REPORT.md)에 기록한다. 합성 테스트 결과는 실제 Ollama/Backend 왕복을 보장하지 않는다.
- 실제 Backend 왕복·최종 판정 실행·confidence 보정·중복/역순 처리·공개 배포는 미완료. Git 커밋·푸시·PR은 수행하지 않았다.
