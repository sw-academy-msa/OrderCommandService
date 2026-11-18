package com.msa.order.domain.terms.repository;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 빈 DB의 전체 적용과 Flyway 통합 전 개발 DB의 기준점 처리를 실제 PostgreSQL로 검증합니다. */
class CheckoutTermsMigrationTest {
    @Test
    void freshDatabaseCreatesTablesAndSeedsTermsOnce() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            var migration = flyway(db).createSchemas(true).load();
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(3);
            assertThat(migration.migrate().migrationsExecuted).isZero();
            try (var connection =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var sql = connection.createStatement();
                    var row = sql.executeQuery("select version from orders.checkout_terms")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("checkout-v1");
            }
        }
    }

    // local 프로필과 같은 baseline 3으로 기존 개발 DB의 테이블과 행을 다시 만들거나 바꾸지 않습니다.
    @Test
    void existingDevelopmentDatabaseIsBaselinedWithoutRunningMigrations() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            try (var connection =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var sql = connection.createStatement()) {
                sql.execute("create schema orders");
                sql.execute("create table orders.existing_business_row(id integer primary key)");
                sql.execute("insert into orders.existing_business_row values (7)");
                var migration = flyway(db).baselineOnMigrate(true).baselineVersion("3").load();
                assertThat(migration.migrate().migrationsExecuted).isZero();
                assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("3");
                try (var row = sql.executeQuery("select id from orders.existing_business_row")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getInt(1)).isEqualTo(7);
                }
            }
        }
    }

    // application.yaml의 위치·스키마 설정과 같게 맞춥니다.
    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(
            PostgreSQLContainer db) {
        return Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas("orders")
                .defaultSchema("orders")
                .locations("classpath:db/migration")
                .cleanDisabled(true);
    }
}
