package com.naztech.lending.auth;

import com.naztech.lending.security.PublicEndpoints;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Opens the demonstration account listing, on a developer machine only.
 *
 * <p>It has to be reachable before anybody has signed in - it is what the
 * sign-in page reads to offer the accounts - so it cannot be behind
 * authentication. Being a bean at all is conditional on the local profile, so
 * outside that profile the path is neither permitted nor served.
 */
@Component
@Profile("local")
public class LocalPublicEndpoints implements PublicEndpoints {

    @Override
    public List<String> paths() {
        return List.of(LocalDemoAccountController.PATH);
    }

    @Override
    public String reason() {
        return "local demonstration accounts, offered on the sign-in page";
    }
}
