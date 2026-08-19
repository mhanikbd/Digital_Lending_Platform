package com.naztech.lending.organization.repository;

import com.naztech.lending.organization.domain.OrgUnitType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgUnitTypeRepository extends JpaRepository<OrgUnitType, String> {

    List<OrgUnitType> findAllByOrderByHierarchyLevelAscCodeAsc();
}
