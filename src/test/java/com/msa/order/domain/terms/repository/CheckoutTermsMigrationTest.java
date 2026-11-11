package com.msa.order.domain.terms.repository;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 이미 업무 테이블이 있는 DB에 약관만 추가해도 기존 데이터를 보존하는지 실제 PostgreSQL로 검증합니다. */
class CheckoutTermsMigrationTest {
    @Test
    void explicitlyBaselinesNonemptySchemaAtZeroWithoutChangingExistingBusinessRows()
            throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            try (var connection =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var sql = connection.createStatement()) {
                sql.execute("create schema orders");
                sql.execute("create table orders.existing_business_row(id integer primary key)");
                sql.execute("insert into orders.existing_business_row values (7)");
                var migration =
                        Flyway.configure()
                                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                                .schemas("orders")
                                .defaultSchema("orders")
                                .createSchemas(false)
                                .table("checkout_terms_schema_history")
                                .locations("classpath:db/checkout-terms")
                                .baselineOnMigrate(true)
                                .baselineVersion("0")
                                .cleanDisabled(true)
                                .load();
                assertThat(migration.migrate().migrationsExecuted).isEqualTo(2);
                assertThat(migration.migrate().migrationsExecuted).isZero();
                try (var row = sql.executeQuery("select id from orders.existing_business_row")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getInt(1)).isEqualTo(7);
                }
                try (var row = sql.executeQuery("select version from orders.checkout_terms")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo("checkout-v1");
                }
            }
        }
    }
}
