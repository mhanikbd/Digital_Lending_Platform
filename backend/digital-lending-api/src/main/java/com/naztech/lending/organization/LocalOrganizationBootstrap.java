package com.naztech.lending.organization;

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
import org.springframework.beans.factory.annotation.Value;
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
    private final String adminUsername;

    public LocalOrganizationBootstrap(OrgUnitRepository units,
                                      OrgUnitTypeRepository unitTypes,
                                      UserOrgUnitRepository postings,
                                      UserAccountRepository users,
                                      @Value("${dlp.auth.bootstrap.username:EMP-10001}")
                                      String adminUsername) {
        this.units = units;
        this.unitTypes = unitTypes;
        this.postings = postings;
        this.users = users;
        this.adminUsername = adminUsername;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        OrgUnit bank = units.findByCode(ROOT_CODE).orElse(null);
        if (bank == null) {
            bank = seedHierarchy();
        }
        postAdministrator(bank);
    }

    private OrgUnit seedHierarchy() {
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
        return bank;
    }

    /**
     * Posts the seeded administrator to the bank itself. Their role is head
     * office scoped, so the posting decides nothing about what they can see -
     * but an account posted nowhere looks broken on the scope screen, and this
     * is the account a developer signs in as.
     */
    private void postAdministrator(OrgUnit bank) {
        Optional<UserAccount> admin = users.findByTypeAndUsername(UserType.BANK_USER, adminUsername);
        if (admin.isEmpty()) {
            return;
        }
        UserOrgUnitId id = new UserOrgUnitId(admin.get().getId(), bank.getId());
        if (postings.existsById(id)) {
            return;
        }
        postings.save(new UserOrgUnit(admin.get(), bank, true));
        log.info("Posted {} to {}", adminUsername, bank.getCode());
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
