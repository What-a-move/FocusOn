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
  'https://desktop-n7o3o02.tail1023df.ts.net/api/embed' \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3-embedding:4b-fp16","input":["React useState 객체 상태 변경을 학습합니다."],"truncate":false,"keep_alive":"10m"}'
```

이 요청은 임베딩 통신만 확인하며 관련성 라벨을 반환하지 않는다. 라벨 테스트는 아래 Mac Hybrid 실행으로 한다.

## 현재 코드 실행

```bash
cd /Users/lyg123d/Documents/what_a_move
python3 -m pip install -r AI/evaluation/requirements-streamlit.txt
python3 -m streamlit run AI/evaluation/similarity_streamlit.py --server.address 127.0.0.1 --server.port 8501
```

Streamlit에서 목표와 추출 JSON을 넣고 비교한다. 학교 API 기본 주소는 위 HTTPS 주소이며 별도 배포에서는 화면 입력 또는 `FOCUSON_EMBEDDING_URL`로 바꾼다. 모델/차원을 줄이거나 실제 revision이 바뀌면 현재 보정 설정을 그대로 정확도 검증값으로 사용하지 않는다.

- 메타데이터: APP_META·TAB_META. RELATED만 확정하며 그 외 정상 결과는 Chrome DOM/Desktop OCR 재확인을 표시한다.
- 본문: CHROME_DOM 또는 DESKTOP_APP_OCR. text 최대 2,000자. Desktop OCR 품질 정보가 없거나 실패하면 UNCERTAIN이다.
- Chrome viewport OCR: enum은 남기지만 MVP 분석·추가 요청에서 제외한다.
- Kiwi는 로컬에서 목표·제목·본문의 키워드를 비교한다. OpenAI 호출·목표 Graph 생성·ChromaDB 저장은 없다.
- 형식/문맥/민감정보·품질 검사 후 Qwen 벡터를 받아 기존 Hybrid 계산으로 라벨·사유·근거·커버리지·시간을 표시한다. 모델/네트워크 실패는 무관으로 처리하지 않는다.
- 공개 Backend 분석 API, 실제 캡처·재수집, 판정 confidence 보정·알림은 후속 작업이다.

## 코드 위치와 회귀 검사

- `src/models/embedding_client.py`: Qwen HTTP 전송·벡터 검증·고정 retrieval 지침
- `src/analysis/vector_math.py`: 공용 정규화·코사인
- `src/preprocessing/`: 입력 전처리·품질·민감정보 검사
- `src/analysis/acquisition_policy.py`: 메타데이터 재확인·Desktop 대상 방어
- `evaluation/graphless_hybrid.py`: Kiwi 키워드·Hybrid 점수·근거/커버리지
- `evaluation/streamlit_runner.py`: 현재 입력 변환·평가·벡터 캐시
- `src/api/relevance_contract.py`: 공개 입력 변환과 응답 검증 도구

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q
```

회귀는 합성 벡터·HTTP Mock·UI 검사이며 실제 학교 모델 정확도/속도와 구분한다. 모델 실측은 테스트 환경·cacheHit·API 요청 수·모델 로드/처리 시간·전체 시간을 함께 보고한다. 원문·비밀키·벡터를 저장소에 커밋하지 않는다.
