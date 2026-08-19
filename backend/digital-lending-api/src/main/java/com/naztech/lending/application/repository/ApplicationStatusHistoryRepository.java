package com.naztech.lending.application.repository;

import com.naztech.lending.application.domain.ApplicationStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusHistoryRepository
        extends JpaRepository<ApplicationStatusHistory, Long> {

    /** Where the file has been, oldest first, which is how a trail reads. */
    List<ApplicationStatusHistory> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}
