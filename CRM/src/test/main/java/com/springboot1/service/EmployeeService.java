package com.springboot1.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.controller.Employee;
import com.springboot1.repository.EmployeeRepository;

@Service
@Transactional
public class EmployeeService {

	private final EmployeeRepository repo;

	public EmployeeService(EmployeeRepository repo) {
		this.repo = repo;
	}

	// ── Read ──────────────────────────────────────────────────────────────────

	public List<Employee> getAllEmployees() {
		return repo.findAll();
	}

	public Optional<Employee> getById(Long id) {
		return repo.findById(id);
	}

	public List<Employee> getManagers() {
		return repo.findByRole(Employee.Role.MANAGER);
	}

	public List<Employee> getActiveManagers() {
		return repo.findByRoleAndStatus(Employee.Role.MANAGER, Employee.Status.ACTIVE);
	}

	public List<Employee> getSalesExecutives() {
		return repo.findByRole(Employee.Role.SALES_EXECUTIVE);
	}

	public List<Employee> getActiveSalesExecutives() {
		return repo.findByRoleAndStatus(Employee.Role.SALES_EXECUTIVE, Employee.Status.ACTIVE);
	}

	// ── Create ────────────────────────────────────────────────────────────────

	public Employee addEmployee(Employee employee) {
		if (repo.existsByEmail(employee.getEmail())) {
			throw new IllegalArgumentException("Email already exists: " + employee.getEmail());
		}
		return repo.save(employee);
	}

	// ── Update ────────────────────────────────────────────────────────────────

	public Employee updateEmployee(Long id, Employee updated) {
		Employee existing = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));

		if (repo.existsByEmailAndIdNot(updated.getEmail(), id)) {
			throw new IllegalArgumentException("Email already in use: " + updated.getEmail());
		}

		existing.setName(updated.getName());
		existing.setEmail(updated.getEmail());
		existing.setPhone(updated.getPhone());
		existing.setRole(updated.getRole());
		existing.setDepartment(updated.getDepartment());
		existing.setStatus(updated.getStatus());

		return repo.save(existing);
	}

	// ── Delete ────────────────────────────────────────────────────────────────

	public void deleteEmployee(Long id) {
		Employee emp = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
		repo.delete(emp);
	}

	// ── Toggle Status ─────────────────────────────────────────────────────────

	public Employee toggleStatus(Long id) {
		Employee emp = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
		emp.setStatus(emp.getStatus() == Employee.Status.ACTIVE ? Employee.Status.INACTIVE : Employee.Status.ACTIVE);
		return repo.save(emp);
	}

	// ── Stats ─────────────────────────────────────────────────────────────────

	public long countManagers() {
		return repo.countByRole(Employee.Role.MANAGER);
	}

	public long countSalesExecutives() {
		return repo.countByRole(Employee.Role.SALES_EXECUTIVE);
	}

	public long countActive() {
		return repo.countActive();
	}

	public long countTotal() {
		return repo.count();
	}
}