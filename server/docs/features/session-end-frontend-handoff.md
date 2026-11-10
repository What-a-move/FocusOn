# Desktop 담당 전달: 종료 시 최종 누적값 필수 (2026-11-05 사용자 승인)

PR #164 / Issue #163. 기존 BETA-API-HANDOFF의 end 본문 없음 계약을 대체한다.

```http
POST /api/v1/sessions/{sessionId}/end
Authorization: Bearer <accessToken>
Idempotency-Key: <명령 UUID>
If-Match-Version: <현재 version>
Content-Type: application/json

{ "accumulatedActiveMs": 1800000 }
```

- 종료 버튼을 누른 시점의 Desktop monotonic 누적값을 정수 ms로 고정해 보낸다. 종료 요청 재시도에는 최초 값·키·버전을 재사용하며 시간이 지났다는 이유로 본문을 바꾸지 않는다.
- 응답 전·실패 때 로컬 누적값을 잃지 않도록 종료 당시 값을 보존한다. 성공한 응답의 accumulatedActiveMs만 최종값으로 적용한다.
- RUNNING: 서버 저장 누적값 이상, 현재 실행 구간 시작 누적값 + 서버 경과시간 +30초 이내로 검증한다. 이전 pause 구간을 포함해 전체 누적값을 보낸다.
- PAUSED·AUTO_PAUSED·AWAITING_END_CONFIRMATION: 마지막 서버 확정 누적값과 같은 값을 보낸다. APP_RESTART 복구는 서버 확정값을 기준으로 한다.
- 본문 없음·필드 누락/null·음수·소수·너무 큰 값·감소·과다 증가는400 VALIDATION_ERROR다. DB 상태·종료 시각·누적값·버전을 변경하지 않는다. 실패를 종료 성공으로 표시하지 않는다.
- 동일 키로 최종 누적값을 변경하면409 IDEMPOTENCY_CONFLICT다. 버전 충돌은 기존 최신 상태 조회·재조정 규칙을 따른다.
- 응답 이름은 그대로: data.status, version, accumulatedActiveMs, targetDurationMs, endedAt. X-Device-Id는 불필요하다.
- session-runtime.cts의 end 명령에서 activeMs()를 body에 전달하는 변경이 필요하다. 현재 백엔드 작업에서 Frontend 코드는 수정하지 않았다.
- 실제 연동 QA: 30분 상당 구간 종료→응답/화면/DB가1800000ms, pause→resume 후 종료→앞 구간 포함, 응답 유실 뒤 재시도→동일값, 본문 누락/과다값→기록 유지.
- Notion end 항목 Request·오류 속성 저장 확인: https://app.notion.com/p/7f7c6ba7e1394e00b8ad2d89d804f148
