package com.crm.demo.repository;

import com.crm.demo.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** All holidays for a specific tenant. */
    List<Holiday> findByTenantSegmentOrderByDateAsc(String tenantSegment);

    /** Find a holiday by date AND tenant (prevents cross-tenant collision). */
    Optional<Holiday> findByDateAndTenantSegment(String date, String tenantSegment);

    /** Check if a date is already taken for a tenant (excluding a given id). */
    boolean existsByDateAndTenantSegmentAndIdNot(String date, String tenantSegment, Long id);

    /** Check if a date is already taken for a tenant (for new records). */
    boolean existsByDateAndTenantSegment(String date, String tenantSegment);
}
