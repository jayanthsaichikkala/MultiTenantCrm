package com.crm.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

	// =========================
	// LOGIN METHODS
	// =========================

	@Query("SELECT u FROM User u WHERE u.username = :username ORDER BY u.id ASC LIMIT 1")
	User findByUsername(@Param("username") String username);

	@Query("SELECT u FROM User u WHERE u.email = :email ORDER BY u.id ASC LIMIT 1")
	User findByEmail(@Param("email") String email);

	/**
	 * Returns the first user whose username OR email matches the given values.
	 * Using LIMIT 1 avoids NonUniqueResultException when the OR clause
	 * inadvertently matches more than one row.
	 */
	@Query("SELECT u FROM User u WHERE u.username = :username OR u.email = :email ORDER BY u.id ASC LIMIT 1")
	Optional<User> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

	/**
	 * Existence check used for duplicate-detection before saving a new user.
	 */
	@Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username OR u.email = :email")
	boolean existsByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

	// =========================
	// TENANT FILTER
	// Example:
	// tcs -> emp.tcs@crm.com
	// =========================

	@Query("SELECT u FROM User u WHERE u.email LIKE %:tenantSegment%")
	List<User> findByTenantSegment(@Param("tenantSegment") String tenantSegment);

	// =========================
	// ONLY EMPLOYEES BY TENANT
	// =========================

	@Query("SELECT u FROM User u WHERE u.email LIKE %:tenantSegment% AND UPPER(u.role) = 'EMPLOYEE'")
	List<User> findEmployeesByTenant(@Param("tenantSegment") String tenantSegment);
}