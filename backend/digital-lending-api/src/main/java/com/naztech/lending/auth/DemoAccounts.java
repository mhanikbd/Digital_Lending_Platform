package com.naztech.lending.auth;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The staff accounts a developer or a demonstrator signs in as.
 *
 * <p>One list, read by three things: the bootstrap that creates the accounts,
 * the organisation bootstrap that posts them to a unit, and the local-only
 * endpoint that offers them on the sign-in page. Three copies of the same roster
 * would drift, and the way you would find out is a card on the login page that
 * fills in a password nobody has.
 *
 * <p>The roster covers the whole six-step workflow, so a demonstrator can walk
 * one application from origination to disbursement by signing in as each role in
 * turn. It also makes the platform's own rules visible: the branch manager and
 * the relationship manager see different customers because their scopes differ,
 * and the field officer is missing permissions the others hold.
 *
 * <p>These passwords are published on the sign-in page. That is only ever
 * acceptable because every user of this class is gated on the {@code local}
 * profile - see {@link LocalAuthBootstrap} and {@link LocalDemoAccountController}.
 */
public final class DemoAccounts {

    /**
     * Deliberately uniform and deliberately obvious. A demonstrator reading it
     * off a card should not have to wonder whether they mistyped it, and a
     * password that looks like a real one is a password somebody eventually
     * reuses somewhere it matters.
     */
    public static final String PASSWORD = "Demo#Local1";

    private static final List<DemoAccount> ROSTER = List.of(
            new DemoAccount("EMP-10001", "Local Administrator", "ADMIN", "NRBC",
                    "Configures products, versions and rules. Sees the whole bank."),
            new DemoAccount("EMP-10002", "Nasima Haque", "BM", "BR-101",
                    "Branch Manager, Gulshan. Recommends the branch's files to head office."),
            new DemoAccount("EMP-10003", "Tanvir Rahman", "FO", "BR-102",
                    "Field Officer, Banani. Originates applications for one branch."),
            new DemoAccount("EMP-10004", "Sabrina Chowdhury", "RM", "RG-DHKN",
                    "Relationship Manager, Dhaka North. First delegated approval tier."),
            new DemoAccount("EMP-10005", "Imran Kabir", "CA", "PPC-01",
                    "Credit Analyst, head office. Analyses, queries and recommends."),
            new DemoAccount("EMP-10006", "Farhana Islam", "HOCRM", "DEP-CRM",
                    "Head of Credit Risk. Third approval tier, and may negotiate a rate."),
            new DemoAccount("EMP-10007", "Rezaul Karim", "SO", "BR-101",
                    "Sourcing Officer, Gulshan. Works the file and recommends it to the branch."),
            new DemoAccount("EMP-10008", "Shirin Akhter", "MIS", "NRBC",
                    "Head office MIS. Receives files from branches and allocates them to credit."),
            new DemoAccount("EMP-10009", "Kamal Uddin", "UH", "RG-DHKN",
                    "Unit Head, Dhaka North. Second delegated approval tier."),
            new DemoAccount("EMP-10010", "Nusrat Jahan", "CAD", "CAD-01",
                    "Credit Administration. Disburses approved loans."));

    private DemoAccounts() {
    }

    public static List<DemoAccount> roster() {
        return ROSTER;
    }

    /** The usernames, for deciding whether a database holds only demo accounts. */
    public static Set<String> usernames() {
        return ROSTER.stream().map(DemoAccount::username).collect(Collectors.toUnmodifiableSet());
    }

    public static Optional<DemoAccount> byUsername(String username) {
        return ROSTER.stream().filter(account -> account.username().equals(username)).findFirst();
    }

    /**
     * One demonstration account.
     *
     * @param username    the employee id signed in with
     * @param roleCode    the role granted, which must exist in {@code auth.t_role}
     * @param orgUnitCode the unit posted to, which decides what they can see
     * @param note        one line explaining why this account is on the list
     */
    public record DemoAccount(String username, String displayName, String roleCode,
                              String orgUnitCode, String note) {
    }
}
