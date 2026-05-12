package com.springboot1.controller;

import java.security.Principal;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.springboot1.controller.Lead.LeadStatus;
import com.springboot1.model.Role;
import com.springboot1.model.User;
import com.springboot1.repository.UserRepository;
import com.springboot1.service.EmployeeService;
import com.springboot1.service.LeadService;
import com.springboot1.service.UserService;

@Controller
@RequestMapping("/admin")
public class AdminController {

	private final EmployeeService empService;
	private final LeadService leadService;
	private final UserService userService;
	private final UserRepository userRepository;

	public AdminController(EmployeeService empService, LeadService leadService,
			UserService userService, UserRepository userRepository) {
		this.empService = empService;
		this.leadService = leadService;
		this.userService = userService;
		this.userRepository = userRepository;
	}

	// ── Helper ───────────────────────────────────────────────────────────────
	private String tid(Principal principal) {
		if (principal == null) return null;
		return userRepository.findByUsername(principal.getName())
				.map(User::getTenantId).orElse(null);
	}

	// ════════════════════════════════════════════════════════════════════════
	// DASHBOARD
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/dashboard")
	public String dashboard(Model model, Principal principal) {
		String t = tid(principal);
		// Team stats — driven from users table (source of truth, always accurate)
		model.addAttribute("totalEmployees",  userService.countTotalStaffByTenant(t));
		model.addAttribute("totalManagers",   userService.countManagersByTenant(t));
		model.addAttribute("totalSalesExecs", userService.countSalesExecsByTenant(t));
		model.addAttribute("activeEmployees", userService.countActiveStaffByTenant(t));
		// Lead stats
		model.addAttribute("pendingLeads",    leadService.countPendingByTenant(t));
		model.addAttribute("approvedLeads",   leadService.countApprovedByTenant(t));
		model.addAttribute("totalLeads",      leadService.countTotalByTenant(t));
		model.addAttribute("approvedValue",   leadService.sumApprovedValueByTenant(t));
		model.addAttribute("recentLeads",     leadService.getPendingLeadsByTenant(t));
		return "dashboard-admin";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/employees")
	public String listEmployees(Model model, Principal principal,
			@RequestParam(required = false) String role,
			@RequestParam(required = false) String success,
			@RequestParam(required = false) String error) {
		String t = tid(principal);
		if ("MANAGER".equals(role)) {
			model.addAttribute("employees",  empService.getManagersByTenant(t));
			model.addAttribute("filterRole", "MANAGER");
		} else if ("SALES_EXECUTIVE".equals(role)) {
			model.addAttribute("employees",  empService.getSalesExecutivesByTenant(t));
			model.addAttribute("filterRole", "SALES_EXECUTIVE");
		} else {
			model.addAttribute("employees",  empService.getAllEmployeesByTenant(t));
			model.addAttribute("filterRole", "ALL");
		}
		model.addAttribute("newEmployee",     new Employee());
		model.addAttribute("totalManagers",   userService.countManagersByTenant(t));
		model.addAttribute("totalSalesExecs", userService.countSalesExecsByTenant(t));
		model.addAttribute("totalActive",     userService.countActiveStaffByTenant(t));
		if (success != null) model.addAttribute("success", success);
		if (error   != null) model.addAttribute("error",   error);
		return "admin-employees";
	}

	@PostMapping("/employees/add")
	public String addEmployee(@ModelAttribute Employee employee, Principal principal, RedirectAttributes ra) {
		try {
			employee.setTenantId(tid(principal));
			empService.addEmployee(employee);
			ra.addFlashAttribute("success", "Employee '" + employee.getName() + "' added.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	@GetMapping("/employees/{id}/json")
	@ResponseBody
	public Employee getEmployeeJson(@PathVariable Long id) {
		return empService.getById(id).orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
	}

	@PostMapping("/employees/{id}/update")
	public String updateEmployee(@PathVariable Long id, @ModelAttribute Employee emp, RedirectAttributes ra) {
		try {
			empService.updateEmployee(id, emp);
			ra.addFlashAttribute("success", "Employee updated.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	@PostMapping("/employees/{id}/toggle-status")
	public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
		try {
			Employee emp = empService.toggleStatus(id);
			ra.addFlashAttribute("success", emp.getName() + " is now " + emp.getStatus().name().toLowerCase() + ".");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	@PostMapping("/employees/{id}/delete")
	public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
		try {
			empService.deleteEmployee(id);
			ra.addFlashAttribute("success", "Employee deleted.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads")
	public String listLeads(Model model, Principal principal,
			@RequestParam(required = false) String status) {
		String t = tid(principal);
		if ("PENDING".equals(status)) {
			model.addAttribute("leads",  leadService.getPendingLeadsByTenant(t));
			model.addAttribute("filter", "PENDING");
		} else {
			model.addAttribute("leads",  leadService.getAllLeadsByTenant(t));
			model.addAttribute("filter", "ALL");
		}
		model.addAttribute("salesExecs",    empService.getActiveSalesExecutivesByTenant(t));
		model.addAttribute("pendingCount",  leadService.countPendingByTenant(t));
		model.addAttribute("approvedCount", leadService.countApprovedByTenant(t));
		model.addAttribute("rejectedCount", leadService.countRejectedByTenant(t));
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		return "admin-view-leads";
	}

	@GetMapping("/leads/add")
	public String showAddLeadPage(Model model, Principal principal) {
		model.addAttribute("salesExecs", empService.getActiveSalesExecutivesByTenant(tid(principal)));
		return "admin-add-lead";
	}

	@GetMapping("/leads/assign")
	public String assignLeads(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("leads",      leadService.getPendingLeadsByTenant(t));
		model.addAttribute("salesExecs", empService.getActiveSalesExecutivesByTenant(t));
		return "admin-assign-leads";
	}

	@GetMapping("/leads/status")
	public String leadStatus(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("leads",         leadService.getAllLeadsByTenant(t));
		model.addAttribute("pendingCount",  leadService.countPendingByTenant(t));
		model.addAttribute("approvedCount", leadService.countApprovedByTenant(t));
		model.addAttribute("rejectedCount", leadService.countRejectedByTenant(t));
		return "admin-lead-status";
	}

	@PostMapping("/leads/{id}/approve")
	public String approveLead(@PathVariable Long id,
			@RequestParam(required = false) Long assignTo, RedirectAttributes ra) {
		try {
			Lead lead = leadService.approveLead(id, assignTo);
			ra.addFlashAttribute("success", "Lead '" + lead.getCustomerName() + "' approved.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	@PostMapping("/leads/{id}/reject")
	public String rejectLead(@PathVariable Long id,
			@RequestParam(required = false) String rejectionNote, RedirectAttributes ra) {
		try {
			Lead lead = leadService.rejectLead(id, rejectionNote);
			ra.addFlashAttribute("success", "Lead '" + lead.getCustomerName() + "' rejected.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	@PostMapping("/leads/{id}/reset")
	public String resetLead(@PathVariable Long id, RedirectAttributes ra) {
		try {
			leadService.resetToPending(id);
			ra.addFlashAttribute("success", "Lead reset to pending.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	@PostMapping("/leads/{id}/delete")
	public String deleteLead(@PathVariable Long id, RedirectAttributes ra) {
		try {
			leadService.deleteLead(id);
			ra.addFlashAttribute("success", "Lead deleted.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// PIPELINE
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/pipeline/deals")
	public String deals(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("approvedLeads", leadService.getAllLeadsByTenant(t).stream()
				.filter(l -> l.getStatus() == LeadStatus.APPROVED).collect(Collectors.toList()));
		model.addAttribute("approvedCount", leadService.countApprovedByTenant(t));
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		return "admin-deals";
	}

	@GetMapping("/pipeline/stages")
	public String pipelineStages(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("pendingLeads",  leadService.countPendingByTenant(t));
		return "admin-pipeline-stages";
	}

	@GetMapping("/pipeline/won-lost")
	public String wonLost(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("rejectedLeads", leadService.countRejectedByTenant(t));
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		return "admin-won-lost";
	}

	// ════════════════════════════════════════════════════════════════════════
	// ACTIVITIES
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/activities/calls")
	public String calls(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("salesExecs", empService.getSalesExecutivesByTenant(t));
		model.addAttribute("leads",      leadService.getAllLeadsByTenant(t));
		return "admin-calls";
	}

	@GetMapping("/activities/meetings")
	public String meetings(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("employees", empService.getAllEmployeesByTenant(t));
		model.addAttribute("leads",     leadService.getAllLeadsByTenant(t));
		return "admin-meetings";
	}

	@GetMapping("/activities/tasks")
	public String tasks(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("pendingLeads", leadService.getPendingLeadsByTenant(t));
		model.addAttribute("salesExecs",   empService.getSalesExecutivesByTenant(t));
		return "admin-tasks";
	}

	// ════════════════════════════════════════════════════════════════════════
	// REPORTS
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/reports/sales")
	public String salesReports(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("pendingLeads",  leadService.countPendingByTenant(t));
		model.addAttribute("rejectedLeads", leadService.countRejectedByTenant(t));
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		model.addAttribute("leads",         leadService.getAllLeadsByTenant(t));
		return "admin-sales-reports";
	}

	@GetMapping("/reports/performance")
	public String performanceReports(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalEmployees",  empService.countTotalByTenant(t));
		model.addAttribute("totalManagers",   empService.countManagersByTenant(t));
		model.addAttribute("totalSalesExecs", empService.countSalesExecutivesByTenant(t));
		model.addAttribute("activeEmployees", empService.countActiveByTenant(t));
		model.addAttribute("approvedLeads",   leadService.countApprovedByTenant(t));
		model.addAttribute("approvedValue",   leadService.sumApprovedValueByTenant(t));
		model.addAttribute("employees",       empService.getAllEmployeesByTenant(t));
		return "admin-performance-reports";
	}

	@GetMapping("/reports/revenue")
	public String revenueReports(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("leads",         leadService.getAllLeadsByTenant(t));
		return "admin-revenue-reports";
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users/add")
	public String showAddUserPage(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("allUsers", t != null
				? userService.getStaffUsersByTenant(t)
				: userService.getAllStaffUsers());
		return "admin-add-user";
	}

	@GetMapping("/users/new")
	public String showNewUserForm(Model model, Principal principal) {
		model.addAttribute("adminTenantId", tid(principal));
		if (principal != null) {
			userRepository.findByUsername(principal.getName())
				.ifPresent(a -> model.addAttribute("adminCompanyName", a.getCompanyName()));
		}
		return "admin-new-user";
	}

	@PostMapping("/users/new")
	public String createUser(
			@RequestParam String fullName,
			@RequestParam String username,
			@RequestParam String email,
			@RequestParam String password,
			@RequestParam String phone,
			@RequestParam(required = false) String address,
			@RequestParam(required = false) String companyName,
			@RequestParam String role,
			@RequestParam(required = false, defaultValue = "ACTIVE") String status,
			Principal principal,
			RedirectAttributes ra) {
		try {
			Role userRole = Role.valueOf(role);
			String t = tid(principal);
			String company = (companyName != null && !companyName.isBlank()) ? companyName
					: userRepository.findByUsername(principal.getName())
						.map(User::getCompanyName).orElse(null);
			userService.createStaffUser(fullName, username, email, password, phone, address, company, t, userRole, status);
			ra.addFlashAttribute("success", userRole == Role.MANAGER
					? "Manager '" + fullName + "' created."
					: "Sales Executive '" + fullName + "' created.");
			return "redirect:/admin/users/add";
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error",           e.getMessage());
			ra.addFlashAttribute("formFullName",    fullName);
			ra.addFlashAttribute("formUsername",    username);
			ra.addFlashAttribute("formEmail",       email);
			ra.addFlashAttribute("formPhone",       phone);
			ra.addFlashAttribute("formAddress",     address);
			ra.addFlashAttribute("formCompanyName", companyName);
			ra.addFlashAttribute("formRole",        role);
			ra.addFlashAttribute("formStatus",      status);
			return "redirect:/admin/users/new";
		}
	}

	@PostMapping("/users/{id}/delete")
	public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
		try {
			userService.deleteUser(id);
			ra.addFlashAttribute("success", "User deleted.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
		}
		return "redirect:/admin/users/add";
	}

	@PostMapping("/users/{id}/update")
	public String updateUser(@PathVariable Long id,
			@RequestParam String email,
			@RequestParam String phone,
			@RequestParam String role,
			@RequestParam String status,
			@RequestParam(required = false) String address,
			@RequestParam(required = false) String companyName,
			RedirectAttributes ra) {
		try {
			userService.updateUser(id, email, phone, role, status, address, companyName);
			ra.addFlashAttribute("success", "User updated successfully.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/users/add";
	}

	@GetMapping("/users")
	public String listUsers(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("managers",   userService.getStaffUsersByTenant(t).stream()
				.filter(u -> u.getRole() == Role.MANAGER).collect(Collectors.toList()));
		model.addAttribute("salesExecs", userService.getStaffUsersByTenant(t).stream()
				.filter(u -> u.getRole() == Role.SALES_EXECUTIVE).collect(Collectors.toList()));
		return "admin-users";
	}

	@GetMapping("/users/roles")
	public String assignRoles(Model model, Principal principal) {
		model.addAttribute("allUsers", userService.getStaffUsersByTenant(tid(principal)));
		return "admin-assign-roles";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SYSTEM
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/notifications")
	public String notifications(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("pendingLeads",  leadService.getPendingLeadsByTenant(t));
		model.addAttribute("pendingCount",  leadService.countPendingByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		return "admin-notifications";
	}

	@GetMapping("/settings")
	public String settings(Model model, Principal principal) {
		if (principal != null) {
			userRepository.findByUsername(principal.getName()).ifPresent(a -> {
				model.addAttribute("adminUser",     a);
				model.addAttribute("adminTenantId", a.getTenantId());
				model.addAttribute("adminCompany",  a.getCompanyName());
			});
		}
		return "admin-settings";
	}

	@GetMapping("/profile")
	public String profile(Model model, Principal principal) {
		String t = tid(principal);
		if (principal != null) {
			userRepository.findByUsername(principal.getName())
				.ifPresent(a -> model.addAttribute("adminUser", a));
		}
		model.addAttribute("totalEmployees", empService.countTotalByTenant(t));
		model.addAttribute("totalLeads",     leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads",  leadService.countApprovedByTenant(t));
		model.addAttribute("approvedValue",  leadService.sumApprovedValueByTenant(t));
		return "admin-profile";
	}
}
