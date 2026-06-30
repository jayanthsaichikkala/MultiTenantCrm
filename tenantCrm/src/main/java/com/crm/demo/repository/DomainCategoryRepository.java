package com.crm.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.crm.demo.model.DomainCategory;

@Repository
public interface DomainCategoryRepository extends JpaRepository<DomainCategory, Long> {
    List<DomainCategory> findByTenantSegment(String tenantSegment);
    boolean existsByNameAndTenantSegment(String name, String tenantSegment);
}
