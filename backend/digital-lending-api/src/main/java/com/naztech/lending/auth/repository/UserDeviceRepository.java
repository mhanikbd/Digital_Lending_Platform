package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.UserAccount;
import com.naztech.lending.auth.domain.UserDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByUserAndDeviceId(UserAccount user, String deviceId);

    List<UserDevice> findByUser(UserAccount user);
}
