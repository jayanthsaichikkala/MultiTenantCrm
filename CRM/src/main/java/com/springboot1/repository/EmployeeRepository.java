package com.springboot1.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.springboot1.controller.Employee;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	Optional<Employee> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	Optional<Employee> findByUserId(Long userId);

	List<Employee> findByRole(Employee.Role role);

	List<Employee> findByStatus(Employee.Status status);

	List<Employee> findByRoleAndStatus(Employee.Role role, Employee.Status status);

	// ── Tenant-scoped queries ─────────────────────────────────────────────

	List<Employee> findByTenantId(String tenantId);

	List<Employee> findByTenantIdAndRole(String tenantId, Employee.Role role);

	List<Employee> findByTenantIdAndStatus(String tenantId, Employee.Status status);

	List<Employee> findByTenantIdAndRoleAndStatus(String tenantId, Employee.Role role, Employee.Status status);

	@Query("SELECT COUNT(e) FROM Employee e WHERE e.role = :role")
	long countByRole(Employee.Role role);

	@Query("SELECT COUNT(e) FROM Employee e WHERE e.tenantId = :tenantId AND e.role = :role")
	long countByTenantIdAndRole(@Param("tenantId") String tenantId, @Param("role") Employee.Role role);

	@Query("SELECT COUNT(e) FROM Employee e WHERE e.status = 'ACTIVE'")
	long countActive();

	@Query("SELECT COUNT(e) FROM Employee e WHERE e.tenantId = :tenantId AND e.status = 'ACTIVE'")
	long countActiveByTenantId(@Param("tenantId") String tenantId);

	@Query("SELECT COUNT(e) FROM Employee e WHERE e.tenantId = :tenantId")
	long countByTenantId(@Param("tenantId") String tenantId);
}