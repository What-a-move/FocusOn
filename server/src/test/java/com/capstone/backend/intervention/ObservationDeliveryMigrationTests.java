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

/** 이미 존재하는 V17 개입 이력에서 V18 적용·참조·전송 slot 제약을 확인한다. */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class ObservationDeliveryMigrationTests {
  @ParameterizedTest
  @ValueSource(strings = {"history", "contextForeignKey", "contextShape", "deliveryNonce",
      "sentSlot", "deliveryForeignKey"})
  void additiveMigrationPreservesHistoryAndConstraints(String scenario) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_context_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("17").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        fixture(connection);
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
        assertEquals(1234, number(connection,
            "SELECT accumulated_active_ms FROM learning_sessions WHERE id=1"));
        assertEquals(1, number(connection,
            "SELECT count(*) FROM intervention_events WHERE id=1 AND status='PENDING'"
                + " AND observation_context_id IS NULL AND delivery_confirmed_at IS NULL"));
        String delivery = "INSERT INTO intervention_deliveries(intervention_id,client_delivery_id,"
            + "dispatch_status,channels_json,accepted_at,cooldown_until) VALUES(1,'"
            + UUID.randomUUID() + "','SENT','[\"MOLE_UI\"]',now(),now()+interval '5 minutes')";
        if (scenario.equals("history")) {
          assertEquals(0, number(connection, "SELECT count(*) FROM session_observation_contexts"));
        } else if (scenario.equals("sentSlot")) {
          execute(connection, "UPDATE intervention_events SET delivery_confirmed_at=now(),"
              + "pending_slot=NULL WHERE id=1");
          assertEquals(1, number(connection, "SELECT count(*) FROM intervention_events"
              + " WHERE status='PENDING' AND pending_slot IS NULL"));
          var error = assertThrows(SQLException.class, () -> execute(connection,
              "UPDATE intervention_events SET pending_slot=1 WHERE id=1"));
          assertEquals("23514", error.getSQLState());
        } else if (scenario.equals("deliveryNonce")) {
          execute(connection, delivery);
          var error = assertThrows(SQLException.class, () -> execute(connection, delivery));
          assertEquals("23505", error.getSQLState());
        } else {
          String bad = switch (scenario) {
            case "contextForeignKey" -> "UPDATE intervention_events"
                + " SET observation_context_id=99999 WHERE id=1";
            case "contextShape" -> "INSERT INTO session_observation_contexts(session_id,"
                + "runner_device_id,observer,client_context_id,foreground_sequence,running_since,"
                + "created_at,valid_until) VALUES(1,1,'DESKTOP','" + UUID.randomUUID()
                + "',1,now(),now(),now()+interval '30 seconds')";
            default -> delivery.replace("VALUES(1,", "VALUES(99999,");
          };
          var error = assertThrows(SQLException.class, () -> execute(connection, bad));
          assertEquals(scenario.equals("contextShape") ? "23514" : "23503", error.getSQLState());
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
            INSERT INTO intervention_events(session_id,type,status,cause,pending_slot,
                channels_json,policy_version,running_since,created_at,expires_at)
                VALUES(1,'OFF_TASK','PENDING',1,1,'["MOLE_UI"]','v1',now(),now(),
                    now()+interval '30 seconds');
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
