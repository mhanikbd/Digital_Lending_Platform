package com.naztech.lending.auth;

import com.naztech.lending.auth.domain.Role;
import com.naztech.lending.auth.dto.DemoAccountResponse;
import com.naztech.lending.auth.repository.RoleRepository;
import com.naztech.lending.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demonstration accounts, with their passwords.
 *
 * <p>Yes, really. On a developer machine that is the point: a demonstrator
 * should be able to sign in as a branch manager, see one branch's customers,
 * sign out, sign in as the regional manager and see four - without keeping a
 * list of credentials in a chat message.
 *
 * <p>Three things make that acceptable, and all three have to hold:
 *
 * <ol>
 *   <li>The bean exists only under the {@code local} profile. In any other
 *       environment this class is not instantiated, the path is not permitted by
 *       {@link LocalPublicEndpoints}, and the request 404s.
 *   <li>The accounts themselves exist only under that profile - see
 *       {@link LocalAuthBootstrap} - so there is nothing here to sign in to
 *       elsewhere.
 *   <li>The bootstrap refuses to seed at all if the database already holds staff
 *       accounts that are not on the roster, so pointing a local build at a real
 *       database does not add a published credential to it.
 * </ol>
 *
 * <p>If any of those is ever relaxed, this endpoint has to go with it.
 */
@RestController
@RequestMapping(LocalDemoAccountController.PATH)
@Profile("local")
@Tag(name = "Demo accounts", description = "Local development only")
public class LocalDemoAccountController {

    static final String PATH = "/api/v1/auth/demo-accounts";

    private final LocalDemoDirectory directory;
    private final RoleRepository roles;

    public LocalDemoAccountController(LocalDemoDirectory directory, RoleRepository roles) {
        this.directory = directory;
        this.roles = roles;
    }

    @GetMapping
    @Operation(summary = "The seeded demonstration accounts",
            description = "Username, password, role and posting for each account the local "
                    + "bootstrap creates. Exists only under the local profile: outside it this "
                    + "endpoint is not registered and the accounts do not exist.")
    public ApiResponse<List<DemoAccountResponse>> list() {
        return ApiResponse.success(directory.all().stream()
                .map(credential -> DemoAccountResponse.of(
                        credential.account(),
                        credential.password(),
                        roles.findByCode(credential.account().roleCode()).orElse(null)))
                .toList());
    }
}
