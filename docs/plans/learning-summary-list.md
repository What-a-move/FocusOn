## 목적과 범위
#29 저장된 READY 학습 정리를 Desktop이 목록으로 조회한다.

## 입출력 계약
GET /api/v1/learning-summaries?page=0 은 data 배열에 sessionId, goalSnapshot, endedAt을 반환한다. 페이지당 20건, 종료 시각 및 세션 ID 내림차순이다.

## 실패·예산 정책
인증 및 Desktop 권한을 검증한다. 소유자의 종료된 세션만 한 쿼리로 읽고 내용 원문은 노출하지 않는다. page는 0부터 10000까지다.

## 기계가 판별 가능한 완료 조건
server check 및 SessionApiTests에서 인증, 다른 사용자 격리, 미완료 정리 제외를 확인한다.
