package com.capstone.backend.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** V16 데이터 보존과 실제 PostgreSQL의 NULL unique/CHECK/FK를 확인한다. */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class InterventionMigrationTests {
  @ParameterizedTest
  @ValueSource(strings = {"duplicateCause", "pendingSlot", "ackTimestamp", "slotState",
      "foreignKey", "type", "history", "noRaw"})
  void migrationPreservesAndConstrains(String scenario) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_intervention_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("16").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        fixture(connection);
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
        assertEquals(1234, number(connection,
            "SELECT accumulated_active_ms FROM learning_sessions WHERE id=1"));
        assertEquals(1, number(connection, "SELECT count(*) FROM information_schema.tables"
            + " WHERE table_schema='" + schema + "' AND table_name='session_extensions'"));
        String insert = "INSERT INTO intervention_events(session_id,type,status,cause,pending_slot,"
            + "channels_json,policy_version,running_since,created_at,expires_at)"
            + " VALUES(1,'OFF_TASK','PENDING',1,1,'[\"MOLE_UI\"]','v1',now(),now(),"
            + "now()+interval '30 second')";
        execute(connection, insert);
        if (scenario.equals("history")) {
          execute(connection, "UPDATE intervention_events SET status='EXPIRED',pending_slot=NULL");
          execute(connection, insert.replace("'PENDING',1,1", "'PENDING',2,1"));
          assertEquals(2, number(connection, "SELECT count(*) FROM intervention_events"));
        } else if (scenario.equals("noRaw")) {
          assertEquals(0, number(connection, "SELECT count(*) FROM information_schema.columns"
              + " WHERE table_schema='" + schema + "' AND table_name IN"
              + " ('intervention_events','intervention_policy_states') AND column_name"
              + " IN ('content_payload','url','title','token','ai_response')"));
        } else {
          String bad = switch (scenario) {
            case "duplicateCause" -> insert;
            case "pendingSlot" -> insert.replace("'PENDING',1,1", "'PENDING',2,1");
            case "ackTimestamp" -> "UPDATE intervention_events SET"
                + " status='ACKNOWLEDGED',pending_slot=NULL";
            case "slotState" -> "UPDATE intervention_events SET pending_slot=NULL";
            case "foreignKey" -> insert.replace("VALUES(1,", "VALUES(99999,");
            default -> insert.replace("OFF_TASK", "DISTRACTION_ALERT");
          };
          var error = assertThrows(SQLException.class, () -> execute(connection, bad));
          assertEquals(scenario.equals("duplicateCause") || scenario.equals("pendingSlot")
              ? "23505" : scenario.equals("foreignKey") ? "23503" : "23514", error.getSQLState());
        }
      } finally {
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
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
