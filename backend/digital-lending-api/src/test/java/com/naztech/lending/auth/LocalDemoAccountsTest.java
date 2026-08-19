package com.naztech.lending.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.naztech.lending.auth.DemoAccounts.DemoAccount;
import com.naztech.lending.security.PublicEndpoints;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Profile;

/**
 * The demonstration roster, and the guards that make publishing its passwords
 * acceptable.
 *
 * <p>The first test is the important one. Everything else here is a convenience;
 * that one is a security invariant. A class that stops being local-profile-only
 * is a class that ships a known credential to an environment somebody else can
 * reach, and the failure would be silent - the endpoint would simply start
 * answering.
 */
class LocalDemoAccountsTest {

    /**
     * Every class that knows a demo password, or opens the path that serves
     * them, must be guarded by the local profile.
     *
     * <p>Named explicitly rather than discovered by scanning the package: a new
     * class that needs the guard should fail to be listed here by a person
     * thinking about it, not pass by being invisible to a scan.
     */
    @ParameterizedTest
    @ValueSource(classes = {
            DemoAccounts.class,
            LocalDemoDirectory.class,
            LocalDemoAccountController.class,
            LocalPublicEndpoints.class,
            LocalAuthBootstrap.class,
    })
    void nothingThatKnowsADemoPasswordEscapesTheLocalProfile(Class<?> guarded) {
        if (guarded == DemoAccounts.class) {
            // The roster itself is inert data with no Spring lifecycle. What it
            // must not do is be reachable through a bean that is unguarded,
            // which the rest of this list covers.
            assertThat(guarded.getAnnotation(Profile.class)).isNull();
            return;
        }

        Profile profile = guarded.getAnnotation(Profile.class);

        assertThat(profile)
                .as("%s must be annotated @Profile(\"local\")", guarded.getSimpleName())
                .isNotNull();
        assertThat(profile.value()).containsExactly("local");
    }

    @Test
    void theRosterCoversBranchRegionAndHeadOfficeSoScopeIsDemonstrable() {
        // The point of having a roster rather than one account. If they all sat
        // at head office, signing in as each would prove nothing.
        List<String> units = DemoAccounts.roster().stream().map(DemoAccount::orgUnitCode).toList();

        assertThat(units).contains("BR-101", "BR-102", "RG-DHKN", "NRBC");
    }

    @Test
    void theRosterCoversEveryStepOfTheWorkflow() {
        // A demonstrator has to be able to walk one application from origination
        // to disbursement. A roster missing the sourcing officer strands every
        // file in the first state, which is exactly what happened before these
        // four were added.
        List<String> roles = DemoAccounts.roster().stream().map(DemoAccount::roleCode).toList();

        assertThat(roles)
                .as("origination")
                .contains("FO", "SO")
                .as("branch approval")
                .contains("BM")
                .as("head office and credit")
                .contains("MIS", "CA")
                .as("delegated approval")
                .contains("RM", "UH", "HOCRM")
                .as("disbursement")
                .contains("CAD");
    }

    @Test
    void everyAccountIsDistinctAndExplainsItself() {
        List<DemoAccount> roster = DemoAccounts.roster();

        assertThat(roster).extracting(DemoAccount::username).doesNotHaveDuplicates();
        assertThat(roster).allSatisfy(account -> {
            assertThat(account.displayName()).isNotBlank();
            assertThat(account.roleCode()).isNotBlank();
            assertThat(account.orgUnitCode()).isNotBlank();
            // The note is what a demonstrator reads on hover to know which card
            // to press. An account without one is an account nobody picks.
            assertThat(account.note()).isNotBlank();
        });
    }

    @Test
    void usernamesMatchTheRosterExactly() {
        // The bootstrap decides whether a database holds only demo accounts by
        // comparing against this set. If it drifted from the roster, seeding
        // would either refuse forever or top up a real environment.
        assertThat(DemoAccounts.usernames())
                .containsExactlyInAnyOrderElementsOf(
                        DemoAccounts.roster().stream().map(DemoAccount::username).toList());
    }

    @Test
    void theAdministratorKeepsItsConfiguredPasswordAndTheRestShareOne() {
        LocalDemoDirectory directory = new LocalDemoDirectory("EMP-10001", "SomethingElse#1");

        assertThat(directory.all())
                .filteredOn(credential -> credential.account().username().equals("EMP-10001"))
                .singleElement()
                .satisfies(admin -> assertThat(admin.password()).isEqualTo("SomethingElse#1"));

        assertThat(directory.all())
                .filteredOn(credential -> !credential.account().username().equals("EMP-10001"))
                .allSatisfy(other ->
                        assertThat(other.password()).isEqualTo(DemoAccounts.PASSWORD));
    }

    @Test
    void aRenamedAdministratorLeavesEverybodyOnTheSharedPassword() {
        // dlp.auth.bootstrap.username is configurable. Pointing it at a name
        // that is not on the roster must not quietly hand somebody else the
        // configured password.
        LocalDemoDirectory directory = new LocalDemoDirectory("EMP-99999", "SomethingElse#1");

        assertThat(directory.all()).allSatisfy(credential ->
                assertThat(credential.password()).isEqualTo(DemoAccounts.PASSWORD));
    }

    @Test
    void theOpenedPathIsTheOneTheControllerServes() {
        PublicEndpoints endpoints = new LocalPublicEndpoints();

        assertThat(endpoints.paths()).containsExactly("/api/v1/auth/demo-accounts");
        assertThat(endpoints.reason()).isNotBlank();
    }
}
