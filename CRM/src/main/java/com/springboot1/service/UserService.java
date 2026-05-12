package com.springboot1.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.controller.Employee;
import com.springboot1.model.Role;
import com.springboot1.model.TenantStatus;
import com.springboot1.model.User;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.UserRepository;

@Service
@Transactional
public class UserService {

	private final UserRepository userRepo;
	private final EmployeeRepository empRepo;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepo, EmployeeRepository empRepo, PasswordEncoder passwordEncoder) {
		this.userRepo = userRepo;
		this.empRepo = empRepo;
		this.passwordEncoder = passwordEncoder;
	}

	// ── List all staff users for a specific tenant ───────────────────────────
	public List<User> getAllStaffUsers() {
		List<User> managers = userRepo.findByRoleAndStatus(Role.MANAGER, TenantStatus.ACTIVE);
		List<User> salesExecs = userRepo.findByRoleAndStatus(Role.SALES_EXECUTIVE, TenantStatus.ACTIVE);
		managers.addAll(salesExecs);
		return managers;
	}

	public List<User> getStaffUsersByTenant(String tenantId) {
		return userRepo.findByTenantId(tenantId).stream()
				.filter(u -> u.getRole() == Role.MANAGER || u.getRole() == Role.SALES_EXECUTIVE)
				.collect(java.util.stream.Collectors.toList());
	}

	public List<User> getUsersByRole(Role role) {
		return userRepo.findByRoleAndStatus(role, TenantStatus.ACTIVE);
	}

	// ── Tenant-scoped counts (source of truth = users table) ─────────────────
	public long countTotalStaffByTenant(String tenantId) {
		return userRepo.findByTenantId(tenantId).stream()
				.filter(u -> u.getRole() == Role.MANAGER || u.getRole() == Role.SALES_EXECUTIVE)
				.count();
	}

	public long countManagersByTenant(String tenantId) {
		return userRepo.findByTenantId(tenantId).stream()
				.filter(u -> u.getRole() == Role.MANAGER)
				.count();
	}

	public long countSalesExecsByTenant(String tenantId) {
		return userRepo.findByTenantId(tenantId).stream()
				.filter(u -> u.getRole() == Role.SALES_EXECUTIVE)
				.count();
	}

	public long countActiveStaffByTenant(String tenantId) {
		return userRepo.findByTenantId(tenantId).stream()
				.filter(u -> (u.getRole() == Role.MANAGER || u.getRole() == Role.SALES_EXECUTIVE)
						&& u.getStatus() == TenantStatus.ACTIVE)
				.count();
	}

	// ── Update an existing staff user (no password change) ───────────────────
	public User updateUser(Long userId, String email, String phone, String role,
			String status, String address, String companyName) {
		User user = userRepo.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

		// Check email uniqueness if changed
		if (!user.getEmail().equals(email) && userRepo.existsByEmail(email)) {
			throw new IllegalArgumentException("Email '" + email + "' is already in use.");
		}

		user.setEmail(email);
		user.setPhone(phone);
		user.setRole(Role.valueOf(role));
		user.setStatus(com.springboot1.model.TenantStatus.valueOf(status));
		user.setAddress(address);
		user.setCompanyName(companyName);

		// Sync employee record if exists
		empRepo.findByUserId(userId).ifPresent(emp -> {
			emp.setEmail(email);
			emp.setPhone(phone);
			emp.setRole(Role.valueOf(role) == Role.MANAGER
					? Employee.Role.MANAGER : Employee.Role.SALES_EXECUTIVE);
			emp.setStatus(status.equals("ACTIVE")
					? Employee.Status.ACTIVE : Employee.Status.INACTIVE);
			empRepo.save(emp);
		});

		return userRepo.save(user);
	}

	// ── Delete a staff user and their linked employee record ─────────────────
	public void deleteUser(Long userId) {
		User user = userRepo.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
		if (user.getRole() != Role.MANAGER && user.getRole() != Role.SALES_EXECUTIVE) {
			throw new IllegalArgumentException("Cannot delete this user type.");
		}
		// Remove linked employee record if exists
		empRepo.findByUserId(userId).ifPresent(empRepo::delete);
		userRepo.delete(user);
	}

	public User createStaffUser(String fullName, String username, String email, String password,
			String phone, String address, String companyName, String tenantId, Role role, String status) {

		if (userRepo.existsByUsername(username))
			throw new IllegalArgumentException("Username '" + username + "' is already taken.");
		if (userRepo.existsByEmail(email))
			throw new IllegalArgumentException("Email '" + email + "' is already registered.");
		if (empRepo.existsByEmail(email))
			throw new IllegalArgumentException("An employee with email '" + email + "' already exists.");
		if (role != Role.MANAGER && role != Role.SALES_EXECUTIVE)
			throw new IllegalArgumentException("Admin can only create MANAGER or SALES_EXECUTIVE users.");

		com.springboot1.model.TenantStatus userStatus = "INACTIVE".equals(status)
				? com.springboot1.model.TenantStatus.INACTIVE
				: com.springboot1.model.TenantStatus.ACTIVE;

		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(role);
		user.setPhone(phone);
		user.setAddress(address);
		user.setCompanyName(companyName);
		user.setTenantId(tenantId);
		user.setStatus(userStatus);
		User savedUser = userRepo.save(user);

		Employee emp = new Employee();
		emp.setName(fullName);
		emp.setEmail(email);
		emp.setPhone(phone);
		emp.setDepartment(role == Role.MANAGER ? "Management" : "Sales");
		emp.setRole(role == Role.MANAGER ? Employee.Role.MANAGER : Employee.Role.SALES_EXECUTIVE);
		emp.setStatus(userStatus == com.springboot1.model.TenantStatus.ACTIVE
				? Employee.Status.ACTIVE : Employee.Status.INACTIVE);
		emp.setUserId(savedUser.getId());
		emp.setTenantId(tenantId);
		empRepo.save(emp);

		return savedUser;
	}

	// Overload for backward compatibility
	public User createStaffUser(String fullName, String username, String email, String password,
			String phone, String address, String companyName, String tenantId, Role role) {
		return createStaffUser(fullName, username, email, password, phone, address, companyName, tenantId, role, "ACTIVE");
	}
}
