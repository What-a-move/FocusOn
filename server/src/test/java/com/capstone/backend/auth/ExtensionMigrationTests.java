package com.capstone.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 전용 임시 schema에서 V10 기존 연결을 준비하고 실제 Flyway 업그레이드를 검증한다. */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class ExtensionMigrationTests {
  /** 최신 연결이 인증 불가이면 이전 유효 연결을 보존하고 모두 불가일 때만 최신을 유지한다. */
  @ParameterizedTest
  @ValueSource(strings = {"expiredExtension", "inactiveDesktop", "expiredParent", "revokedParent",
      "missingDesktop", "missingFamily", "bothValid", "noneUsable", "deletedUser"})
  void preservesUsableConnectionBeforeConnectionRecency(String scenario) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    String user = System.getenv("SPRING_DATASOURCE_USERNAME");
    String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String schema = "qa_extension_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .target("10").load().migrate();
        execute(connection, "SET search_path TO " + schema);
        fixture(connection);
        switch (scenario) {
          case "expiredExtension" -> execute(connection,
              "UPDATE token_families SET expires_at=now()-interval '1 day' WHERE id=2");
          case "inactiveDesktop" -> execute(connection,
              "UPDATE user_devices SET status='LOGGED_OUT' WHERE id=2");
          case "expiredParent" -> execute(connection,
              "UPDATE token_families SET expires_at=now()-interval '1 day' WHERE id=4");
          case "revokedParent" -> execute(connection,
              "UPDATE token_families SET status='REVOKED' WHERE id=4");
          case "missingDesktop" -> execute(connection,
              "UPDATE user_devices SET linked_desktop_device_id=NULL WHERE id=4");
          case "missingFamily" -> {
            execute(connection, "DELETE FROM refresh_token_records WHERE token_family_id=2");
            execute(connection, "DELETE FROM token_families WHERE id=2");
          }
          case "noneUsable" -> execute(connection,
              "UPDATE token_families SET expires_at=now()-interval '1 day' WHERE user_id=1");
          case "deletedUser" -> execute(connection,
              "UPDATE users SET deleted_at=now() WHERE id=1");
          default -> { /* bothValid: 둘 다 유효하면 최신 연결을 유지한다. */ }
        }
        final long familyCount = number(connection, "SELECT count(*) FROM token_families");
        final long tokenCount = number(connection, "SELECT count(*) FROM refresh_token_records");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .load().migrate();
        long expected = scenario.equals("bothValid") || scenario.equals("noneUsable")
            || scenario.equals("deletedUser") ? 4 : 3;
        assertEquals(expected, number(connection, "SELECT id FROM user_devices WHERE user_id=1"
            + " AND device_type='CHROME_EXTENSION' AND status='ACTIVE'"));
        assertEquals(6, number(connection, "SELECT count(*) FROM user_devices"));
        assertEquals(familyCount, number(connection, "SELECT count(*) FROM token_families"));
        assertEquals(tokenCount, number(connection, "SELECT count(*) FROM refresh_token_records"));
        assertEquals(1, number(connection,
            "SELECT count(*) FROM user_devices WHERE id=6 AND status='ACTIVE'"));
        long loser = expected == 3 ? 4 : 3;
        assertEquals(0, number(connection, "SELECT count(*) FROM token_families WHERE device_id="
            + loser + " AND status='ACTIVE'"));
        assertEquals(0, number(connection, "SELECT count(*) FROM refresh_token_records r JOIN"
            + " token_families f ON f.id=r.token_family_id WHERE f.device_id=" + loser
            + " AND r.status='ACTIVE'"));
      } finally {
        // 생성한 무작위 QA schema만 정리하며 기존 public schema에는 접근하지 않는다.
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  private void execute(Connection connection, String sql) throws Exception {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private long number(Connection connection, String sql) throws Exception {
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  /** A는 오래된 유효 연결, B는 최신 연결이며 다른 계정의 연결도 함께 준비한다. */
  private void fixture(Connection connection) throws Exception {
    execute(connection, """
        INSERT INTO users(id) OVERRIDING SYSTEM VALUE VALUES(1),(2);
        INSERT INTO user_devices(id,user_id,device_type,installation_id_hash,display_name,
          linked_desktop_device_id) OVERRIDING SYSTEM VALUE VALUES
          (1,1,'DESKTOP',repeat('a',64),'QA A',NULL),
          (2,1,'DESKTOP',repeat('b',64),'QA B',NULL),
          (3,1,'CHROME_EXTENSION',repeat('c',64),'QA A',1),
          (4,1,'CHROME_EXTENSION',repeat('d',64),'QA B',2),
          (5,2,'DESKTOP',repeat('e',64),'QA Other',NULL),
          (6,2,'CHROME_EXTENSION',repeat('f',64),'QA Other',5);
        INSERT INTO token_families(id,user_id,device_id,created_at,expires_at)
          OVERRIDING SYSTEM VALUE VALUES
          (1,1,3,now()-interval '20 days',now()+interval '30 days'),
          (2,1,4,now()-interval '10 days',now()+interval '30 days'),
          (3,1,1,now()-interval '25 days',now()+interval '30 days'),
          (4,1,2,now()-interval '25 days',now()+interval '30 days'),
          (5,2,6,now()-interval '20 days',now()+interval '30 days'),
          (6,2,5,now()-interval '25 days',now()+interval '30 days');
        INSERT INTO refresh_token_records(token_family_id,token_hash,expires_at) VALUES
          (1,repeat('1',64),now()+interval '30 days'),
          (2,repeat('2',64),now()+interval '30 days'),
          (5,repeat('3',64),now()+interval '30 days');
        """);
  }
}
