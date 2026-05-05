package com.springboot1.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.springboot1.controller.Lead;
import com.springboot1.controller.Lead.LeadStatus;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

	List<Lead> findByStatus(LeadStatus status);

	List<Lead> findByStatusOrderByCreatedAtDesc(LeadStatus status);

	List<Lead> findAllByOrderByCreatedAtDesc();

	List<Lead> findByCreatedById(Long managerId);

	List<Lead> findByAssignedToId(Long salesExecId);

	@Query("SELECT COUNT(l) FROM Lead l WHERE l.status = :status")
	long countByStatus(LeadStatus status);

	@Query("SELECT COALESCE(SUM(l.dealValue), 0) FROM Lead l WHERE l.status = 'APPROVED' OR l.status = 'WON'")
	BigDecimal sumApprovedDealValue();

	@Query("SELECT l FROM Lead l LEFT JOIN FETCH l.createdBy LEFT JOIN FETCH l.assignedTo ORDER BY l.createdAt DESC")
	List<Lead> findAllWithRelations();

	@Query("SELECT l FROM Lead l LEFT JOIN FETCH l.createdBy LEFT JOIN FETCH l.assignedTo WHERE l.status = :status ORDER BY l.createdAt DESC")
	List<Lead> findByStatusWithRelations(LeadStatus status);
}