## 목적과 범위
#73 집중 리포트에 실제 전송 확인된 화면 이탈 알림 횟수를 표시한다.

## 입출력 계약
기존 summary에 선택 필드 interventionCount를 반환한다. 현재 세션의 OFF_TASK 중 deliveryConfirmedAt이 기록된 이벤트를 한 번씩 센다.

## 실패·예산 정책
기존 소유권과 종료 상태 검증을 유지한다. 세션 단위 count 쿼리 1회만 추가한다.

## 기계가 판별 가능한 완료 조건
확정 전송만 집계하고 권한 거절 시 저장소를 호출하지 않는다. server 프로필과 SessionSummaryServiceTests로 검증한다.
