-- #318 재설계 1차: 세션 끝 한 줄 회고와 「별로였어」 사유를 세션당 하나 저장한다(다시 보내면 덮어쓰기).
-- 사유·메모는 BAD일 때만 있다. 메모는 사용자 글이라 100자 이내, 민감정보 검사를 통과한 것만 저장하고
-- 로그에 남기지 않는다. V33은 #317(session_pauses)이 쓴다. 이 마이그레이션은 #317 머지 뒤 머지한다.
-- 복구: 새 테이블만 추가하므로 `DROP TABLE session_retrospectives;` 뒤 flyway_schema_history에서
-- version 34 행을 지우면 된다(회고 기록만 사라진다).
CREATE TABLE session_retrospectives (
    session_id BIGINT PRIMARY KEY REFERENCES learning_sessions(id) ON DELETE CASCADE,
    rating VARCHAR(8) NOT NULL CHECK (rating IN ('GOOD','OKAY','BAD')),
    reason_codes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    note VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_session_retrospectives_bad_only CHECK
        (rating = 'BAD' OR (note IS NULL AND reason_codes_json = '[]'::jsonb))
);
