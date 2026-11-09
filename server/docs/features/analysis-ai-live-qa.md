# 실제 Backend–AI–학교 모델 연결 QA

사용자 지시로 현재 실제 AI 연결 QA는 보류했다. 최신 dev0ca256e에는 AI #190의 0.6 판정 코드가 병합됐다. 아래 네트워크/설정 결과는 마지막 검사 당시 이력이며 실제 모델 연결 성공은 아니다. 서버 context/receipt 연결은 intervention-policy-QA.md의 최신 결과를 따른다.

관련: #182, AI #154 / PR #190. 이 문서는 실행 절차와 관측 결과를 구분한다.

## 이전 실행 확인 (2026-11-09)

- dev `4e212de`, #190은 OPEN이며 최신 head `d703397`의 실제 candidate 기준은 0.6이다.
- server/.env와 현재 실행 shell에 AI_ANALYSIS_URL/FOCUSON_AI_API_KEY가 없다. AI/.env 및 8000/8001의 실행 AI 서비스도 이번 확인에서 찾지 못했다.
- 문서의 학교 Tailscale 모델 주소에 비민감 합성 문장 1개로 POST /api/embed를 시도했다. 약 456ms 뒤 DNS 해석 오류(gaierror)로 실패했다. 학교 모델이 꺼졌다고 단정할 수 없다.
- 사용자가 학교 주소 `100.116.183.94`를 제공했다. 기존 hostname의 HTTPS 443을 해당 IP에 고정해 인증서 검증을 유지한 조회와 `http://100.116.183.94:11434/api/version` 조회가 각각 연결 제한 3초 안에 연결되지 않았다(HTTP 000). 해당 IP의 현재 route interface는 en0이다. 사용자가 11434 포트를 확인했고 Tailscale 연결은 확인 중이라고 답했다. 아직 연결 완료 응답은 없으며 모델 가동 여부는 판단할 수 없다.
- 실제 AI /health·모델 판정·Backend 정책 누적·포키 표시 QA는 아직 성공하지 않았다. 제공 IP는 학교 모델 주소로 확인 중이며 Backend용 AI 서비스 origin으로 임의 설정하지 않았다.
- 서비스키·학교 응답 원문·벡터는 출력/문서/저장소에 남기지 않았다.

## 설정 후 실행

기존 개발 DB5432/8080을 초기화하거나 중지하지 않는다. 키를 CLI 인자/채팅에 붙이지 않는다. server/.env에 다음 두 값만 준비한다.

```dotenv
AI_ANALYSIS_URL=<실행 중인 AI 서비스의 origin>
FOCUSON_AI_API_KEY=<AI와 동일한 서비스 키>
```

Backend의 주소는 AI origin이다. 학교 Ollama 주소는 AI 담당의 FOCUSON_EMBEDDING_URL로 설정한다. 학교 Ollama를 Backend AI origin에 직접 넣으면 안 된다. AI 측 candidate 모드·실제 모델 URL·timeout/재시도 설정은 AI 담당과 확인한다. #190 배포/실행본의 /health는 실제 embedding probe와 modelConnected를 반환해야 한다. 이전 dev의 고정 health 200은 연결 증거가 아니다.

### 1. AI HTTP readiness와 합성 시나리오

저장소 루트에서 Python 표준 라이브러리만 사용한다.

```bash
python3 server/scripts/verify_analysis_ai.py
```

- 환경 변수는 env 파일보다 우선한다. env 파일은 두 키의 literal 값만 읽고 shell 구문을 실행하지 않는다.
- /health의 modelConnected/analysisEnabled와 정상 상태를 확인한 뒤 정식 분석 경로에 관련/무관 합성 요청을 한 번씩 보낸다.
- 서비스키는 POST Authorization에만 넣고 redirect는 따라가지 않는다. JSON 응답 크기를 제한하고 결과 필드만 출력한다.
- RECEIVED는 판정 성공으로 처리하지 않는다. UNCERTAIN 응답은 안전 결과지만 시나리오 성공은 아니다.
- 종료 코드0/AI_SCENARIOS_VERIFIED도 AI HTTP 합성 사례 두 개가 기대대로였다는 뜻이다. 출력의 appInterventionVerified는 항상 false다. 정확도 평가·Java client·DB/포키 QA를 대신하지 않는다.

### 2. 실제 Java Backend client와의 연결

예시처럼 기존 server/.env를 현재 shell에 읽되 값을 출력하지 않는다. 명시 flag가 없으면 이 테스트는 외부 AI를 호출하지 않고 생략된다.

```bash
set -a
. server/.env
set +a
FOCUSON_LIVE_AI_TEST=true ./server/gradlew -p server test --tests '*AnalysisAiLiveTests'
```

- 실제 AnalysisAiClient로 정식 경로·Bearer·traceId·내부 envelope·응답 정규화를 검증한다.
- health가 실제 모델 연결/candidate 모드를 확인하지 못하거나 판정이 보류/예상과 다르면 실패한다. 네트워크·모델 실패를 테스트 통과로 바꾸지 않는다.
- 이 테스트는 DB를 수정하지 않는다. 서버 JVM을 호스트에서 실행했으면 호스트 경로 검증이며, 배포 컨테이너 네트워크를 검증한 것으로 취급하지 않는다. 실제 배포 환경의 DNS/경로도 별도 확인한다.

### 3. 실제 앱 전체 흐름

전경 context·관측 소속·receipt 계약/구현(P1~P4)이 선행이다. 일반 Desktop 실행에서 실제 세션 분석 요청을 전송하고 같은 traceId로 Backend AI 호출/판정 저장, heartbeat 시간 정책, 개입 저장·조회, 실제 채널 전송과 receipt, 사용자 ack를 확인한다. FOCUSON_DEMO=1은 interventions GET을 가로채므로 실제 연동 QA에 사용하지 않는다.

#164 종료 및 리포트 연결까지는 별도 담당/후속 QA다. 이탈 누적을 만들려고 UNCERTAIN·메타데이터 UNRELATED·보호 상태를 강제로 UNRELATED로 바꾸지 않는다.

## 검사 도구 회귀

```bash
python3 -m unittest discover -s server/scripts -p 'test_verify_analysis_ai.py' -v
```

loopback HTTP stub을 이용해 인증·trace·정식 경로·redirect 비전달·키/원문 비노출·고정 health 거절·RECEIVED 거절·0.6 경계·UNCERTAIN 미완료 구분을 검증한다. 이 결과를 실제 학교 모델 연결 성공이라고 표현하지 않는다.

## 이번 로컬 검증 결과

- 검사 도구 loopback 회귀 14개 통과. 실제 학교 모델 호출 성공을 의미하지 않는다.
- Backend 전체 build·Checkstyle 통과: 239개, 실패0·오류0·생략12. 생략에는 명시 실행 플래그가 없는 실제 AI 검사 2개가 포함된다.
- Java readiness 회귀는 응답 본문 대기 시간 제한과 민감 응답 비노출을 검증했다. 격리 리뷰 지적 3건을 보완하고 재검토했다.
- 설정 없는 현재 실행 결과는 AI_CONFIGURATION_MISSING_OR_INVALID다. PG 재검증은 이번 검사 도구 추가 작업에서 실행하지 않았다.
