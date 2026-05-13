package com.springboot1.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.controller.Employee;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.LeadRepository;
import com.springboot1.repository.UserRepository;

/**
 * Runs once on startup:
 * 1. Removes known demo/seed employees that have no real linked User account.
 * 2. Syncs tenantId from the linked User record for any remaining employees.
 */
@Component
public class DataCleanupService implements ApplicationRunner {

    // Demo employee names inserted by old seed scripts — remove them on startup
    private static final List<String> DEMO_EMPLOYEE_NAMES = Arrays.asList(
        "Rahul Sharma", "Priya Patel", "Arjun Menon", "Kavya Reddy",
        "Sarah Mitchell", "John Smith", "Jane Doe"
    );

    private final EmployeeRepository empRepo;
    private final UserRepository     userRepo;
    private final LeadRepository     leadRepo;

    public DataCleanupService(EmployeeRepository empRepo,
                              UserRepository userRepo,
                              LeadRepository leadRepo) {
        this.empRepo  = empRepo;
        this.userRepo = userRepo;
        this.leadRepo = leadRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // ── 1. Remove demo employees that have no linked real User ────────────
        empRepo.findAll().forEach(emp -> {
            boolean isDemoName = DEMO_EMPLOYEE_NAMES.stream()
                .anyMatch(demo -> demo.equalsIgnoreCase(emp.getName()));

            boolean hasNoLinkedUser = emp.getUserId() == null
                || !userRepo.existsById(emp.getUserId());

            if (isDemoName && hasNoLinkedUser) {
                // Null out any leads that reference this employee before deleting
                leadRepo.findByCreatedById(emp.getId()).forEach(lead -> {
                    lead.setCreatedBy(null);
                    leadRepo.save(lead);
                });
                leadRepo.findByAssignedToId(emp.getId()).forEach(lead -> {
                    lead.setAssignedTo(null);
                    leadRepo.save(lead);
                });
                empRepo.delete(emp);
            }
        });

        // ── 2. Sync tenantId from linked User for remaining employees ─────────
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
