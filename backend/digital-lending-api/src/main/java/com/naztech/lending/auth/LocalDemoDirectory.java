package com.naztech.lending.auth;

import com.naztech.lending.auth.DemoAccounts.DemoAccount;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The demonstration roster with its passwords resolved.
 *
 * <p>One bean, and the only place that decides what a demo account's password
 * is. The bootstrap that creates the accounts and the endpoint that publishes
 * them both read it, so a card on the sign-in page cannot offer a password the
 * account was never given.
 *
 * <p>The administrator keeps its configured password, because
 * {@code dlp.auth.bootstrap.password} is documented and a developer may already
 * have set it. Everyone else shares one obvious password; there is nothing to
 * protect on a machine whose sign-in page lists the credentials.
 *
 * <p>Local profile only. Nothing here exists in any other environment, which is
 * what makes publishing a password acceptable at all.
 */
@Component
@Profile("local")
public class LocalDemoDirectory {

    private final List<Credential> credentials;

    public LocalDemoDirectory(
            @Value("${dlp.auth.bootstrap.username:EMP-10001}") String adminUsername,
            @Value("${dlp.auth.bootstrap.password:ChangeMe#Local1}") String adminPassword) {

        this.credentials = DemoAccounts.roster().stream()
                .map(account -> new Credential(
                        account,
                        account.username().equals(adminUsername)
                                ? adminPassword : DemoAccounts.PASSWORD))
                .toList();
    }

    public List<Credential> all() {
        return credentials;
    }

    /** One account and the password it was actually seeded with. */
    public record Credential(DemoAccount account, String password) {
    }
}
