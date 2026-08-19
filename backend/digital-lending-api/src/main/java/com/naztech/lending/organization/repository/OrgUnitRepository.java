package com.naztech.lending.organization.repository;

import com.naztech.lending.organization.domain.OrgUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    Optional<OrgUnit> findByCode(String code);

    /**
     * The whole tree, with each unit's type and parent already loaded. The tree
     * is small - a bank has hundreds of branches, not millions - so one query
     * beats a lazy walk that issues one per node.
     */
    @EntityGraph(attributePaths = {"unitType", "parent"})
    List<OrgUnit> findAllByOrderByCodeAsc();

    /**
     * Every unit at or beneath the given ones.
     *
     * <p>A recursive query rather than a stored path column: a path has to be
     * rewritten for an entire subtree whenever a unit is moved, and a path that
     * drifts out of step with parent_id is a bug nobody notices until an
     * approval goes to the wrong region. PostgreSQL walks this in milliseconds
     * at the size a bank's hierarchy actually reaches.
     */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM organization.t_org_unit WHERE id IN (:rootIds)
                UNION ALL
                SELECT child.id
                FROM organization.t_org_unit child
                JOIN subtree ON child.parent_id = subtree.id
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<UUID> findSubtreeIds(@Param("rootIds") Collection<UUID> rootIds);
}
