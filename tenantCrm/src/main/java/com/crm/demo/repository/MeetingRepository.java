package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crm.demo.model.Meeting;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

}