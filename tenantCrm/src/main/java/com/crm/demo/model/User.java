package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    public static final String STATUS_ACTIVE = "active";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    private String password;

    private String role;

    @Column(nullable = false, columnDefinition = "VARCHAR(10) DEFAULT '" + STATUS_ACTIVE + "'")
    private String status = STATUS_ACTIVE;

    @Column(name = "employee_limit", nullable = false, columnDefinition = "int default 10")
    private Integer employeeLimit = 10;

    private String domain;

    @Column(name = "joining_date")
    private java.time.LocalDate joiningDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status != null ? status : STATUS_ACTIVE; }
    public void setStatus(String status) { this.status = status; }

    public Integer getEmployeeLimit() { return employeeLimit != null ? employeeLimit : 10; }
    public void setEmployeeLimit(Integer employeeLimit) { this.employeeLimit = employeeLimit; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public java.time.LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(java.time.LocalDate joiningDate) { this.joiningDate = joiningDate; }

    public boolean isActive() { return STATUS_ACTIVE.equalsIgnoreCase(getStatus()); }
	
}