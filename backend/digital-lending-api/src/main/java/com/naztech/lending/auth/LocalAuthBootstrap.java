package com.naztech.lending.auth;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one administrator on a developer machine so the portal has something
 * to sign in as.
 *
 * <p>Guarded by the local profile rather than seeded through a migration: a
 * migration runs everywhere, and a known credential must never reach an
 * environment a customer can read. User administration proper arrives with the
 * back-office screens in Milestone 35.
 *
 * <p>Runs on every start, not only the first. Creating the account and granting
 * it a role are separate concerns, and they were introduced one milestone apart -
 * so a developer whose database was created before roles existed has an account
 * that can authenticate and then do nothing. Repairing that here costs one query
 * and saves a confusing morning.
 */
@Component
@Profile("local")
public class LocalAuthBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAuthBootstrap.class);

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String displayName;

    public LocalAuthBootstrap(UserAccountRepository users,
                              RoleRepository roles,
                              UserCredentialRepository credentials,
                              PasswordEncoder passwordEncoder,
                              @Value("${dlp.auth.bootstrap.username:EMP-10001}") String username,
                              @Value("${dlp.auth.bootstrap.password:ChangeMe#Local1}") String password,
                              @Value("${dlp.auth.bootstrap.display-name:Local Administrator}")
                              String displayName) {
        this.users = users;
        this.roles = roles;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<UserAccount> existing = users.findByTypeAndUsername(UserType.BANK_USER, username);
        UserAccount user = existing.orElseGet(this::createBootstrapUser);
        if (user == null) {
            return;
        }
        grantAdminRole(user);
    }

    /**
     * Returns null when other staff accounts already exist: somebody has set
     * this environment up properly and it is not this runner's business to add
     * an account with a published password to it.
     */
    private UserAccount createBootstrapUser() {
        if (users.existsByUserType(UserType.BANK_USER)) {
            return null;
        }
        UserAccount user = users.save(new UserAccount(UserType.BANK_USER, username, displayName));
        credentials.save(new UserCredential(
                user, CredentialType.PASSWORD, passwordEncoder.encode(password)));

        // The username is safe to log. The password never is, so it is not.
        log.info("Seeded local bank user {} - password comes from dlp.auth.bootstrap.password",
                username);
        return user;
    }

    private void grantAdminRole(UserAccount user) {
        boolean alreadyGranted = user.getRoles().stream()
                .anyMatch(role -> ADMIN_ROLE.equals(role.getCode()));
        if (alreadyGranted) {
            return;
        }
        Optional<Role> admin = roles.findByCode(ADMIN_ROLE);
        if (admin.isEmpty()) {
            log.warn("Role {} is missing, so {} was left with no permissions", ADMIN_ROLE, username);
            return;
        }
        user.grant(admin.get());
        users.save(user);
        log.info("Granted {} the {} role", username, ADMIN_ROLE);
    }
}
