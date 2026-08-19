package com.naztech.lending.rules.repository;

import com.naztech.lending.rules.domain.RuleAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleAttributeRepository extends JpaRepository<RuleAttribute, String> {

    List<RuleAttribute> findAllByOrderByCodeAsc();
}
