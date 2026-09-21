# Desktop 기능 문서

새 기능을 시작할 때 PLAN을 만들고 기능이 완료되면 REPORT를 한 번 만든다. 개발 중 실제 오류가 발생하면 오류마다 ERROR 문서를 추가한다.

```text
study-session-timer-PLAN.md
study-session-timer-permission-denied-ERROR.md
study-session-timer-sync-timeout-ERROR.md
study-session-timer-REPORT.md
```

- 오류가 없으면 ERROR 문서를 만들지 않는다.
- 단순 버그 수정에는 REPORT를 만들지 않는다.
- 기능명과 오류명은 소문자 kebab-case를 사용한다.
- PLAN과 REPORT는 기능 단위, ERROR는 오류 단위로 관리한다.
