-- Additive upgrade; existing V13 history and original clocks are preserved.
-- PostgreSQL rolls back a failed migration transaction. Do not drop history for rollback.
ALTER TABLE analysis_runs
    ADD COLUMN observation_sequence BIGINT,
    ADD COLUMN current_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN ordering_untrusted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_analysis_observation_sequence
        CHECK (observation_sequence BETWEEN 1 AND 9007199254740991),
    ADD CONSTRAINT uq_analysis_observation_sequence
        UNIQUE (session_id, device_id, observation_sequence),
    ADD CONSTRAINT ck_analysis_untrusted_order
        CHECK (NOT ordering_untrusted OR (current_eligible AND observation_sequence IS NULL
            AND relation NOT IN ('RELATED', 'UNRELATED')));
CREATE INDEX ix_analysis_numbered_current
    ON analysis_runs (session_id, device_id, current_eligible, observation_sequence DESC, id DESC);
CREATE INDEX ix_analysis_uncertain_order
    ON analysis_runs (session_id, device_id, ordering_untrusted, id DESC);
CREATE INDEX ix_analysis_source_sequence
    ON analysis_runs (session_id, device_id, content_source, sequence DESC, id DESC);
