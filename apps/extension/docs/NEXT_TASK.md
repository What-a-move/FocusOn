# Extension 다음 작업

## 2026-10-19 화면 수집 모듈 후속 연결

- `public/capture/controller.js`에 현재 focused 창의 활성 탭 선택·단계 수집을 구현했다. `public/service-worker.js`가 이벤트 무효화와 신뢰된 Extension Message를 처리한다.
- 후속 3번에서 Server 최신 RUNNING 세션·제외 목록을 확인한 어댑터가 `screenAcquisition.setContext`를 호출한다. 정책이 없거나 60초 이내 lease가 만료되면 수집하지 않는다. lease는 로컬 안전장치이며 공개 API 컬럼으로 추가하지 않는다.
- `observeMetadata()` 결과를 Server에 제출하고 대응 결과가 허용된 UNCERTAIN일 때만 `collectNext(decision)`를 호출한다. `DWELL_PENDING`은 15초 조건을 충족한 새 호출로 처리한다. 본문·이미지는 로컬/오프라인 큐에 보관하지 않는다.
- Worker 재시작은 session storage로 복구한다. Browser 재시작/세션 복구 때 출처별 sequence의 Server 기준 동기화도 3번에서 연결한다.
- optional `<all_urls>` 승인 화면은 Popup의 OCR 권한 설정 링크로 연다. Chrome 내부 페이지·미검사 iframe·Incognito·정책 불명은 OCR로 우회하지 않는다.
- 실제 Chrome 권한·화면 수집·학교 모델 평가와 속도 측정은 사용자가 보류한 4번이다.

> Frontend(`apps`) 담당자는 Extension 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 현재 우선 작업

- 작업명: Extension 수집기를 Server 세션·제외 정책과 연결
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 우선순위: 높음
- 상태: 예정

## 작업 목적

구현된 현재 활성 탭의 메타데이터·DOM·OCR 단계 수집기를 Server의 유효한 세션과 제외 정책에 연결한다.

## 예상 작업 순서

1. Server에서 최신 RUNNING 세션과 제외 도메인을 조회하는 어댑터를 구현한다.
2. 정책 확인 전에는 수집하지 않고, 정책 유효 기간이 끝나면 수집을 중지한다.
3. 메타데이터 결과를 먼저 제출하고 허용된 UNCERTAIN 응답일 때만 DOM, 이어 OCR을 요청한다.
4. Server 응답 ID·sequence와 Worker 재시작·세션 복구 흐름을 연결한다.
5. Chrome 권한 허용·거부, 제외 도메인, DOM→OCR 분기와 수집 중단을 실제 브라우저에서 확인한다.

## 이후 작업 후보

- 15초 체류 후 페이지 재분석
- 현재 페이지 관련성 분석 요청
- 분석 제외 도메인 설정 UI
- 일회용 연결 코드 기반 Extension pairing
- Desktop 학습 세션 조회 및 타이머 표시
- Desktop 세션 일시정지·재개·종료 명령
- Desktop 연결 상태 표시
- Service Worker 재시작 후 상태 복구

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] 필요한 권한만 Manifest에 선언했다.
- [ ] Server 정책이 없거나 만료된 경우 수집하지 않는다.
- [ ] 메타데이터→DOM→OCR 순서와 제외 정책을 확인했다.
- [ ] Extension 재시작과 Server 연결 실패 흐름을 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `EXTENSION_ARCHITECTURE.md`를 확인했다.
- [ ] `MESSAGE_CONTRACT.md`에서 이번 기능에 필요한 Message와 Payload를 확인했다.
- [ ] `MANIFEST_PERMISSION_POLICY.md`의 Host 권한 제안을 사용자와 확정했다.
- [ ] `SERVICE_WORKER_LIFECYCLE.md`의 15초 체류·저장 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 현재 Branch가 Issue와 연결되어 있다.
