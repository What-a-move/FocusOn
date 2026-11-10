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

/** V19 데이터와 checksum을 보존하면서 V20 새 쓰기 검사가 적용되는지 확인한다. */
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
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
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
