package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.RoleScope;
import com.naztech.lending.auth.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * Case-insensitive lookup, matching the ux_user_type_username index so the
     * query uses it. An employee id typed in any case must find the same person.
     */
    @Query("select u from UserAccount u where u.userType = :userType and lower(u.username) = lower(:username)")
    Optional<UserAccount> findByTypeAndUsername(@Param("userType") UserType userType,
                                                @Param("username") String username);

    /**
     * The permission codes this person holds, by way of every active role
     * assigned to them. One query rather than walking user to roles to
     * permissions, because it runs on every sign-in and every refresh.
     */
    @Query("""
            select distinct p.code
            from UserAccount u
            join u.roles r
            join r.permissions p
            where u.id = :userId and r.status = 'ACTIVE'
            order by p.code
            """)
    List<String> findPermissionCodes(@Param("userId") UUID userId);

    /** The role codes this person holds. Carried for display and for audit. */
    @Query("""
            select r.code
            from UserAccount u
            join u.roles r
            where u.id = :userId and r.status = 'ACTIVE'
            order by r.code
            """)
    List<String> findRoleCodes(@Param("userId") UUID userId);

    /**
     * How far each role this person holds can see. Taken together they decide
     * whether the organisation is narrowed to their own postings, widened to
     * everything beneath them, or not narrowed at all.
     */
    @Query("""
            select distinct r.scopeLevel
            from UserAccount u
            join u.roles r
            where u.id = :userId and r.status = 'ACTIVE'
            """)
    List<RoleScope> findRoleScopes(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = "roles")
    List<UserAccount> findByUserTypeOrderByUsernameAsc(UserType userType);
}
