package com.capstone.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 고유 임시 schema에서 실제 V20 종료 기록의 V21 업그레이드 보존을 확인한다. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class ReportMigrationTests {
  @Autowired private DataSource dataSource;

  @Test
  void indexUpgradePreservesExistingSessionSnapshot() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assumeTrue(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
    }
    var schema = "report208_" + UUID.randomUUID().toString().replace("-", "");
    var jdbc = new JdbcTemplate(dataSource);
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .target("20").load().migrate();
      long user = jdbc.queryForObject("insert into " + schema + ".users "
          + "(onboarding_status,user_time_zone,created_at,updated_at) "
          + "values ('INCOMPLETE','Asia/Seoul',now(),now()) returning id", Long.class);
      long device = jdbc.queryForObject("insert into " + schema + ".user_devices "
          + "(user_id,device_type,installation_id_hash,display_name,status,created_at) "
          + "values (?,'DESKTOP',?,'검증','ACTIVE',now()) returning id", Long.class,
          user, "a".repeat(64));
      long goal = jdbc.queryForObject("insert into " + schema + ".goals "
          + "(user_id,goal_text,confirmation_source,status,created_at,updated_at) "
          + "values (?,'보존 목표','MANUAL_CONFIRMED','ACTIVE',now(),now()) returning id",
          Long.class, user);
      jdbc.update("insert into " + schema + ".learning_sessions "
          + "(user_id,goal_id,runner_device_id,goal_text_snapshot,status,version,"
          + "target_duration_ms,accumulated_active_ms,accumulated_at_resume_ms,"
          + "user_time_zone_snapshot,study_date,session_options_snapshot_json,"
          + "intervention_policy_version,started_at,ended_at,created_at) "
          + "values (?,?,?,'보존 목표','ENDED',0,300000,60000,0,'Asia/Seoul',"
          + "'2026-11-01','{}','v1',now(),now(),now())", user, goal, device);
      String sql = "select row_to_json(s)::text from " + schema + ".learning_sessions s";
      var before = jdbc.queryForList(sql, String.class);
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .target("21").load().migrate();
      assertThat(jdbc.queryForList(sql, String.class)).containsExactlyElementsOf(before);
      assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname=? "
          + "and indexname='ix_learning_sessions_user_study_date'", Integer.class, schema))
          .isEqualTo(1);
    } finally {
      jdbc.execute("drop schema if exists " + schema + " cascade");
    }
  }
}
