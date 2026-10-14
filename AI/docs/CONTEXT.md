# AI 작업 컨텍스트

> AI 담당자가 작업 시작 시 가장 먼저 읽는 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

- 학습 목표와 화면·페이지 정보의 관련성 판단
- OCR·페이지 텍스트 정제와 입력 품질 판단
- 페이지 이동·체류 시간을 이용한 목표 이탈 및 학습 흐름 분석
- MediaPipe가 클라이언트에서 계산한 상태값의 보조 분석
- 분석 결과의 상태값·점수·신뢰도 반환
- AI API 입력·출력 계약 관리
- 평가 데이터와 모델·프롬프트 품질 확인

## 현재 상태

### 완료된 작업

- AI 기본 디렉터리와 평가 디렉터리가 생성되어 있다.
- AI 전용 문서 구조와 기능별 템플릿을 구성했다.
- 공통 API 응답 초안은 루트 `docs/API_CONTRACT.md`에 정의되어 있다.
- 현재 활동 관련성 API, 분석 기준, Prompt 가이드를 초안으로 작성했다.
- 관련성, 학습 흐름, MediaPipe 사용자 상태를 분리된 기능으로 개발하는 방향을 정리했다.
- Notion AI 기획 v1.1의 목표 보조 6상태, Client별 OCR 경로, 두 분석 제외 모드, 최근 7일 개인화 휴식 입력 계약을 문서 초안에 반영했다.
- Issue #3에서 AI 개발 불변 규칙, 다중 상태 모델, 데이터 수명, 평가 기준 문서 초안을 작성했다.
- Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로, ColPali 시각 콘텐츠 비교 실험 경로를 문서로 구분했다.
- 목표·논리 세션·회차·이벤트·시간 집계 계약을 `goal_session_spec.md`로 분리했다.
- 콘텐츠 감지·Chrome 내부 OCR·Desktop Apple Vision OCR·ColPali 입력 경계를 `content_acquisition_spec.md`로 분리했다.
- FocusSessionAgent, 피드백·개인화, 비동기 근거 기반 노트의 상세 계약 초안을 추가했다.
- Issue #11의 DOM·OCR 공통 입력 모델, 텍스트 정제·청크 분할, 품질·개인정보·중복·stale 검증과 단위 테스트를 구현했다.
- RTX A4000 유사도 비교용 실험 지침과 방식별 결과 양식을 `AI/evaluation/SIMILARITY_EVALUATION_GUIDE.md`에 작성했다. 실제 임베딩·재정렬 평가 결과는 아직 없다.
- GoalProfile/DOM/OCR 텍스트를 붙여넣어 1~7번 후보를 비교하는 로컬 Streamlit 실험 화면을 `AI/evaluation/similarity_streamlit.py`에 구현했다. 임베딩·재정렬 모델의 실제 성능 측정과 임계값 보정은 아직 없다.
- 실험 화면의 2번은 GoalProfile 확장 키워드, 4번은 개념별 임베딩 점수, 5번은 GoalProfile 직접·보조 관계 경로를 사용한다. 별도 선수 관계가 없어 5번 Graph는 제한된 실험형이다.

### 진행 중인 작업

- Issue #10 확정 후 Client·Server·AI의 JSON 필드명과 enum 통일 및 외부 입력 Adapter 연결
- 루트 `docs/ARCHITECTURE.md`의 Desktop 중심 흐름과 Notion의 Extension 중심 기본안 차이 검토
- Server·Extension·공유 타입 담당자와 다중 상태 및 API 경로 검토
- 목표·세션·회차·이벤트 관계와 사용자당 동시 활성 회차 정책 검토
- Agent 도구 Schema·예산·Checkpoint 저장소와 피드백 적용 범위 검토
- 학습 노트 Queue·근거 Schema·재생성·삭제 정책 검토
- 기능별 PLAN·ERROR·REPORT와 GitHub Issue·PR 역할 정리
- ColPali 실행 위치와 개인정보·자원 예산 검토
- Notion 내부의 카메라 MVP 포함·출시 미정 충돌을 해결하고 출시 범위를 결정

### 아직 진행하지 않은 작업

- 실행 가능한 AI 서버 진입점 구현
- 합의된 공개 Pydantic 요청·응답 Schema 구현·Server 연결 (학교 내부 입력 Schema는 구현됨)
- 학교 Qwen3 API 연결은 실제로 확인했으며, 학교 GPU 사용률·VRAM 및 정확도·성능 기준 검증은 남아 있음
- LLM Fallback 구현
- Server 연동
- 학교 GPU 성능·독립 정답 세트 정확도 평가 (합성 평가 CLI는 구현됨)
- 목표·세션·회차·이벤트·집계 Schema 구현
- FocusSessionAgent 상태 그래프와 제한 도구 구현
- 피드백 저장·캐시·알림·집계 무효화 구현
- 비동기 학습 노트 작업과 근거 검증 구현
- 페이지 이동·체류 시간 기반 학습 흐름 분석
- MediaPipe 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동

### 확인된 문서·코드 차이

- `packages/shared-types`는 Notion 기준 공개 상태 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 정의한다. 내부 AI 세부 상태는 API 경계에서 변환한다.
- Extension Manifest에는 아직 `activeTab`, `nativeMessaging` 권한이 없고 Content Script·Service Worker는 골격 상태다.
- Server에는 실행 설정과 빌드 골격만 있고 목표·세션·이벤트·AI 연동 코드가 없다.
- AI 실행 서버·공개 API 연결은 골격 상태다. Issue #11의 순수 전처리·학교 내부 Schema·Graph·Hybrid 판정·Ollama Client·합성 평가 CLI는 구현되어 있다. 실제 Qwen API/벡터 생성과 임시 Adapter를 통한 합성 Hybrid 실행은 확인했지만 GPU 사용률·VRAM·정확도는 검증하지 않았다.
- Issue #11의 외부 요청 필드와 enum은 Issue #10 및 Client·Server 계약 확정 전까지 제안이다. 순수 모듈은 문단당 최대 800자를 기본값으로 사용하며 다른 길이·품질 수치는 설정 가능한 초기 실험값이다.
- 루트 아키텍처는 Desktop 화면 분석을 기본으로 설명하고, 최신 AI 기획은 Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR을 Client별로 분리해 제안한다. 팀 합의 후 공통 문서를 갱신해야 한다.

## 현재 기술

- 언어: Python
- API 서버: FastAPI 검토
- Schema 검증: Pydantic 2.13.5 (학교 내부 Schema 구현, 공개 API 합의 대기)
- 테스트: pytest 9.1.1
- 모델: 학교 Ollama의 `qwen3-embedding:4b-fp16` 실제 벡터 반환 확인; LLM 공급자·운영 연결 계약 검토 필요
- 분석 대상: 화면·페이지에서 추출한 텍스트와 메타데이터
- OCR: Chrome Extension 내부 로컬 OCR과 macOS Desktop Apple Vision OCR의 독립 경로 제안; AI는 허용된 정제 텍스트만 평가
- 시각 분석: ColPali는 PDF·Canvas·이미지·슬라이드 대상 비교 실험 단계
- 카메라 분석: 출시 여부 보류; 출시되더라도 MediaPipe 클라이언트가 계산한 최소 상태값만 사용
- 원본 저장: 원본 화면·카메라 영상은 기본 저장하지 않음

## 예상 데이터 흐름

```text
Client별 사전 제외·권한 검사
  → Chrome DOM 우선 추출 → 부족할 때 Extension 내부 로컬 OCR
  → macOS Desktop 허용 화면 → Apple Vision OCR
  → 개인정보·품질 검사
  → Spring Server 인증·소유권·최신 버전 검증
  → AI 텍스트 정제
  → 규칙 기반 판단
  → 임베딩 판단
  → 애매한 경우 LLM 또는 FocusSessionAgent 판단
  → 관련성·흐름·행동 상태 분리
  → Server 결과 저장
  → Extension·웹 대시보드 상태·알림·기록 표시
```

## 반드시 지켜야 하는 조건

- 입력 데이터에 비밀번호·결제 정보·개인 메시지가 포함되지 않도록 확인한다.
- 원본 화면·카메라 영상을 기본 저장하지 않는다.
- OCR 실행과 원본 이미지 처리는 AI 서버 담당 범위에 포함하지 않는다.
- Server 또는 외부 ColPali로 원본 이미지를 전송하려면 현재 개인정보 결정 변경이 먼저 필요하다.
- 관련성 분석 API에는 체류 시간과 MediaPipe 상태값을 섞지 않는다.
- 학습 흐름과 사용자 상태는 별도 분석 기능으로 구현한다.
- 추출·분석·관련성·흐름·행동 상태를 서로 다른 필드로 관리하는 안을 검토한다.
- `OFF_TASK`만으로 `DRIFT_RISK`, 알림 또는 차단을 결정하지 않는다.
- 판단이 어렵거나 실패한 경우 목표 무관이나 이탈로 단정하지 않는다.
- 규칙이나 임베딩으로 충분히 판단 가능한 요청에는 LLM을 호출하지 않는다.
- 모델·프롬프트·임계값 변경은 `DECISION_RECORD.md`에 기록한다.
- 분석 결과에는 최소한 상태값과 신뢰도 기준을 포함한다.
- 오래된 목표·회차·탭 결과는 적용 전에 폐기한다.
- 같은 이벤트·세션 명령·노트 작업의 중복 처리는 멱등해야 한다.
- 명시적 사용자 수정은 정확히 일치하는 목표·콘텐츠 범위에서 캐시보다 우선한다.
- 학습 시간은 Server가 계산하고 LLM이 추정하지 않는다.
- 학습 노트는 관찰 근거 없이 이해·해결 완료를 주장하지 않는다.
- 회차 종료와 시간 저장은 학습 노트 생성 성공에 의존하지 않는다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 작성한다.
- 분석 제외는 `ANALYSIS_ONLY`와 `ANALYSIS_AND_TIME`을 구분하고 두 경우 모두 콘텐츠 payload를 AI에 보내지 않는다.
- 목표 보조는 사용자의 최종 확인 전 확정 목표로 사용하지 않는다.

## 작업 종료 시 갱신

- 완료·진행 중·미착수 작업
- 입력·출력 계약 변경
- 모델·프롬프트·임계값 결정
- 발생한 오류와 평가 결과
- 다음 작업과 선행 조건

## 2026-10-07 학교 캡스톤 지침 작성

- 이재빈 노션 전처리·관련성 문서를 기준으로 [통합 #11 PLAN](features/learning-goal-content-relevance-evaluation-PLAN.md)을 작성했다. Issue 본문은 지침의 요구사항 원본으로 사용하지 않았다.
- 현재 개발 방향은 Hybrid 6번·Qwen3-Embedding-4B·FP16·vLLM이며 숫자 결합 비율·임계값은 미확정이다. Windows GPU는 WSL2·Linux 방향으로 호환성을 확인한다.
- 김성현 → 이재빈 입력은 text·ocr 형식, 최종 qualityScore·contentHash는 이재빈 전처리 책임이다. 출력 계약은 유지하고 백엔드 필드 9개는 협의 예정이다.
- 이번 Branch에서 변경한 것은 지침·작업 기록 문서다. AI 모델·관련성 CLI·FastAPI 구현 및 학교 GPU 성능 측정은 수행하지 않았다. #11 작업 공간의 미커밋 전처리는 자동 통합하지 않았다.
- 기존 문서의 후보 미정·사용자 판정 수정/피드백 우선 제안은 이번 PLAN의 현재 구현 근거로 사용하지 않는다. 공통 API 계약의 채택 여부는 별도 확인한다.

## 2026-10-07 이슈·Branch 통합

- 기존 #14 관련성 구현·평가 범위를 #11 입력 검증·전처리와 통합한다.
- 통합 Branch는 `feat/11-ai-preprocessing-relevance`이며 학교 지침서도 이 Branch를 가리킨다.
- 전처리와 관련성은 한 Issue에서 개발하되 모듈·테스트는 분리한다. 최신 요구사항과 기존 출력 계약은 유지한다.
- 로컬 미커밋 전처리·실험 작업은 백업·복원해 보존하고 문서 통합 커밋에 포함하지 않는다. 원격 문서 준비와 실제 코드 구현·게시·GPU 검증을 구분한다.
- #14는 #11로 통합한 이력으로 남기며 완료 구현으로 닫지 않는다.

## 2026-10-07 기존 전처리·실험 코드 게시 검증

- 기존 로컬 전처리 순수 모듈과 유사도 실험 소스를 #11 통합 Branch에 게시한다.
- 모델 없는 관련 테스트는 30 passed, 6 subtests passed. 실제 모델/GPU/네트워크 측정은 이번 검증에 포함하지 않았다.
- 현재 내부 품질 검사는 입력 quality_score를 사용하며 최종 qualityScore 산출·실제 입력 Adapter·전체 현재 문맥 검증은 후속 구현이다.
- 기존 Streamlit은 E5-small/BGE reranker 실험형이고 현재 MVP Qwen3 4B·FP16·Hybrid 6번 운영 구현과 구분한다.
- 범위·검증·남은 제한은 [부분 구현 게시 결과](features/learning-goal-content-relevance-evaluation-REPORT.md)를 따른다.

## 2026-10-07 학교 Ollama 테스트 지침

- 최신 이재빈 노션과 사용자 요청에 따라 Mac에서 개발하고 학교 Windows GPU PC에서 Ollama로 Qwen3 Embedding 4B·FP16 API를 먼저 테스트한다. 추후 WSL2·Linux·vLLM으로 전환하는 방향은 유지한다.
- [설치·테스트 지침](features/school-ollama-embedding-SETUP.md)에 학교 로컬 벡터·GPU 확인, 반복 요청 측정, Mac SSH 터널·왕복 측정을 분리해 작성했다.
- 이번 작업은 지침서 작성이다. 학교 PC 설치·GPU·API·SSH 실기 검증과 실제 프로젝트 Embedding Client 구현은 수행하지 않았다.

## 2026-10-07 통합 #11 실제 개발

- `ContentInput`·`VerifiedContext`·`ConfirmedGoal`을 분리하고 공통 `text`·`ocr` 입력을 기존 전처리에 연결했다. 검증 문맥은 호출자가 Server 검증 결과 또는 합성 Fixture로 제공하며 AI가 인증을 대신하지 않는다.
- 최종 qualityScore는 원천 품질 × 정상 문자 비율 × 정제 단위 유지 비율로 계산한다. DOM은 Client qualityScore를 최종값으로 신뢰하지 않고 OCR은 인식 confidence를 상한으로 사용한다. 산식·임계값은 실험 정책이다.
- PLAN 사례 기반 초기 목표 템플릿과 DIRECT/PREREQUISITE/SUPPORTING 역할별 Hybrid 판정, 대표 본문 범위 검사·적극적 무관 주제 근거를 구현했다. 템플릿·무관 주제 범위와 임계값의 독립 검수·보정은 남아 있다.
- 최초 Ollama Client는 `qwen3-embedding:4b-fp16`·manifest digest·F16·2560차원·embedding capability를 확인하고 `/api/embed`에 batch 요청한다. HTTP timeout·제한 재시도·동시 요청·입력/응답 크기 상한을 둔다.
- 원문 없는 유한 TTL/LRU 목표 벡터·결과·이벤트 캐시, 사용자·목표·run·정책·모델·Graph·전처리/추출기 분리, 중복 충돌과 응답 직전 재검증을 구현했다. 상태 변경·제외·민감·실패 시 캐시를 정리하며 Server는 종료·삭제 시 `clear_caches()`를 연결해야 한다.
- 합성 평가 CLI의 fake/real 모드와 목표군이 겹치지 않는 개발/평가 split을 구현했다. fake 결과는 로직 검증이며 모델 정확도·GPU 성능이 아니다. real은 digest가 없거나 연결이 실패하면 오류로 종료하며 fake로 전환하지 않는다.
- 공개 FastAPI·응답 envelope/confidence·Server 연동, 백엔드 9개 컬럼 합의, 최근 흐름/추가 LLM, vLLM 전환과 학교 실측은 미완료다. 실제 테스트 결과·명령은 [구현 리포트](features/learning-goal-content-relevance-evaluation-REPORT.md)를 따른다.

## 2026-10-11 제목·소제목·코드 입력 반영

- 기존 `pageTitle`의 임베딩을 실제 Hybrid 점수·충돌 검사·근거 ID에 연결했다. `text`에 명시된 Markdown h1~h3는 내부 `HEADING`으로 구분한다. 제목·소제목은 본문 커버리지에 포함하지 않으며 이 정보만으로 관련·무관을 확정하지 않는다.
- 코드 fence의 언어·설명·들여쓰기를 보존하고 CODE 문자열의 HTML entity를 임의로 변환하지 않는다. 개인정보 검사의 별도 decode는 유지한다.
- 기존 `appName`·`urlHost`로 결과 캐시를 구분한다. 목표 벡터는 재사용하며 앱·도메인을 임의 점수로 변환하지 않는다. 공개 입력·응답 컬럼은 추가하지 않았다.
- 전처리 `school-text-v2`, 분석기 `school-hybrid-v2`. 2026-10-14 전체 테스트 166 passed, 6 subtests passed. 실제 Qwen3 벡터 생성과 합성 평가를 확인했으며 정확도·GPU 자원 검증은 별도다.
- 창 제목·포커스·구조화 소제목/코드 정보의 실제 전달 계약은 추가 확인이 필요하다. 명시적인 구조가 없는 OCR·평문에서 소제목이나 코드 언어를 추측하지 않는다.
- 사용자 요청으로 Notion AI 최종 기획서 (2)의 7.1·8.2·8.3절, 이재빈 전처리 5·7절, 관련성 7.2·10·11절과 유사도 검색 6번 설명에 이 구현 범위를 반영했다. 수정한 4개 페이지를 다시 조회해 의도한 본문, 기존 JSON 예시와 하위 페이지 참조 보존을 확인했다. 제목·소제목 처리는 현재 로컬 실험 정책으로 표시했고 숫자 가중치·임계값이나 새 공개 컬럼을 확정하지 않았다.
- 학교 전달용 [단일 MD 지침](features/school-ollama-embedding-SETUP.md)을 현재 노션·코드 기준으로 개정했다. 제목·소제목·코드 입력, 임베딩 payload, 기존 점수/커버리지/충돌, 내부/공개 출력 경계와 Windows/Mac 실행 명령을 담았다. 11개 JSON 예시·내부 Schema·정제 순서·실제 payload·설정값을 검증하고 공개 요청/성공/실패 3개 예시가 현재 노션과 일치함을 확인했다. Windows·학교 GPU·SSH 실기 검증은 미실행이다.

## 2026-10-12 최신 16개 더미 데이터

- `AI/evaluation/relevance_cases.json`을 `synthetic-school-v2`로 갱신했다. 기존 JSON 평가 형식을 유지하고 개발 8개·평가 8개와 목표 템플릿 분리를 유지했다.
- 제목·소제목·본문 일치, 제목/본문 양방향 충돌, 소제목/본문 충돌, 제목·소제목만 있는 입력, 제목 없는 OCR, 코드 언어/entity 보존, 긴 제목·소제목의 커버리지 제외를 반영했다. 선수·보조, 무관 주제, 캡처 실패, PARTIAL, 제외·민감 사례도 포함한다.
- 기존 fake 벡터를 전처리 후 전체 단위 수·순서에 맞췄다. expected는 기획/보류 정책에 따른 초안이며 사람의 독립 검수가 필요하다. real 모드가 fake 벡터를 사용하지 않는 경계는 유지한다.
- fake 개발/평가 CLI 각각 8/8 기대 라벨 일치·종료 코드 0, 전체 166 passed·6 subtests passed. 충돌/본문 부족 사유, 근거 ID, 코드 entity와 분할 순서도 검증했다. 실제 Qwen3/GPU 정확도·속도를 측정한 결과가 아니다.
- 학교 MD의 데이터 목록·개정일을 함께 갱신했다. 운영 분석 코드·공개 API·Graph·숫자 정책은 변경하지 않았다.

## 2026-10-12 학교 모델 연결 확인

- 사용자 제공 API 형식은 현재 Ollama Client의 `/api/embed` 요청과 일치한다. Mac 코드 버전 `school-text-v2`·`school-hybrid-v2`와 SSH Client를 확인했다.
- 샌드박스의 최초 localhost 호출은 네트워크 권한으로 거부되어 연결 상태 근거로 사용하지 않았다. 허용된 제한 밖 호출에서 `127.0.0.1:11435/api/tags`는 ConnectionRefusedError(errno 61)를 반환했다. 모델 metadata·벡터·real Hybrid 실행은 아직 확인하지 못했다.
- 제공된 SSH 명령은 SCHOOL_USER·SCHOOL_HOST 예시이며 실제 접속 대상·포트가 없다. 실제 대상 정보를 요청했다. 비밀번호·개인키를 문서에 기록하지 않는다.
- 이후 사용자 제공 SCHOOL_LAN.md의 학교 내부 FastAPI `10.125.43.92:8000` 경로를 확인했다. 실제 Mac Wi-Fi IP는 문서의 허용 IP와 일치했고, 제한 밖 `/health` 요청은 연결 시도 5초 후 timeout을 반환했다. 라우트는 en0·학교 Wi-Fi 게이트웨이를 사용했다.
- 사용자가 학교 관리자 PowerShell의 Enable-MacAccess.ps1을 아직 실행하지 않았다고 확인했다. 이 선행 작업 후 `/health`·합성 `/api/embed` 검증을 재개한다. timeout만으로 Windows 방화벽과 학교 유무선망 차단 중 원인을 확정하지 않는다.
- 문서의 LAN 게이트웨이는 `/health`·`/api/embed`만 제공한다. 기존 Client는 원격 HTTP를 거부하고 Ollama metadata 경로를 요구하므로 해당 Client를 이 주소로 변경해 연결 완료로 표시하지 않았다. 실제 health 응답 확인 후 필요한 연동 경계를 검토한다.

## 2026-10-13 Tailscale 연결 준비

- 사용자가 Tailscale을 선택했고 학교 PC에서 직접 설치·로그인·Serve 실행을 진행할 수 있다고 확인했다. Mac에는 Tailscale 명령과 `/Applications/Tailscale.app`이 확인되지 않았다.
- 공식 `Tailscale-1.102.4-macos.pkg`를 다운로드했고 Homebrew cask의 SHA256 일치와 Tailscale Inc. 개발자 서명·Apple 공증을 확인했다. 설치·로그인·VPN 허용은 아직 완료하지 않았다. 설치 UI 접근은 Computer Use 권한 대기 상태다.
- 기존 학교 로컬 FastAPI를 private HTTPS Serve로 연결하는 절차를 학교 SETUP과 단일 전달 MD에 준비했다. 학교 HTTPS 주소를 받은 뒤 `/health`·실제 합성 벡터를 확인한다. 모델 연결·Hybrid 실측 완료로 표시하지 않는다.
- 현재 운영 Client는 수정하지 않았다. 학교 FastAPI의 모델 metadata 계약은 실제 응답 확인 후 연동해야 한다.

## 2026-10-14 학교 Qwen 실제 연결·합성 파이프라인 확인

- Mac에서 학교 PC의 Tailscale Serve HTTPS `/health`가 HTTP 200을 반환했다. 응답은 `qwen3-embedding:4b-fp16`, digest `3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc`, F16, 2560차원, Ollama 0.40.0을 보고했다.
- 실제 단일 텍스트 임베딩이 2560차원 유한 벡터로 반환됐고 L2 norm 1.0을 확인했다. 첫 요청 Ollama 내부 시간 6.238초(모델 로딩 6.176초), 후속 단일 요청 내부 시간 0.245~0.329초였다.
- 학교 FastAPI는 `/health`·`/api/embed`만 노출한다. 기존 `OllamaEmbeddingClient`/평가 CLI의 `/api/tags`·`/api/show`·`/api/version` 검증 계약과 맞지 않아, 변경 없는 운영 CLI 실행은 되지 않았다. 실제 평가에는 응답 메타데이터를 확인하고 `/api/embed`를 호출하는 일회성 메모리 Adapter를 사용했다. 저장소 코드는 변경하지 않았다.
- 일회성 Adapter로 development 합성 사례 8개를 실행했다. 7개 임베딩 배치 요청이 성공했고 재시도는 없었다. 분석된 6건 중 임시 기대 라벨 일치 3건, 불일치 3건이며 2건은 본문 부족·BLACK_SCREEN으로 모델 호출 전에 건너뛰었다. 기대 라벨은 사람 검수가 안 된 초안이므로 정확도 지표로 해석하지 않는다.
- 전체 개발 평가 경과시간은 58.39초였다. 요청 왕복시간은 중앙값 6.08초·95백분위 23.31초, 서버 Ollama 처리시간 중앙값 약 0.35초였다. 별도 웜 단일 요청 Mac 왕복은 2.619초·16.419초·5.704초, 서버 처리 0.245~0.329초였다. 측정 상세와 단계별 제한은 [구현 리포트](features/learning-goal-content-relevance-evaluation-REPORT.md)에 기록한다.
- `tailscale status`와 `tailscale ping`은 계속 `relay "tok"`/DERP 경로였다. 해당 성능 측정은 직접 연결이 아닌 릴레이 환경이며 GPU 사용률·VRAM은 수집하지 않았다.
- 다음 선행 작업은 학교 API의 승인된 접근 경로와 기존 Ollama Client metadata 계약을 일치시키는 것이다. 그 뒤 사람 검수 개발 데이터로 보정하고 설정을 동결해 독립 evaluation split을 실행한다. 공개 FastAPI·Server 연동과 MVP 완료는 아직 아니다.
