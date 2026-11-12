# Desktop 기능 기획서

## 기본 정보

- 기능명: 카메라 분석 (Apple Vision 로컬 이탈 감지)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-11-12
- 우선순위: MVP - 핵심 (선택 동의)
- 관련 Issue: #222
- Branch: `feat/222-desktop-camera-analysis`
- 정본: Notion `카메라 분석` 설계서 (3·4·5·6·7·8장)

## 기능 목적

- 화면 분석만으로는 모르는 "자리를 비움·고개를 오래 돌림"을 알아차리게 한다. 사용자가 카메라 분석을 켠 세션에서만 동작한다.

## Desktop 상세 동작

- Swift 도우미(`native/observer`) `camera` 명령
  - AVFoundation 시스템 기본 카메라(640×480)를 켜 두고 가장 최근 프레임 하나만 메모리에 둔다. `cameraStop`이면 스트림과 프레임을 버린다.
  - Apple Vision `VNDetectFaceLandmarksRequest`로 얼굴 수·두 눈 랜드마크·머리 yaw/pitch를 본다.
  - 밖으로는 `faceCount`·`eyesVisible`·`gaze(SCREEN/AWAY/UNKNOWN)`·`quality(GOOD/LOW/UNUSABLE)`만 보낸다. 프레임·좌표·랜드마크는 내보내지 않는다.
  - 판정: 밝기 평균 40 미만 → UNUSABLE / 얼굴 없음(밝음) → AWAY / 얼굴 2개 이상 → UNUSABLE / 얼굴이 작거나 신뢰도 낮음 → LOW / 머리 yaw·pitch 0.44rad(약 25도) 초과 → AWAY / 정면인데 눈이 안 보임 → UNKNOWN / 그 밖 → SCREEN.
- Main `CameraMonitor` (`camera-monitor.cts`), 2초마다 1회
  - 유효한(GOOD) AWAY가 끊기지 않고 60초 → `CAMERA_ALERT_CANDIDATE`, 90초 → `CAMERA_AWAY_CONFIRMED`.
  - SCREEN 복귀·LOW·UNUSABLE·UNKNOWN·켜는 중·도우미 실패는 누적을 지운다(불확실한 신호를 AWAY로 바꾸지 않음).
  - 같은 화면(key)의 최신 화면 판정이 RELATED이면 누적을 지우고 알리지 않는다. PRIVACY_BLOCKED이면 카메라를 끈다.
  - 한 이탈 동안 알림 1회, 카메라 알림 사이 5분.
- 켜는 조건: 세션 RUNNING + 세션 옵션 `cameraAnalysisEnabled` + macOS 카메라 권한 허용 + 지금 화면이 PRIVACY_BLOCKED 아님. 하나라도 아니면 바로 `cameraStop`.
- 알림: 확정되면 화면 위 포키를 `interventionId: local-camera`, 강도별 이탈 문구로 띄운다. 이미 포키가 떠 있으면 미룬다.
  - 서버 알림이 아니라 확인했어·닫기는 기록하지 않는다. 잠시 쉬기는 세션을 멈춘다.
- 로그: 상태가 바뀔 때만 `state.transition`(component `camera`), 알림은 `notify.fire`(`CAMERA_AWAY`). 얼굴·시선 값은 남기지 않는다.

## 화면 및 사용자 경험

- 새 화면 없음. 기존 포키 알림 창·문구를 그대로 쓴다. 카메라 사용 여부는 집중 준비 화면에 이미 표시된다.

## 데이터 및 IPC/API

- 서버로 보내는 카메라 데이터 없음. 서버 카메라 이벤트(설계서 8.1)는 API가 없어 서버 팀 제안으로 남긴다.

## 완료 조건 및 결과 (2026-11-12)

- 단위 테스트 5개: 60·90초, 초기화 5종, RELATED 억제·포키 대기, 5분 cooldown, 응답 검증.
- 실제 앱(로컬 서버, 세션 5): 자리 비움 10:53:18 → 60초 후보 10:54:18 → 90초 확정·포키 10:54:48 → 복귀 시 ATTENTION. 일시정지 즉시 `CAMERA_PAUSED`로 해제.

## 남은 일·한계

- 앉아 있을 때 고개를 숙이면(노트·키보드) 가끔 AWAY가 섞인다. 단발이라 누적은 지워지지만, 아래를 90초 넘게 보면 알림이 뜰 수 있다. 사용자 테스트로 각도(`HEAD_AWAY_RAD`)와 pitch 기준을 조정한다.
- 시선(눈동자)은 보지 않고 머리 방향으로만 추정한다.
- 카메라 오류 사유를 설정 화면에 보여 주기(설계서 7.3), 서버 카메라 이벤트 기록은 후속.
