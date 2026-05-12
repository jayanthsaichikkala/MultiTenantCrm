package com.springboot1.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.controller.Employee;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.UserRepository;

/**
 * Runs once on startup to fix legacy Employee records that have no tenantId.
 * Syncs tenantId from the linked User record so tenant-scoped queries work correctly.
 */
@Component
public class DataCleanupService implements ApplicationRunner {

private final EmployeeRepository empRepo;
private final UserRepository userRepo;

public DataCleanupService(EmployeeRepository empRepo, UserRepository userRepo) {
this.empRepo = empRepo;
this.userRepo = userRepo;
}

@Override
@Transactional
public void run(ApplicationArguments args) {
// Fix employees with null tenantId by syncing from their linked User
empRepo.findAll().forEach(emp -> {
if (emp.getTenantId() == null && emp.getUserId() != null) {
userRepo.findById(emp.getUserId()).ifPresent(user -> {
if (user.getTenantId() != null) {
emp.setTenantId(user.getTenantId());
empRepo.save(emp);
}
});
}
});
}
}
