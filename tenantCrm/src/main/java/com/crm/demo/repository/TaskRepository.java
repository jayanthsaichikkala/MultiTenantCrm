package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crm.demo.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
