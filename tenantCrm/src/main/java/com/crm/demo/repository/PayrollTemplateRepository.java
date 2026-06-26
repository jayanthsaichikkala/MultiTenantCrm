package com.crm.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.crm.demo.model.PayrollTemplate;
import com.crm.demo.model.User;

public interface PayrollTemplateRepository extends JpaRepository<PayrollTemplate, Long> {

    /** All payroll templates for a tenant, newest first. */
    List<PayrollTemplate> findByTenantSegmentOrderByCreatedAtDesc(String tenantSegment);

    /** One template per employee (unique per tenant). */
    Optional<PayrollTemplate> findByEmployeeAndTenantSegment(User employee, String tenantSegment);

    /** Find all templates for an employee across tenants (for employee self-view). */
    List<PayrollTemplate> findByEmployee(User employee);

    /** Count templates for a tenant. */
    long countByTenantSegment(String tenantSegment);

    /** Find templates for a specific team's members. */
    @Query("SELECT p FROM PayrollTemplate p WHERE p.employee IN (:employees) AND p.tenantSegment = :tenant")
    List<PayrollTemplate> findByEmployeesAndTenant(@Param("employees") List<User> employees,
                                                   @Param("tenant") String tenant);

    @Transactional
    void deleteByEmployee(User employee);
}