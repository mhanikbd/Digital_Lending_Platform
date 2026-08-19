package com.naztech.lending.application.repository;

import com.naztech.lending.application.domain.LoanPurpose;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanPurposeRepository extends JpaRepository<LoanPurpose, String> {

    List<LoanPurpose> findByStatusOrderByDisplayOrderAsc(String status);
}
