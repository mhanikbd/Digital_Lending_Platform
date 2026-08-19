package com.naztech.lending.application.repository;

import com.naztech.lending.application.domain.ApplicationComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationCommentRepository extends JpaRepository<ApplicationComment, Long> {

    List<ApplicationComment> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
