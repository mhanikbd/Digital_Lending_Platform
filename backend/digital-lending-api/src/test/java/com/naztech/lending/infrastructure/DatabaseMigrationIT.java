package com.naztech.lending.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Exactly what each milestone has been allowed to create so far.
     *
     * <p>This began as an assertion that no schema held any table at all, which
     * was right while only V1 existed and became wrong the moment authentication
     * landed. Listing the tables instead keeps the original point - a table
     * appears when the module that needs it does, not before - while staying
     * true. A new table means adding a line here deliberately, which is exactly
     * the moment to ask whether it is speculative.
     */
    private static final Map<String, Set<String>> EXPECTED_TABLES = Map.of(
            "auth", Set.of(
                    "t_user", "t_user_credential", "t_device", "t_session", "t_login_history",
                    "t_permission", "t_role", "t_role_permission", "t_user_role"),
            "organization", Set.of(
                    "t_org_unit_type", "t_org_unit", "t_user_org_unit"),
            "customer", Set.of(
                    "t_customer", "t_customer_address", "t_customer_identification"),
            "product", Set.of(
                    "t_loan_product", "t_loan_product_version", "t_product_tenure",
                    "t_product_fee", "t_product_risk_limit"),
            "rules", Set.of(
                    "t_rule_attribute", "t_rule_group", "t_rule",
                    "t_rule_evaluation", "t_rule_evaluation_detail"));

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
    void recordsEveryMigrationAsApplied() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success = false", Integer.class);
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success = true", Integer.class);
        String firstVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM public.flyway_schema_history WHERE installed_rank = 1", String.class);

        assertThat(failed).isZero();
        assertThat(applied).isPositive();
        assertThat(firstVersion).isEqualTo("1");
    }

    @Test
    void eachSchemaHoldsExactlyTheTablesItsMilestoneCreated() {
        for (Map.Entry<String, Set<String>> expected : EXPECTED_TABLES.entrySet()) {
            List<String> actual = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                    String.class, expected.getKey());

            assertThat(actual)
                    .as("tables in schema %s", expected.getKey())
                    .containsExactlyInAnyOrderElementsOf(expected.getValue());
        }
    }

    @Test
    void schemasWhoseMilestoneHasNotArrivedAreStillEmpty() {
        List<String> notYetBuilt = EXPECTED_SCHEMAS.stream()
                .filter(schema -> !EXPECTED_TABLES.containsKey(schema))
                .toList();

        List<String> holdingTables = jdbcTemplate.queryForList(
                "SELECT DISTINCT table_schema FROM information_schema.tables", String.class);

        // The original point of this test, kept: a table for a module nobody has
        // built yet is a speculative table.
        assertThat(holdingTables).doesNotContainAnyElementsOf(notYetBuilt);
    }
}
