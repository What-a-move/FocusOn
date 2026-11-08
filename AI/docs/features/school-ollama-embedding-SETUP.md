# 학교 Qwen 임베딩 연결·현재 Hybrid 실행 지침

기준일: 2026-10-28. 사용하지 않는 목표 Graph·OpenAI·ChromaDB 설정과 이전 Graph 평가 명령을 제거했다. 학교 PC는 임베딩 벡터를 반환하며, Mac 평가 코드가 Kiwi 키워드·코사인·Hybrid 근거·커버리지와 라벨을 계산한다. 학교 PC에 목표 Graph 생성 Prompt나 OpenAI 키를 전달할 필요가 없다.

## 학교 PC API

현재 Client가 요청하는 형식:

```http
POST /api/embed
Content-Type: application/json
```

```json
{
  "model": "qwen3-embedding:4b-fp16",
  "input": ["Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: React useState 공부", "화면에서 추출한 정제 텍스트"],
  "truncate": false,
  "keep_alive": "10m"
}
```

응답은 아래와 같은 형태이며 각 벡터는 2,560개의 유한 숫자를 포함해야 한다. 아래 `[0.1, 0.2]`는 형식 설명용 축약이며 실제 차원이 아니다.

```json
{
  "model": "qwen3-embedding:4b-fp16",
  "embeddings": [[0.1, 0.2], [0.3, 0.4]],
  "total_duration": 100000000,
  "load_duration": 5000000
}
```

벡터 수·차원·유한성·0벡터·응답 모델명을 검사한다. Client는 `/api/embed`만 호출하고 `/health`, 모델 목록·정밀도·revision 조회를 분석마다 반복하지 않는다. configured revision은 캐시 구분용 설정이며 서버 실제 리비전을 매번 검증한 값이 아니다. 실제 모델 변경 시 별도 운영 확인과 재평가가 필요하다.

## Mac에서 연결 확인

학교 PC와 Mac의 Tailscale을 켜고 학교 임베딩 서비스와 Serve 연결이 실행되어 있어야 한다. 기존 전달 주소로 단일 요청을 확인한다. 이 문서는 해당 서비스의 현재 가동을 보장하지 않는다.

```bash
curl --noproxy '*' --connect-timeout 5 --max-time 60 --fail-with-body -sS \
  -o /tmp/focuson-embed-response.json \
  -w '\nHTTP %{http_code} | 연결 %{time_connect}s | 전체 %{time_total}s\n' \
  'https://<학교-PC의-Tailscale-호스트>/api/embed' \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3-embedding:4b-fp16","input":["React useState 객체 상태 변경을 학습합니다."],"truncate":false,"keep_alive":"10m"}'
```

이 요청은 임베딩 통신만 확인하며 관련성 라벨을 반환하지 않는다. 라벨 테스트는 아래 Mac Hybrid 실행으로 한다.

## 현재 코드 실행

Streamlit 화면은 삭제했다. FastAPI 수신 실행법은 [수신 지침](fastapi-reception-REPORT.md)을 따른다. 현재 FastAPI는 수신만 확인하며 관련성 판정을 실행하지 않는다.

## 백엔드 서버 Tailscale 연결

백엔드를 실제로 운영할 서버에 Tailscale을 설치하고 학교 PC와 같은 tailnet에 등록한다. 같은 tailnet에서 필요한 장치·서비스 접근을 허용한다. 개발자의 노트북 연결과 서버 연결을 각각 확인한다. 설치·장기 운영 지침은 https://tailscale.com/docs/how-to/set-up-servers 를 따른다.

Linux에서 설치가 끝난 뒤 서버에서 `sudo tailscale up`을 실행하고 표시된 인증 URL로 해당 tailnet에 로그인한다. `tailscale status`로 등록과 연결을 확인한다. 팀원 계정 초대 및 접근 정책 설정은 tailnet 관리자가 진행한다.

모델 접속 대상은 `https://<학교-PC의-Tailscale-호스트>/api/embed`이다. 위의 Mac 연결 확인과 같은 합성 요청으로 서버에서 HTTP 200과 벡터 응답을 확인한다. 학교 모델 프록시는 `keep_alive: "10m"`만 허용한다. 백엔드가 기존 Docker 컨테이너에서 실행된다면 그 컨테이너에서도 DNS와 HTTPS 요청을 별도로 확인한다.

이 모델 주소는 임베딩 벡터를 반환한다. 관련성 판정용 FastAPI의 실행 위치·주소·인증 키는 별도로 확정해야 한다. 사용자에게 노출할 공개 API는 백엔드가 제공한다. FastAPI까지 학교 PC 또는 배포 서버에서 운영해야 개발자 Mac에 의존하지 않는다.

## 2026-11-03 실제 모델 지연 측정

Mac에서 localhost FastAPI HTTP → Tailscale → 실제 학교 Qwen을 호출했다. 모델은 `/health`로 먼저 준비했으며, 최초 분석 1건 뒤 합성 DOM·TAB_META 각 20건을 순차 실행했다. 같은 관련/무관 fixture를 반복했으며 OCR·실제 화면·배포 Backend·동시 부하는 포함하지 않는다. p95는 정렬 후 ceil(0.95 * N)번째 값이다.

- 설정: candidate 모드, 모델 호출 timeout 2.5초, 재시도 0회, keep_alive=10m.
- 최초 분석: 2.24초. 이후 40건 p50=1.10초, p95=2.32초, 최대=4.04초, 3초 초과=2건, 분석 불가=0건.
- DOM 20건 p95=3.76초, 메타데이터 20건 p95=1.83초. 본문 판정은 이번 표본에서도 3초 목표를 넘었다. 전체 3초 내 완료 보장 및 전체 제품 p95 달성으로 해석하지 않는다.

## 코드 위치와 회귀 검사

- `src/models/embedding_client.py`: Qwen HTTP 전송·벡터 검증·고정 retrieval 지침
- `src/analysis/vector_math.py`: 공용 정규화·코사인
- `src/preprocessing/`: 입력 전처리·품질·민감정보 검사
- `src/analysis/acquisition_policy.py`: 메타데이터 재확인·Desktop 대상 방어
- `evaluation/graphless_hybrid.py`: Kiwi 키워드·Hybrid 점수·근거/커버리지
- `evaluation/analysis_runner.py`: 현재 입력 변환·평가·벡터 캐시
- `src/api/relevance_contract.py`: 공개 입력 변환과 응답 검증 도구

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q
```

회귀는 합성 벡터·HTTP Mock 검사이며 실제 학교 모델 정확도/속도와 구분한다. 모델 실측은 테스트 환경·cacheHit·API 요청 수·모델 로드/처리 시간·전체 시간을 함께 보고한다. 원문·비밀키·벡터를 저장소에 커밋하지 않는다.
