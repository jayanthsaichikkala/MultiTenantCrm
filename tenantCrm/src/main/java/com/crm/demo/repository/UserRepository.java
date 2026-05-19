package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    // LOGIN WITH USERNAME
    User findByUsername(String username);

    // LOGIN WITH EMAIL
    User findByEmail(String email);

    // LOGIN WITH USERNAME OR EMAIL
    User findByUsernameOrEmail(String username, String email);

    // TENANT-SCOPED: fetch all users whose email contains the tenant segment
    // e.g. tenantSegment = "tcs"  →  matches "emp.tcs@crm.com", "admin.tcs@crm.com"
    @Query("SELECT u FROM User u WHERE u.email LIKE %:tenantSegment%")
    List<User> findByTenantSegment(@Param("tenantSegment") String tenantSegment);

}