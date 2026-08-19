package com.naztech.lending.organization.repository;

import com.naztech.lending.organization.domain.UserOrgUnit;
import com.naztech.lending.organization.domain.UserOrgUnitId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserOrgUnitRepository extends JpaRepository<UserOrgUnit, UserOrgUnitId> {

    /** The postings a person holds, with the unit itself, for display. */
    @EntityGraph(attributePaths = {"orgUnit", "orgUnit.unitType"})
    List<UserOrgUnit> findByUserId(UUID userId);

    /** Just the ids, for resolving what a person is allowed to see. */
    @Query("select p.orgUnit.id from UserOrgUnit p where p.user.id = :userId")
    List<UUID> findOrgUnitIds(@Param("userId") UUID userId);
}
