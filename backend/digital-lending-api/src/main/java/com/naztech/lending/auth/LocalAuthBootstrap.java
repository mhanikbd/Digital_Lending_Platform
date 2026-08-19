package com.naztech.lending.auth;

import com.naztech.lending.auth.domain.CredentialType;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserCredential;
import com.naztech.lending.auth.domain.UserType;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.auth.repository.UserCredentialRepository;
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
 * Creates one bank user on a developer machine so the portal has something to
 * sign in as.
 *
 * <p>Guarded by the local profile rather than seeded through a migration: a
 * migration runs everywhere, and a known credential must never reach an
 * environment a customer can read. User administration proper arrives with
 * Milestone 6.
 */
@Component
@Profile("local")
public class LocalAuthBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAuthBootstrap.class);

    private final UserAccountRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String displayName;

    public LocalAuthBootstrap(UserAccountRepository users,
                              UserCredentialRepository credentials,
                              PasswordEncoder passwordEncoder,
                              @Value("${dlp.auth.bootstrap.username:EMP-10001}") String username,
                              @Value("${dlp.auth.bootstrap.password:ChangeMe#Local1}") String password,
                              @Value("${dlp.auth.bootstrap.display-name:Local Administrator}")
                              String displayName) {
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByUserType(UserType.BANK_USER)) {
            return;
        }

        UserAccount user = users.save(new UserAccount(UserType.BANK_USER, username, displayName));
        credentials.save(new UserCredential(
                user, CredentialType.PASSWORD, passwordEncoder.encode(password)));

        // The username is safe to log; the password never is, so it is not.
        log.info("Seeded local bank user {} - password comes from dlp.auth.bootstrap.password", username);
    }
}
