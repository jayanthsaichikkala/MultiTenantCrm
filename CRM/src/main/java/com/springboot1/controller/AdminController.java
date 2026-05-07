package com.springboot1.controller;

import java.security.Principal;

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

	// ── Helper: get the logged-in admin's tenantId ───────────────────────────
	private String getAdminTenantId(Principal principal) {
		if (principal == null) return null;
		return userRepository.findByUsername(principal.getName())
				.map(User::getTenantId)
				.orElse(null);
	}

	// ════════════════════════════════════════════════════════════════════════
	// DASHBOARD
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("totalEmployees", empService.countTotal());
		model.addAttribute("totalManagers", empService.countManagers());
		model.addAttribute("totalSalesExecs", empService.countSalesExecutives());
		model.addAttribute("activeEmployees", empService.countActive());
		model.addAttribute("pendingLeads", leadService.countPending());
		model.addAttribute("approvedLeads", leadService.countApproved());
		model.addAttribute("totalLeads", leadService.countTotal());
		model.addAttribute("approvedValue", leadService.sumApprovedValue());
		model.addAttribute("recentLeads", leadService.getPendingLeads());
		return "dashboard-admin";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — List
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/employees")
	public String listEmployees(Model model, @RequestParam(required = false) String role,
			@RequestParam(required = false) String success, @RequestParam(required = false) String error) {
		if ("MANAGER".equals(role)) {
			model.addAttribute("employees", empService.getManagers());
			model.addAttribute("filterRole", "MANAGER");
		} else if ("SALES_EXECUTIVE".equals(role)) {
			model.addAttribute("employees", empService.getSalesExecutives());
			model.addAttribute("filterRole", "SALES_EXECUTIVE");
		} else {
			model.addAttribute("employees", empService.getAllEmployees());
			model.addAttribute("filterRole", "ALL");
		}

		model.addAttribute("newEmployee", new Employee());
		model.addAttribute("totalManagers", empService.countManagers());
		model.addAttribute("totalSalesExecs", empService.countSalesExecutives());
		model.addAttribute("totalActive", empService.countActive());

		if (success != null)
			model.addAttribute("success", success);
		if (error != null)
			model.addAttribute("error", error);

		return "admin-employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Add
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/add")
	public String addEmployee(@ModelAttribute Employee employee, RedirectAttributes ra) {
		try {
			empService.addEmployee(employee);
			ra.addFlashAttribute("success", "Employee '" + employee.getName() + "' added successfully.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Edit (load into modal via AJAX)
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/employees/{id}/json")
	@ResponseBody
	public Employee getEmployeeJson(@PathVariable Long id) {
		return empService.getById(id).orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Update
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/{id}/update")
	public String updateEmployee(@PathVariable Long id, @ModelAttribute Employee employee, RedirectAttributes ra) {
		try {
			empService.updateEmployee(id, employee);
			ra.addFlashAttribute("success", "Employee updated successfully.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Toggle Status
	// ════════════════════════════════════════════════════════════════════════

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

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Delete
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/{id}/delete")
	public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
		try {
			empService.deleteEmployee(id);
			ra.addFlashAttribute("success", "Employee deleted successfully.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete employee: " + e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — List / Approval Queue
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads")
	public String listLeads(Model model, @RequestParam(required = false) String status) {
		if ("PENDING".equals(status)) {
			model.addAttribute("leads", leadService.getPendingLeads());
			model.addAttribute("filter", "PENDING");
		} else {
			model.addAttribute("leads", leadService.getAllLeads());
			model.addAttribute("filter", "ALL");
		}

		model.addAttribute("salesExecs", empService.getActiveSalesExecutives());
		model.addAttribute("pendingCount", leadService.countPending());
		model.addAttribute("approvedCount", leadService.countApproved());
		model.addAttribute("rejectedCount", leadService.countRejected());
		model.addAttribute("totalLeads", leadService.countTotal());

		return "admin-view-leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Approve
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/approve")
	public String approveLead(@PathVariable Long id, @RequestParam(required = false) Long assignTo,
			RedirectAttributes ra) {
		try {
			Lead lead = leadService.approveLead(id, assignTo);
			ra.addFlashAttribute("success", "Lead for '" + lead.getCustomerName() + "' approved successfully.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Reject
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/reject")
	public String rejectLead(@PathVariable Long id, @RequestParam(required = false) String rejectionNote,
			RedirectAttributes ra) {
		try {
			Lead lead = leadService.rejectLead(id, rejectionNote);
			ra.addFlashAttribute("success", "Lead for '" + lead.getCustomerName() + "' rejected.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Reset to Pending
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/reset")
	public String resetLead(@PathVariable Long id, RedirectAttributes ra) {
		try {
			leadService.resetToPending(id);
			ra.addFlashAttribute("success", "Lead reset to pending for re-review.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Delete
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/delete")
	public String deleteLead(@PathVariable Long id, RedirectAttributes ra) {
		try {
			leadService.deleteLead(id);
			ra.addFlashAttribute("success", "Lead deleted.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete lead: " + e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — Add User page (list + "Add User" button)
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users/add")
	public String showAddUserPage(Model model, Principal principal) {
		String tenantId = getAdminTenantId(principal);
		if (tenantId != null) {
			model.addAttribute("allUsers", userService.getStaffUsersByTenant(tenantId));
		} else {
			model.addAttribute("allUsers", userService.getAllStaffUsers());
		}
		return "admin-add-user";
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — New User form (separate page)
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users/new")
	public String showNewUserForm(Model model, Principal principal) {
		// Pass admin's tenantId to display as read-only info
		model.addAttribute("adminTenantId", getAdminTenantId(principal));
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
			Principal principal,
			RedirectAttributes ra) {
		try {
			Role userRole = Role.valueOf(role);
			String tenantId = getAdminTenantId(principal);
			userService.createStaffUser(fullName, username, email, password, phone,
					address, companyName, tenantId, userRole);
			ra.addFlashAttribute("success",
					userRole == Role.MANAGER
							? "Manager '" + fullName + "' created successfully."
							: "Sales Executive '" + fullName + "' created successfully.");
			return "redirect:/admin/users/add";
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			ra.addFlashAttribute("formFullName", fullName);
			ra.addFlashAttribute("formUsername", username);
			ra.addFlashAttribute("formEmail", email);
			ra.addFlashAttribute("formPhone", phone);
			ra.addFlashAttribute("formAddress", address);
			ra.addFlashAttribute("formCompanyName", companyName);
			ra.addFlashAttribute("formRole", role);
			return "redirect:/admin/users/new";
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — Delete User
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/users/{id}/delete")
	public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
		try {
			userService.deleteUser(id);
			ra.addFlashAttribute("success", "User deleted successfully.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete user: " + e.getMessage());
		}
		return "redirect:/admin/users/add";
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — List Users
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users")
	public String listUsers(Model model) {
		model.addAttribute("managers", userService.getUsersByRole(Role.MANAGER));
		model.addAttribute("salesExecs", userService.getUsersByRole(Role.SALES_EXECUTIVE));
		return "admin-users";
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — Assign Roles
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users/roles")
	public String assignRoles(Model model) {
		return "admin-assign-roles";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Add Lead page
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads/add")
	public String showAddLeadPage(Model model) {
		return "admin-add-lead";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Assign Leads page
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads/assign")
	public String assignLeads(Model model) {
		return "admin-assign-leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Lead Status page
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads/status")
	public String leadStatus(Model model) {
		return "admin-lead-status";
	}

	// ════════════════════════════════════════════════════════════════════════
	// PIPELINE — Deals
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/pipeline/deals")
	public String deals(Model model) {
		return "admin-deals";
	}

	// ════════════════════════════════════════════════════════════════════════
	// PIPELINE — Stages
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/pipeline/stages")
	public String pipelineStages(Model model) {
		return "admin-pipeline-stages";
	}

	// ════════════════════════════════════════════════════════════════════════
	// PIPELINE — Won / Lost
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/pipeline/won-lost")
	public String wonLost(Model model) {
		return "admin-won-lost";
	}

	// ════════════════════════════════════════════════════════════════════════
	// ACTIVITIES — Calls
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/activities/calls")
	public String calls(Model model) {
		return "admin-calls";
	}

	// ════════════════════════════════════════════════════════════════════════
	// ACTIVITIES — Meetings
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/activities/meetings")
	public String meetings(Model model) {
		return "admin-meetings";
	}

	// ════════════════════════════════════════════════════════════════════════
	// ACTIVITIES — Tasks
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/activities/tasks")
	public String tasks(Model model) {
		return "admin-tasks";
	}

	// ════════════════════════════════════════════════════════════════════════
	// REPORTS — Sales
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/reports/sales")
	public String salesReports(Model model) {
		model.addAttribute("totalLeads", leadService.countTotal());
		model.addAttribute("approvedLeads", leadService.countApproved());
		model.addAttribute("pendingLeads", leadService.countPending());
		model.addAttribute("approvedValue", leadService.sumApprovedValue());
		return "admin-sales-reports";
	}

	// ════════════════════════════════════════════════════════════════════════
	// REPORTS — Performance
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/reports/performance")
	public String performanceReports(Model model) {
		return "admin-performance-reports";
	}

	// ════════════════════════════════════════════════════════════════════════
	// REPORTS — Revenue
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/reports/revenue")
	public String revenueReports(Model model) {
		return "admin-revenue-reports";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SYSTEM — Notifications
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/notifications")
	public String notifications(Model model) {
		return "admin-notifications";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SYSTEM — Settings
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/settings")
	public String settings(Model model) {
		return "admin-settings";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SYSTEM — Profile
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/profile")
	public String profile(Model model) {
		return "admin-profile";
	}
}