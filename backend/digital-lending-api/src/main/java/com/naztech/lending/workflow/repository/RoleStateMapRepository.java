package com.naztech.lending.workflow.repository;

import com.naztech.lending.workflow.domain.RoleStateMap;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleStateMapRepository extends JpaRepository<RoleStateMap, UUID> {

    /**
     * What the roles a person holds allow them to do in one state.
     *
     * <p>Several roles at once, because a person may hold more than one and the
     * grants add up: holding an extra role must never take an action away.
     */
    List<RoleStateMap> findByStateCodeAndRoleCodeInAndStatus(
            String stateCode, Collection<String> roleCodes, String status);

    /** Every grant, for a configuration screen. */
    List<RoleStateMap> findAllByOrderByStateCodeAscRoleCodeAsc();
}
