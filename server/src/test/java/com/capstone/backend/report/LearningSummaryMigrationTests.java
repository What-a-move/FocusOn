package com.capstone.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** V19 데이터와 checksum 보존, V20 새 쓰기 검사, V23 정리 대기열·근거 제약을 확인한다. */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class LearningSummaryMigrationTests {
  @Test
  void preservesLegacyReadyAndRejectsBlankNewWrites() throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_summary_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("19").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        fixture(connection);
        execute(connection, "INSERT INTO learning_summaries(session_id,summary_status,overview,"
            + "next_start_prompt,key_concepts) VALUES(1,'READY',E'\\t\\n','valid',"
            + "'[{\"name\":\"concept\",\"briefExplanation\":\"valid\"}]')");
        String before = value(connection, "SELECT row_to_json(s)::text FROM learning_summaries s");
        String checksum = value(connection,
            "SELECT checksum::text FROM flyway_schema_history WHERE version='19'");
        // V20의 기존 행 보존만 검증한다. 이후 migration(V23)이 추가한 컬럼은 별도 테스트에서 본다.
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("20").load().migrate();
        assertEquals(before,
            value(connection, "SELECT row_to_json(s)::text FROM learning_summaries s"));
        assertEquals(checksum, value(connection,
            "SELECT checksum::text FROM flyway_schema_history WHERE version='19'"));
        assertEquals(1234, number(connection,
            "SELECT accumulated_active_ms FROM learning_sessions WHERE id=1"));
        assertEquals("false", value(connection, "SELECT convalidated::text FROM pg_constraint "
            + "WHERE conname='ck_learning_summary_nonblank' AND connamespace='"
            + schema + "'::regnamespace"));
        execute(connection, "UPDATE learning_summaries SET overview='valid'");
        for (String blank : new String[] {" ", "\t", "\n", "\r\n", "\u00a0", "\ufeff", "\u2007"}) {
          for (String field : new String[] {"overview", "next_start_prompt", "name",
              "briefExplanation"}) {
            String query = field.equals("overview") || field.equals("next_start_prompt")
                ? "UPDATE learning_summaries SET " + field + "=?"
                : "UPDATE learning_summaries SET key_concepts=jsonb_set(key_concepts,'{0,"
                    + field + "}',to_jsonb(CAST(? AS text)))";
            try (var statement = connection.prepareStatement(query)) {
              statement.setString(1, blank);
              assertThrows(SQLException.class, statement::executeUpdate);
            }
          }
        }
      } finally {
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  /** V23이 기존 대기 행을 재처리 가능하게 채우고 종료 행과 세션 기록은 바꾸지 않는지 확인한다. */
  @Test
  void pipelineMigrationBackfillsPendingRowsAndGuardsEvidence() throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_pipeline_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("22").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        execute(connection, """
            INSERT INTO users(id) VALUES(1);
            INSERT INTO user_devices(id,user_id,device_type,installation_id_hash,display_name)
                VALUES(1,1,'DESKTOP',repeat('a',64),'QA');
            INSERT INTO goals(id,user_id,goal_text,confirmation_source,status,created_at,updated_at)
                VALUES(1,1,'QA goal','MANUAL_CONFIRMED','ACTIVE',now(),now());
            INSERT INTO learning_sessions(id,user_id,goal_id,runner_device_id,goal_text_snapshot,
                status,version,target_duration_ms,accumulated_active_ms,user_time_zone_snapshot,
                study_date,session_options_snapshot_json,intervention_policy_version,
                started_at,ended_at,end_reason,created_at)
                SELECT n,1,1,1,'QA goal','ENDED',1,1800000,1234,'Asia/Seoul',CURRENT_DATE,'{}',
                    'v1',TIMESTAMPTZ '2026-11-01 00:00:00Z',
                    TIMESTAMPTZ '2026-11-01 00:30:00Z','USER_ACTION',now()
                FROM generate_series(1,3) n;
            INSERT INTO learning_summaries(session_id,summary_status) VALUES(1,'QUEUED');
            INSERT INTO learning_summaries(session_id,summary_status) VALUES(2,'GENERATING');
            INSERT INTO learning_summaries(session_id,summary_status,overview,next_start_prompt,
                key_concepts) VALUES(3,'READY','요약','다음',
                '[{"name":"개념","briefExplanation":"설명"}]');
            """);
        final String sessionsBefore = value(connection,
            "SELECT json_agg(s ORDER BY id)::text FROM learning_sessions s");
        final String readyBefore = value(connection, "SELECT overview || next_start_prompt "
            + "|| key_concepts::text FROM learning_summaries WHERE session_id=3");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
        for (int id = 1; id <= 2; id++) {
          assertEquals("QUEUED|2026-11-01 00:30:30+00|2026-11-01 00:30:30+00|0", value(connection,
              "SELECT summary_status || '|' || (input_closes_at AT TIME ZONE 'UTC')::text "
                  + "|| '+00|' || (next_attempt_at AT TIME ZONE 'UTC')::text || '+00|' "
                  + "|| attempt_count FROM learning_summaries WHERE session_id=" + id));
        }
        assertEquals("READY|true", value(connection, "SELECT summary_status || '|' "
            + "|| (next_attempt_at IS NULL AND input_closes_at IS NULL)::text "
            + "FROM learning_summaries WHERE session_id=3"));
        assertEquals(readyBefore, value(connection, "SELECT overview || next_start_prompt "
            + "|| key_concepts::text FROM learning_summaries WHERE session_id=3"));
        assertEquals(sessionsBefore,
            value(connection, "SELECT json_agg(s ORDER BY id)::text FROM learning_sessions s"));
        for (String invalid : new String[] {
            "UPDATE learning_summaries SET next_attempt_at=NULL WHERE session_id=1",
            "UPDATE learning_summaries SET attempt_count=4 WHERE session_id=1",
            "UPDATE learning_summaries SET summary_status='GENERATING' WHERE session_id=1",
            "UPDATE learning_summaries SET next_attempt_at=now() WHERE session_id=3"}) {
          assertThrows(SQLException.class, () -> execute(connection, invalid), invalid);
        }
        execute(connection, "INSERT INTO analysis_runs(id,session_id,device_id,client_run_id,"
            + "content_source,observed_at,received_at,sequence,relation,relation_confidence,"
            + "analysis_status,policy_version,created_at) VALUES(1,1,1,'" + UUID.randomUUID()
            + "','CHROME_DOM',now(),now(),1,'RELATED',0.9,'COMPLETED','v1',now())");
        execute(connection, evidence("격리 수준", "a".repeat(64), "0.9"));
        for (String invalid : new String[] {
            evidence("다른 개념", "a".repeat(64), "0.9"), evidence("개념", "A".repeat(64), "0.9"),
            evidence(" ", "b".repeat(64), "0.9"), evidence("가".repeat(121), "c".repeat(64), "0.9"),
            evidence("개념", "d".repeat(64), "1.5")}) {
          assertThrows(SQLException.class, () -> execute(connection, invalid), invalid);
        }
      } finally {
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  private static String evidence(String concept, String hash, String confidence) {
    return "INSERT INTO learning_review_evidences(session_id,analysis_run_id,concept,"
        + "evidence_hash,observed_at,relation_confidence,created_at) VALUES(1,1,'" + concept
        + "','" + hash + "',now()," + confidence + ",now())";
  }

  private static String value(Connection connection, String query) throws SQLException {
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
      rows.next();
      return rows.getString(1);
    }
  }

  private static void fixture(Connection connection) throws SQLException {
    execute(connection, """
            INSERT INTO users(id) VALUES(1);
            INSERT INTO user_devices(id,user_id,device_type,installation_id_hash,display_name)
                VALUES(1,1,'DESKTOP',repeat('a',64),'QA');
            INSERT INTO goals(id,user_id,goal_text,confirmation_source,status,created_at,updated_at)
                VALUES(1,1,'QA goal','MANUAL_CONFIRMED','ACTIVE',now(),now());
            INSERT INTO learning_sessions(id,user_id,goal_id,runner_device_id,goal_text_snapshot,
                status,version,target_duration_ms,accumulated_active_ms,user_time_zone_snapshot,
                study_date,session_options_snapshot_json,intervention_policy_version,
                started_at,last_resumed_at,created_at)
                VALUES(1,1,1,1,'QA goal','RUNNING',0,1800000,1234,'Asia/Seoul',CURRENT_DATE,
                    '{}','v1',now()-interval '1 minute',now()-interval '1 minute',now());
            """);
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static long number(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }
}
