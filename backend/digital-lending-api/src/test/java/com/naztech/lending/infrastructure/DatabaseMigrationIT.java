package com.naztech.lending.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves that the schema can be built from nothing by Flyway alone, which is the
 * only supported way to change this database.
 */
class DatabaseMigrationIT extends IntegrationTestBase {

    /** The module boundaries declared in the architecture, mirrored in the database. */
    private static final List<String> EXPECTED_SCHEMAS = List.of(
            "account", "application", "approval", "audit", "auth", "collection", "credit",
            "customer", "document", "integration", "kyc", "loan", "notification",
            "organization", "product", "repayment", "rules", "workflow");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsEveryModuleSchemaOnACleanDatabase() {
        List<String> actual = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name",
                String.class);

        assertThat(actual).containsAll(EXPECTED_SCHEMAS);
    }

    @Test
    void everySchemaIsDocumented() {
        List<String> undocumented = jdbcTemplate.queryForList(
                "SELECT nspname FROM pg_namespace "
                        + "WHERE obj_description(oid, 'pg_namespace') IS NULL",
                String.class);

        assertThat(undocumented).doesNotContainAnyElementsOf(EXPECTED_SCHEMAS);
    }

    @Test
    void recordsTheBaselineMigrationAsApplied() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success = true", Integer.class);
        String firstVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM public.flyway_schema_history WHERE installed_rank = 1", String.class);

        assertThat(successfulMigrations).isPositive();
        assertThat(firstVersion).isEqualTo("1");
    }

    @Test
    void leavesBusinessTableCreationToLaterMilestones() {
        List<String> schemasAlreadyHoldingTables = jdbcTemplate.queryForList(
                "SELECT DISTINCT table_schema FROM information_schema.tables", String.class);

        assertThat(schemasAlreadyHoldingTables).doesNotContainAnyElementsOf(EXPECTED_SCHEMAS);
    }
}
