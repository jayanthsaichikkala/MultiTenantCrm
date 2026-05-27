package com.crm.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.Meeting;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

	List<Meeting> findByTenantSegmentOrderByMeetingDateAscMeetingTimeAsc(String tenantSegment);

	/** Find all meetings in a tenant where the given username appears in the participants field. */
	@Query("SELECT m FROM Meeting m WHERE m.tenantSegment = :tenant AND m.participants LIKE %:username%")
	List<Meeting> findByTenantAndParticipantUsername(
			@Param("tenant") String tenant,
			@Param("username") String username);
}