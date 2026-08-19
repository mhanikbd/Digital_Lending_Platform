package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserType;
import java.util.Optional;
import java.util.UUID;
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

    boolean existsByUserType(UserType userType);
}
