package com.naztech.lending.organization;

import com.naztech.lending.auth.DemoAccounts;
import com.naztech.lending.auth.DemoAccounts.DemoAccount;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserType;
import com.naztech.lending.auth.repository.UserAccountRepository;
import com.naztech.lending.organization.domain.OrgUnit;
import com.naztech.lending.organization.domain.OrgUnitType;
import com.naztech.lending.organization.domain.UserOrgUnit;
import com.naztech.lending.organization.domain.UserOrgUnitId;
import com.naztech.lending.organization.repository.OrgUnitRepository;
import com.naztech.lending.organization.repository.OrgUnitTypeRepository;
import com.naztech.lending.organization.repository.UserOrgUnitRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A small but realistic hierarchy on a developer machine, so the scope rules
 * have something to be right or wrong about.
 *
 * <p>Local profile only, and for the same reason as the seeded administrator: a
 * migration runs everywhere, and an invented branch reaching a real bank's
 * database would be worse than useless. A bank configures its own units.
 *
 * <p>Ordered after the auth bootstrap, because it posts the account that one
 * creates.
 */
@Component
@Profile("local")
@Order(20)
public class LocalOrganizationBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalOrganizationBootstrap.class);

    private static final String ROOT_CODE = "NRBC";

    private final OrgUnitRepository units;
    private final OrgUnitTypeRepository unitTypes;
    private final UserOrgUnitRepository postings;
    private final UserAccountRepository users;

    public LocalOrganizationBootstrap(OrgUnitRepository units,
                                      OrgUnitTypeRepository unitTypes,
                                      UserOrgUnitRepository postings,
                                      UserAccountRepository users) {
        this.units = units;
        this.unitTypes = unitTypes;
        this.postings = postings;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (units.findByCode(ROOT_CODE).isEmpty()) {
            seedHierarchy();
        }
        postDemoAccounts();
    }

    private void seedHierarchy() {
        OrgUnit bank = save("BANK", null, ROOT_CODE, "NRB Commercial Bank PLC", null, null);

        OrgUnit dhaka = save("ZONE", bank, "ZN-DHK", "Dhaka Zone", null, null);
        OrgUnit north = save("REGION", dhaka, "RG-DHKN", "Dhaka North Region", null, null);
        OrgUnit south = save("REGION", dhaka, "RG-DHKS", "Dhaka South Region", null, null);

        save("BRANCH", north, "BR-101", "Gulshan Branch", "Dhaka", "Dhaka");
        save("BRANCH", north, "BR-102", "Banani Branch", "Dhaka", "Dhaka");
        save("BRANCH", south, "BR-201", "Motijheel Branch", "Dhaka", "Dhaka");

        OrgUnit chattogram = save("ZONE", bank, "ZN-CTG", "Chattogram Zone", null, null);
        OrgUnit ctgRegion = save("REGION", chattogram, "RG-CTG", "Chattogram Region", null, null);
        save("BRANCH", ctgRegion, "BR-301", "Agrabad Branch", "Chattogram", "Chattogram");

        // Head office functions hang off the bank, not off a zone.
        save("DEPARTMENT", bank, "DEP-CRM", "Credit Risk Management", "Dhaka", "Dhaka");
        save("PPC", bank, "PPC-01", "Personal Processing Centre", "Dhaka", "Dhaka");
        save("CAD", bank, "CAD-01", "Credit Administration Department", "Dhaka", "Dhaka");

        log.info("Seeded a local organisation of {} units under {}", units.count(), ROOT_CODE);
    }

    /**
     * Posts each demonstration account to the unit its role implies.
     *
     * <p>This is what makes the scope rules demonstrable rather than merely
     * implemented. The branch manager is posted to a branch and the relationship
     * manager to a region, so signing in as one and then the other shows the
     * customer list actually narrowing. The administrator is posted to the bank
     * itself: their role is head-office scoped so the posting decides nothing,
     * but an account posted nowhere looks broken on the scope screen.
     *
     * <p>Silent when an account or a unit is absent. A developer who has renamed
     * a branch should get a missing posting, not a failed startup.
     */
    private void postDemoAccounts() {
        for (DemoAccount account : DemoAccounts.roster()) {
            Optional<UserAccount> user =
                    users.findByTypeAndUsername(UserType.BANK_USER, account.username());
            Optional<OrgUnit> unit = units.findByCode(account.orgUnitCode());
            if (user.isEmpty() || unit.isEmpty()) {
                continue;
            }
            UserOrgUnitId id = new UserOrgUnitId(user.get().getId(), unit.get().getId());
            if (postings.existsById(id)) {
                continue;
            }
            postings.save(new UserOrgUnit(user.get(), unit.get(), true));
            log.info("Posted {} to {}", account.username(), account.orgUnitCode());
        }
    }

    private OrgUnit save(String typeCode, OrgUnit parent, String code, String name,
                         String city, String district) {
        OrgUnitType type = unitTypes.findById(typeCode).orElseThrow(
                () -> new IllegalStateException("Unit type " + typeCode + " is missing; V4 did not run"));
        OrgUnit unit = new OrgUnit(type, parent, code, name);
        if (city != null) {
            unit.describeContact(null, city, district);
        }
        return units.save(unit);
    }
}
