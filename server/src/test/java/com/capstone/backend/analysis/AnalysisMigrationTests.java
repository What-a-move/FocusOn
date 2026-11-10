package com.capstone.backend.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 운영 DB 대신 임시 schema에서 V13→V14→V15과 실제 PostgreSQL 제약을 검증한다. */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class AnalysisMigrationTests {
  @ParameterizedTest
  @ValueSource(strings = {"duplicate", "source", "sequence", "confidence", "foreignKey",
      "guard", "relation", "noRawColumns", "crossSourceOrder", "globalDuplicate", "globalInvalid",
      "unsafeOrderingFlag", "untrustedRelation"})
  void preservesSessionAndEnforcesResultConstraints(String scenario) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_analysis_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("13").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        fixture(connection);
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
        assertEquals(1234, number(connection,
            "SELECT accumulated_active_ms FROM learning_sessions WHERE id=1"));
        String insert = runInsert();
        execute(connection, insert);
        if (scenario.equals("noRawColumns")) {
          assertEquals(0, number(connection, "SELECT count(*) FROM information_schema.columns"
              + " WHERE table_schema='" + schema + "' AND table_name='analysis_runs'"
              + " AND column_name NOT IN ('id','session_id','device_id','client_run_id',"
              + "'content_source','observed_at','received_at','sequence','relation',"
              + "'relation_confidence','analysis_status','reason_code','error_code',"
              + "'model_version','prompt_version','policy_version','created_at',"
              + "'observation_sequence','current_eligible','ordering_untrusted',"
              + "'observation_context_id')"));
        } else if (scenario.equals("crossSourceOrder")) {
          execute(connection, insert.replace("RELATED',0.9,'COMPLETED'",
              "EXCLUDED',NULL,'SKIPPED'").replace("CHROME_DOM", "APP_META")
              .replace("now(),now(),1", "now()+interval '1 second',now(),2")
              .replaceFirst("[0-9a-f]{8}-[0-9a-f-]{27,}", UUID.randomUUID().toString()));
          execute(connection, "UPDATE analysis_runs SET relation='UNRELATED' WHERE sequence=1");
          assertEquals(2, number(connection, "SELECT sequence FROM analysis_runs"
              + " WHERE session_id=1 AND device_id=1 ORDER BY observed_at DESC,id DESC LIMIT 1"));
        } else {
          String bad = switch (scenario) {
            case "duplicate" -> insert;
            case "globalDuplicate" -> {
              execute(connection, "UPDATE analysis_runs SET observation_sequence=5");
              yield insert.replace("analysis_runs(", "analysis_runs(observation_sequence,")
                  .replace("VALUES(", "VALUES(5,");
            }
            case "globalInvalid" -> insert
                .replace("analysis_runs(", "analysis_runs(observation_sequence,")
                .replace("VALUES(", "VALUES(0,");
            case "unsafeOrderingFlag" -> "UPDATE analysis_runs SET current_eligible=false,"
                + "ordering_untrusted=true,relation='UNCERTAIN'";
            case "untrustedRelation" -> "UPDATE analysis_runs SET ordering_untrusted=true";
            case "source" -> insert.replace("CHROME_DOM", "CAMERA_LOCAL");
            case "sequence" -> insert.replace("now(),now(),1", "now(),now(),0");
            case "confidence" -> insert.replace("0.9", "1.1");
            case "foreignKey" -> insert.replace("VALUES(1,1,", "VALUES(99999,1,");
            case "guard" -> insert.replace("RELATED", "EXCLUDED");
            default -> insert.replace("RELATED", "UNKNOWN");
          };
          // 중복 외 시나리오는 별도 UUID로 목적한 CHECK/FK 실패를 확인한다.
          if (!scenario.equals("duplicate")) {
            bad = bad.replaceFirst("[0-9a-f]{8}-[0-9a-f-]{27,}", UUID.randomUUID().toString());
          }
          final String sql = bad;
          var error = assertThrows(SQLException.class, () -> execute(connection, sql));
          assertEquals(scenario.equals("duplicate") || scenario.equals("globalDuplicate") ? "23505"
              : scenario.equals("foreignKey") ? "23503" : "23514", error.getSQLState());
        }
      } finally {
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  @Test
  void v14HistorySurvivesObservationOrderUpgrade() throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_analysis_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("14").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        fixture(connection);
        execute(connection, runInsert().replace("now(),now(),1",
            "now()+interval '1 hour',now(),1"));
        long observed = number(connection, "SELECT extract(epoch from observed_at)::bigint"
            + " FROM analysis_runs");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
        assertEquals(1, number(connection, "SELECT count(*) FROM analysis_runs"
            + " WHERE observation_sequence IS NULL AND current_eligible"
            + " AND NOT ordering_untrusted"));
        assertEquals(observed, number(connection, "SELECT extract(epoch from observed_at)::bigint"
            + " FROM analysis_runs"));
        assertEquals(1234, number(connection,
            "SELECT accumulated_active_ms FROM learning_sessions"));
      } finally {
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  private static String runInsert() {
    return "INSERT INTO analysis_runs(session_id,device_id,client_run_id,"
        + "content_source,"
        + "observed_at,received_at,sequence,relation,relation_confidence,analysis_status,"
        + "policy_version,created_at) VALUES(1,1,'" + UUID.randomUUID()
        + "','CHROME_DOM',now(),now(),1,'RELATED',0.9,'COMPLETED','v1',now())";
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
