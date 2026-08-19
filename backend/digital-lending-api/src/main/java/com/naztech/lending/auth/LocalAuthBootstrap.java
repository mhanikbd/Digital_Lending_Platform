package com.naztech.lending.auth;

import com.naztech.lending.auth.LocalDemoDirectory.Credential;
import com.naztech.lending.auth.domain.CredentialType;
import com.naztech.lending.auth.domain.Role;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserCredential;
import com.naztech.lending.auth.domain.UserType;
import com.naztech.lending.auth.repository.RoleRepository;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.auth.repository.UserCredentialRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the demonstration staff accounts on a developer machine, so the portal
 * has something to sign in as and the scope rules have somebody to apply to.
 *
 * <p>Guarded by the local profile rather than seeded through a migration: a
 * migration runs everywhere, and a known credential must never reach an
 * environment a customer can read. User administration proper arrives with the
 * back-office screens in Milestone 35.
 *
 * <p>Runs on every start, not only the first, and tops up whatever is missing.
 * Creating an account, granting it a role and posting it to a unit were
 * introduced milestones apart, so a database created earlier has accounts that
 * can authenticate and then do nothing. Repairing that here costs a few queries
 * and saves a confusing morning.
 *
 * <p>Ordered first. Without an explicit order this ran at lowest precedence -
 * that is, last - so the organisation bootstrap looked for accounts that did not
 * exist yet and posted none of them. The symptom was subtle: a second start
 * repaired it, so it only ever looked wrong on a fresh database.
 */
@Component
@Profile("local")
@Order(10)
public class LocalAuthBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAuthBootstrap.class);

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final LocalDemoDirectory directory;

    public LocalAuthBootstrap(UserAccountRepository users,
                              RoleRepository roles,
                              UserCredentialRepository credentials,
                              PasswordEncoder passwordEncoder,
                              LocalDemoDirectory directory) {
        this.users = users;
        this.roles = roles;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.directory = directory;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (holdsRealStaff()) {
            log.info("Staff accounts already exist that are not demonstration accounts, "
                    + "so none were seeded");
            return;
        }
        for (Credential credential : directory.all()) {
            seed(credential);
        }
    }

    /**
     * True when somebody has set this environment up properly.
     *
     * <p>The check is on the names rather than on the count. An earlier version
     * refused as soon as any bank user existed, which meant a database created
     * before the roster grew could never be topped up - the one account it had
     * blocked the other five forever.
     */
    private boolean holdsRealStaff() {
        return users.findByUserTypeOrderByUsernameAsc(UserType.BANK_USER).stream()
                .map(UserAccount::getUsername)
                .anyMatch(username -> !DemoAccounts.usernames().contains(username));
    }

    private void seed(Credential credential) {
        String username = credential.account().username();
        UserAccount user = users.findByTypeAndUsername(UserType.BANK_USER, username)
                .orElseGet(() -> create(credential));
        grant(user, credential.account().roleCode());
    }

    private UserAccount create(Credential credential) {
        UserAccount user = users.save(new UserAccount(
                UserType.BANK_USER,
                credential.account().username(),
                credential.account().displayName()));

        this.credentials.save(new UserCredential(
                user, CredentialType.PASSWORD, passwordEncoder.encode(credential.password())));

        // The username is safe to log. The password never is, so it is not -
        // publishing it on the sign-in page of a local machine is one decision,
        // writing it into a log file that gets pasted into a ticket is another.
        log.info("Seeded local bank user {} ({})",
                credential.account().username(), credential.account().roleCode());
        return user;
    }

    private void grant(UserAccount user, String roleCode) {
        boolean alreadyGranted = user.getRoles().stream()
                .anyMatch(role -> roleCode.equals(role.getCode()));
        if (alreadyGranted) {
            return;
        }
        Optional<Role> role = roles.findByCode(roleCode);
        if (role.isEmpty()) {
            log.warn("Role {} is missing, so {} was left with no permissions",
                    roleCode, user.getUsername());
            return;
        }
        user.grant(role.get());
        users.save(user);
        log.info("Granted {} the {} role", user.getUsername(), roleCode);
    }
}
