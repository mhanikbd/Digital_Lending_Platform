package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.CredentialType;
import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByUserAndCredentialType(UserAccount user, CredentialType credentialType);
}
