package com.naztech.lending.auth.repository;

import com.naztech.lending.auth.domain.LoginHistory;
import com.naztech.lending.auth.domain.UserAccount;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findByUserOrderByOccurredAtDesc(UserAccount user, Pageable pageable);
}
