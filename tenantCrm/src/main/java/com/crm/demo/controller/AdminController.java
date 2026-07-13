package com.crm.demo.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.Meeting;
import com.crm.demo.model.Project;
import com.crm.demo.model.Report;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;
import com.crm.demo.service.AttendanceService;
import java.time.DayOfWeek;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
public class AdminController {

	private static final String ATTR_LOGGED_IN_USER = "loggedInUser";
	private static final String ROLE_ADMIN = "ADMIN";
	private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
	private static final String ROLE_MANAGER = "MANAGER";
	private static final String ROLE_EMPLOYEE = "EMPLOYEE";
	private static final String ROLE_HR = "HR";
	private static final String STATUS_ACTIVE = "active";
	private static final String STATUS_PENDING = "pending";
	private static final String STATUS_REJECTED = "rejected";
	private static final String TOTAL_EMPLOYEES = "totalEmployees";
	private static final String STATUS_DONE = "statusDone";
	private static final String STATUS_PENDING_LOWER = "statusPending";
	private static final String REDIRECT_ADMIN_EMPLOYEES = "redirect:/admin/employees";
	private static final String ATTR_ERROR_MESSAGE = "errorMessage";
	private static final String REDIRECT_ADMIN_ADD_EMPLOYEE = "redirect:/admin/add-employee";
	private static final String TENANT_DOMAIN_SUFFIX = "@crm.com).";
	private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
	private static final String REDIRECT_ADMIN_EDIT_EMPLOYEE = "redirect:/admin/edit-employee/";
	private static final String ATTR_UPCOMING_MEETINGS = "upcomingMeetings";
	private static final String ATTR_PAST_MEETINGS = "pastMeetings";
	private static final String ATTR_TENANT_USERS = "tenantUsers";
	private static final String ATTR_ACTIVE_PAGE = "activePage";
	private static final String PAGE_SCHEDULE_MEETING = "schedule-meeting";
	private static final String TEMPLATE_SCHEDULE_MEETING = "admin-scheduleMeeting";
	private static final String REDIRECT_ADMIN_SCHEDULE_MEETING = "redirect:/admin/schedule-meeting";
	private static final String MSG_MEETING_NOT_FOUND = "Meeting not found.";
	private static final String REDIRECT_ADMIN_SETTINGS = "redirect:/admin/settings";

	@Autowired
	private AttendanceService attendanceService;

	@Autowired
	private MeetingRepository meetingRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private ReportAttachmentRepository reportAttachmentRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private ProfileUpdateService profileUpdateService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private HolidayRepository holidayRepository;

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private com.crm.demo.repository.DomainCategoryRepository domainCategoryRepository;

	// =========================================================
	// COMMON USER DETAILS
	// =========================================================

	private void injectUser(HttpServletRequest request, Model model) {
		String username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		String role     = (String) request.getAttribute("loggedInRole");
		model.addAttribute("adminName", username != null ? username : "Admin");
		model.addAttribute("adminRole", role     != null ? role     : ROLE_ADMIN);
	}

	private String getLoggedInUsername(HttpServletRequest request) {
		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		if (username == null) {
			username = SecurityContextHolder.getContext().getAuthentication().getName();
		}
		return username;
	}

	private void populateMeetingModel(Model model, String tenant, String username) {
		model.addAttribute(ATTR_UPCOMING_MEETINGS, getUpcomingMeetingsForUser(tenant, username != null ? username : ""));
		model.addAttribute(ATTR_PAST_MEETINGS, getPastMeetingsForUser(tenant, username != null ? username : ""));
		model.addAttribute(ATTR_TENANT_USERS,
				userRepository.findByTenantSegment(tenant).stream()
						.filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole())
								  && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
						.collect(Collectors.toList()));
		model.addAttribute(ATTR_ACTIVE_PAGE, PAGE_SCHEDULE_MEETING);
	}

	private String validateUserData(String username, String email, String password, String confirmPassword, String role, String tenant) {
		var usernameError = validateUsername(username);
		if (usernameError != null) return usernameError;

		var emailError = validateEmail(email, tenant);
		if (emailError != null) return emailError;

		var passwordError = validatePassword(password, confirmPassword);
		if (passwordError != null) return passwordError;

		if (role == null || role.trim().isBlank() || (!ROLE_HR.equalsIgnoreCase(role) && !ROLE_MANAGER.equalsIgnoreCase(role) && !ROLE_EMPLOYEE.equalsIgnoreCase(role))) {
			return "Please select a valid role (HR, Manager, or Employee).";
		}
		return null;
	}

	private String checkEmployeeLimit(String adminUser, String tenant) {
		var currentAdmin = userRepository.findByUsername(adminUser);
		if (currentAdmin != null) {
			var employeeCount = userRepository.findByTenantSegment(tenant).stream()
					.filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole()) && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
					.count();
			int limit = currentAdmin.getEmployeeLimit() != null ? currentAdmin.getEmployeeLimit() : 10;
			if (employeeCount >= limit) {
				return "Employee limit reached (" + limit + "). You cannot add more employees.";
			}
		}
		return null;
	}

	private String checkBulkUploadLimit(String adminUser, String tenant, int toAddCount) {
		var currentAdmin = userRepository.findByUsername(adminUser);
		if (currentAdmin != null) {
			var existingCount = userRepository.findByTenantSegment(tenant).stream()
					.filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole()) && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
					.count();
			int limit = currentAdmin.getEmployeeLimit() != null ? currentAdmin.getEmployeeLimit() : 10;
			if (existingCount + toAddCount > limit) {
				return "Upload rejected. Adding " + toAddCount
						+ " employee(s) would exceed your limit of " + limit + " (current count: " + existingCount + ").";
			}
		}
		return null;
	}

	private String validateUpdateEmployee(User emp, String username, String email, String role, String tenant) {
		var usernameError = validateUsername(username);
		if (usernameError != null) return usernameError;

		var emailError = validateEmail(email, tenant);
		if (emailError != null) return emailError;

		if (role == null || role.trim().isBlank() || ROLE_ADMIN.equalsIgnoreCase(role) || ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
			return "You cannot assign that role.";
		}

		var existingUserByUname = userRepository.findByUsername(username.trim());
		if (existingUserByUname != null && !existingUserByUname.getId().equals(emp.getId())) {
			return "Username is already taken.";
		}
		var existingUserByEmail = userRepository.findByEmail(email.trim());
		if (existingUserByEmail != null && !existingUserByEmail.getId().equals(emp.getId())) {
			return "Email is already taken.";
		}
		return null;
	}

	private String validateUsername(String username) {
		if (username == null || username.trim().isBlank()) {
			return "Username is required.";
		}
		if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
			return "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.";
		}
		return null;
	}

	private String validateEmail(String email, String tenant) {
		if (email == null || email.trim().isBlank()) {
			return "Email is required.";
		}
		if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
			return "Please provide a valid email address.";
		}
		if (tenant != null && !tenant.isBlank() && !email.trim().contains("." + tenant + "@")) {
			return "Email must belong to your tenant domain (expected format: name." + tenant + TENANT_DOMAIN_SUFFIX;
		}
		return null;
	}

	private String validatePassword(String password, String confirmPassword) {
		if (password == null || password.length() < 4) {
			return "Password must be at least 4 characters long.";
		}
		if (!password.matches("^[A-Za-z0-9]+$")) {
			return "Password must contain only letters and numbers (no special characters).";
		}
		if (!password.equals(confirmPassword)) {
			return "Passwords do not match.";
		}
		return null;
	}

	/** Resolve the current admin's tenant segment from their email. */
	private String getTenantSegment(String username) {
		if (username == null) return "";
		var user = userRepository.findByUsername(username);
		if (user == null || user.getEmail() == null) return "";
		var email = user.getEmail();
		try {
			var local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
			int dot = local.lastIndexOf('.');
			return dot >= 0 ? local.substring(dot + 1) : local;
		} catch (Exception e) {
			return "";
		}
	}

	// =========================================================
	// DASHBOARD
	// =========================================================

	@GetMapping("/dashboard")
	public String dashboard(HttpServletRequest request, Model model) {
		injectUser(request, model);

		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		var employees = userRepository.findEmployeesByTenant(tenant);
		var projects  = projectRepository.findAll();
		var tasks     = tenant.isBlank() ? taskRepository.findAll() : taskRepository.findByTenantSegment(tenant);

		var activeProjects = projects.stream().filter(p -> STATUS_ACTIVE.equalsIgnoreCase(p.getStatus())).count();
		var tasksDone      = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		var pendingTasks   = tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();

		model.addAttribute(TOTAL_EMPLOYEES,  employees.size());
		model.addAttribute("employeeCount",   employees.size());
		model.addAttribute("employeeGrowth",  "+0%");
		model.addAttribute("activeProjects",  activeProjects);
		model.addAttribute("projectCount",    activeProjects);
		model.addAttribute("projectGrowth",   "+0%");
		model.addAttribute("tasksDone",       tasksDone);
		model.addAttribute("taskGrowth",      "+0%");
		model.addAttribute("overdueTasks",    pendingTasks);
		model.addAttribute("overdueChange",   "0%");
		model.addAttribute("pendingTasks",    pendingTasks);
		model.addAttribute("recentActivities", java.util.Collections.emptyList());
		addAnalyticsAttributes(model, dashboardAnalytics(request));

		return "admin-dashboard";
	}

	@GetMapping("/dashboard/analytics")
	@ResponseBody
	public Map<String, Object> dashboardAnalytics(HttpServletRequest request) {
		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant = getTenantSegment(username);

		var users = tenant.isBlank()
				? userRepository.findAll()
				: userRepository.findByTenantSegment(tenant);
		var employees = users.stream()
				.filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
				.filter(u -> !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
				.collect(Collectors.toList());
		var tasks = tenant.isBlank()
				? taskRepository.findAll()
				: taskRepository.findByTenantSegment(tenant);

		var data = buildDashboardAnalytics(tasks, employees);
		data.put(TOTAL_EMPLOYEES, employees.size());
		data.put("activeProjects", projectRepository.findAll().stream()
				.filter(p -> STATUS_ACTIVE.equalsIgnoreCase(p.getStatus()))
				.count());
		data.put("tasksDone", data.get(STATUS_DONE));
		data.put("pendingTaskTotal", data.get(STATUS_PENDING_LOWER));
		return data;
	}

	private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> employees) {
		Map<String, Object> data = new LinkedHashMap<>();
		List<Task> scopedTasks = tasks != null ? tasks : Collections.emptyList();
		List<User> scopedEmployees = employees != null ? employees : Collections.emptyList();

		var statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		var statusInProgress = scopedTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
		var statusPending = scopedTasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();
		var statusReview = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();
		var priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
		var priorityMedium = scopedTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
		var priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

		var memberLabels = new ArrayList<String>();
		var memberTaskCounts = new ArrayList<Long>();
		for (var employee : scopedEmployees) {
			if (ROLE_HR.equalsIgnoreCase(employee.getRole()) || ROLE_MANAGER.equalsIgnoreCase(employee.getRole())) {
				continue;
			}
			var count = scopedTasks.stream()
					.filter(t -> employee.getUsername() != null && employee.getUsername().equalsIgnoreCase(t.getAssignedTo()))
					.count();
			memberLabels.add(employee.getUsername());
			memberTaskCounts.add(count);
		}

		var activeCount = scopedEmployees.stream().filter(User::isActive).count();
		var inactiveCount = scopedEmployees.size() - activeCount;
		var verified = scopedTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
		var rejected = scopedTasks.stream().filter(t -> STATUS_REJECTED.equalsIgnoreCase(t.getVerificationStatus())).count();
		var waiting = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
		var unverified = scopedTasks.size() - verified - rejected - waiting;

		data.put(STATUS_DONE, statusDone);
		data.put("statusInProgress", statusInProgress);
		data.put(STATUS_PENDING_LOWER, statusPending);
		data.put("statusReview", statusReview);
		data.put("priorityHigh", priorityHigh);
		data.put("priorityMedium", priorityMedium);
		data.put("priorityLow", priorityLow);
		data.put("memberLabels", memberLabels);
		data.put("memberTaskCounts", memberTaskCounts);
		data.put("activeTeam", activeCount);
		data.put("inactiveTeam", inactiveCount);
		data.put("verified", verified);
		data.put(STATUS_REJECTED, rejected);
		data.put("waiting", waiting);
		data.put("unverified", Math.max(unverified, 0));
		data.put("totalMyTasks", scopedTasks.size());
		return data;
	}

	private void addAnalyticsAttributes(Model model, Map<String, Object> data) {
		model.addAttribute("chartStatusDone", data.get(STATUS_DONE));
		model.addAttribute("chartStatusInProgress", data.get("statusInProgress"));
		model.addAttribute("chartStatusPending", data.get(STATUS_PENDING_LOWER));
		model.addAttribute("chartStatusReview", data.get("statusReview"));
		model.addAttribute("chartPriorityHigh", data.get("priorityHigh"));
		model.addAttribute("chartPriorityMedium", data.get("priorityMedium"));
		model.addAttribute("chartPriorityLow", data.get("priorityLow"));
		model.addAttribute("chartMemberLabels", data.get("memberLabels"));
		model.addAttribute("chartMemberTaskCounts", data.get("memberTaskCounts"));
		model.addAttribute("chartActiveTeam", data.get("activeTeam"));
		model.addAttribute("chartInactiveTeam", data.get("inactiveTeam"));
		model.addAttribute("chartVerified", data.get("verified"));
		model.addAttribute("chartRejected", data.get(STATUS_REJECTED));
		model.addAttribute("chartWaiting", data.get("waiting"));
		model.addAttribute("chartUnverified", data.get("unverified"));
		model.addAttribute("chartTotalMyTasks", data.get("totalMyTasks"));
	}

	// =========================================================
	// EMPLOYEES — LIST PAGE  (sidebar "Employees" lands here)
	// =========================================================

	@GetMapping("/employees")
	public String employeesPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		var users = userRepository.findByTenantSegment(tenant).stream()
				.filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole())
						  && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
				.collect(Collectors.toList());

		var active   = users.stream().filter(User::isActive).count();
		var inactive = users.size() - active;

		model.addAttribute("managers",          users);
		model.addAttribute("totalManagers",     users.size());
		model.addAttribute(TOTAL_EMPLOYEES,    users.size());
		model.addAttribute("activeEmployees",   active);
		model.addAttribute("inactiveEmployees", inactive);
		return "add-users";
	}

	/**
	 * REST: GET /admin/api/employee/{id}
	 * Returns employee profile + last 30 days attendance as JSON for the modal.
	 */
	@GetMapping("/api/employee/{id}")
	@ResponseBody
	public Map<String, Object> employeeDetail(@PathVariable Long id, HttpServletRequest request) {
		attendanceService.processAutoPunchOuts();
		var resp = new LinkedHashMap<String, Object>();
		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		var user = userRepository.findById(id).orElse(null);
		if (user == null || (!ROLE_EMPLOYEE.equalsIgnoreCase(user.getRole())
				&& !ROLE_MANAGER.equalsIgnoreCase(user.getRole())
				&& !ROLE_HR.equalsIgnoreCase(user.getRole()))) {
			resp.put("error", "User not found.");
			return resp;
		}

		// Profile
		resp.put("id",       user.getId());
		resp.put("username", user.getUsername());
		resp.put("email",    user.getEmail());
		resp.put("role",     user.getRole());
		resp.put("status",   user.getStatus());

		// Last 30 days attendance
		var today = LocalDate.now();
		var from  = today.minusDays(29);
		var holidays = fetchHolidays(tenant, from, today);
		var records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(user, from, today);
		var days = buildDayList(records, from, today, holidays);

		// Stats
		var present  = days.stream().filter(d -> "present".equals(d.getStatus()) || "late".equals(d.getStatus())).count();
		var absent   = days.stream().filter(d -> "absent".equals(d.getStatus())).count();
		var halfDay  = days.stream().filter(d -> "half-day".equals(d.getStatus())).count();
		var holiday  = days.stream().filter(d -> "holiday".equals(d.getStatus())).count();
		resp.put("presentDays", present);
		resp.put("absentDays",  absent);
		resp.put("halfDays",    halfDay);
		resp.put("holidays",    holiday);

		// Attendance rows (last 30 days, newest first)
		var rows = new ArrayList<Map<String, String>>();
		for (var d : days) {
			var row = new LinkedHashMap<String, String>();
			row.put("date",      d.getDate().toString());
			row.put("checkIn",   d.getCheckInDisplay());
			row.put("checkOut",  d.getCheckOutDisplay());
			row.put("worked",    d.getWorkedHours());
			row.put("breakTime", d.getBreakDuration());
			row.put("dayType",   d.isReal() && d.getRecord().getCheckOut() != null ? d.getRecord().getDayType() : "—");
			row.put("status",    d.getStatus());
			rows.add(row);
		}
		resp.put("attendance", rows);
		return resp;
	}

	private List<AttendanceDay> buildDayList(List<Attendance> records,
											  LocalDate from, LocalDate to,
											  Map<LocalDate, String> holidays) {
		var byDate = new LinkedHashMap<LocalDate, Attendance>();
		for (var a : records) byDate.put(a.getDate(), a);

		var days = new ArrayList<AttendanceDay>();
		var today  = LocalDate.now();
		var cursor = to;
		while (!cursor.isBefore(from)) {
			if (holidays.containsKey(cursor)) {
				days.add(new AttendanceDay(cursor, holidays.get(cursor), true));
			} else {
				var dow = cursor.getDayOfWeek();
				if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
					days.add(new AttendanceDay(cursor, "weekend"));
				} else if (byDate.containsKey(cursor)) {
					days.add(new AttendanceDay(byDate.get(cursor)));
				} else if (!cursor.isAfter(today)) {
					days.add(new AttendanceDay(cursor, "absent"));
				}
			}
			cursor = cursor.minusDays(1);
		}
		return days;
	}

	private Map<LocalDate, String> fetchHolidays(String tenant, LocalDate from, LocalDate to) {
		Map<LocalDate, String> map = new LinkedHashMap<>();
		if (tenant == null || tenant.isBlank()) return map;
		List<Holiday> list = holidayRepository.findByTenantAndDateRange(
				tenant, from.toString(), to.toString());
		for (Holiday h : list) map.put(LocalDate.parse(h.getDate()), h.getName());
		return map;
	}

	// =========================================================
	// EMPLOYEES — ADD FORM PAGE
	// =========================================================

	@GetMapping("/add-employee")
	public String addEmployeePage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		var tenant = getTenantSegment(getLoggedInUsername(request));
		model.addAttribute("domainCategories", domainCategoryRepository.findByTenantSegment(tenant));
		return "admin-add-employee";
	}

	// =========================================================
	// EMPLOYEES — ADD USER (POST)
	// =========================================================

	@GetMapping("/add-user")
	public String addUserRedirect() {
		return REDIRECT_ADMIN_EMPLOYEES;
	}

	@PostMapping("/add-user")
	public String addUser(@RequestParam String email,
	                      @RequestParam String username,
	                      @RequestParam String password,
	                      @RequestParam String confirmPassword,
	                      @RequestParam String role,
	                      @RequestParam(required = false) String domain,
	                      @RequestParam(required = false) String joiningDate,
	                      HttpServletRequest request,
	                      RedirectAttributes ra) {

		var adminUser = getLoggedInUsername(request);
		var tenant = getTenantSegment(adminUser);

		var validationError = validateUserData(username, email, password, confirmPassword, role, tenant);
		if (validationError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		var limitError = checkEmployeeLimit(adminUser, tenant);
		if (limitError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, limitError);
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		if (userRepository.findByEmail(email.trim()) != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Email already in use.");
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		if (userRepository.findByUsername(username.trim()) != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Username already in use.");
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		var user = new User();
		user.setEmail(email.trim());
		user.setUsername(username.trim());
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(role.toUpperCase());
		user.setStatus(STATUS_ACTIVE);

		if (domain != null && !domain.trim().isEmpty()) {
			user.setDomain(domain.trim());
		}
		
		var parsedJoiningDate = LocalDate.now();
		if (joiningDate != null && !joiningDate.trim().isEmpty()) {
			try {
				parsedJoiningDate = LocalDate.parse(joiningDate.trim());
			} catch (Exception e) {
				// use default today
			}
		}
		user.setJoiningDate(parsedJoiningDate);

		userRepository.save(user);

		notificationService.notifyEmployeeManagementChanged(tenant, "added", username.trim());

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Employee added successfully.");
		return REDIRECT_ADMIN_EMPLOYEES;
	}

	// =========================================================
	// BULK EMPLOYEE UPLOAD (Excel)
	//  Expected columns (row 0 = header, skipped):
	//    A: username  B: email  C: password  D: role
	//
	//  Validate ALL rows first — any error rejects the whole file.
	// =========================================================

	@PostMapping("/bulk-upload")
	public String bulkUpload(@RequestParam("file") MultipartFile file,
	                         HttpServletRequest request,
	                         RedirectAttributes ra) {

		if (file == null || file.isEmpty()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Please select an Excel file to upload.");
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}
		var originalFilename = file.getOriginalFilename();
		if (originalFilename == null ||
				(!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Only .xlsx or .xls files are supported.");
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		var username = getLoggedInUsername(request);
		var segment  = getTenantSegment(username);

		var errors = new ArrayList<String>();
		var toSave = new ArrayList<User>();

		try (var is = file.getInputStream();
		     var workbook = new XSSFWorkbook(is)) {

			var sheet = workbook.getSheetAt(0);
			if (sheet.getLastRowNum() < 1) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "The file has no data rows.");
				return REDIRECT_ADMIN_ADD_EMPLOYEE;
			}

			for (var rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				var row = sheet.getRow(rowIndex);
				if (row != null) {
					processUploadRow(row, rowIndex, segment, errors, toSave);
				}
			}

		} catch (Exception e) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Failed to parse file: " + e.getMessage());
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		// Any errors → reject all
		if (!errors.isEmpty()) {
			ra.addFlashAttribute("bulkErrors", errors);
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE,
					"Upload rejected — " + errors.size() + " error(s) found. No employees were saved.");
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		if (toSave.isEmpty()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No valid data rows found in the file.");
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		// Enforce employee limit for bulk upload
		var limitError = checkBulkUploadLimit(username, segment, toSave.size());
		if (limitError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, limitError);
			return REDIRECT_ADMIN_ADD_EMPLOYEE;
		}

		userRepository.saveAll(toSave);
		notificationService.notifyEmployeeManagementChanged(segment, "uploaded", "multiple");
		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, toSave.size() + " employee(s) imported successfully.");
		return REDIRECT_ADMIN_EMPLOYEES;
	}

	private void processUploadRow(Row row, int rowIndex, String segment, List<String> errors, List<User> toSave) {
		var uname = getAdminCellString(row, 0);
		var email = getAdminCellString(row, 1);
		var pwd   = getAdminCellString(row, 2);
		var role  = getAdminCellString(row, 3);

		// Skip fully blank rows
		if (uname.isBlank() && email.isBlank() && pwd.isBlank() && role.isBlank()) {
			return;
		}

		var rowLabel = "Row " + (rowIndex + 1);

		if (uname.isBlank()) { errors.add(rowLabel + ": username is empty."); return; }
		if (email.isBlank()) { errors.add(rowLabel + " (" + uname + "): email is empty."); return; }
		if (pwd.isBlank())   { errors.add(rowLabel + " (" + uname + "): password is empty."); return; }
		if (role.isBlank())  { errors.add(rowLabel + " (" + uname + "): role is empty."); return; }

		if (ROLE_ADMIN.equalsIgnoreCase(role) || ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
			errors.add(rowLabel + " (" + uname + "): role '" + role + "' is not allowed.");
			return;
		}
		if (segment != null && !segment.isBlank() && !email.contains("." + segment + "@")) {
			errors.add(rowLabel + " (" + uname + "): email '" + email
					+ "' does not belong to tenant domain (expected: name." + segment + TENANT_DOMAIN_SUFFIX);
			return;
		}
		if (userRepository.existsByUsernameOrEmail(uname, email)) {
			errors.add(rowLabel + " (" + uname + "): username or email already exists in the system.");
			return;
		}
		var dupInFile = toSave.stream().anyMatch(u ->
				u.getUsername().equalsIgnoreCase(uname) || u.getEmail().equalsIgnoreCase(email));
		if (dupInFile) {
			errors.add(rowLabel + " (" + uname + "): username or email is duplicated within this file.");
			return;
		}

		var user = new User();
		user.setUsername(uname);
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(pwd));
		user.setRole(role.toUpperCase());
		user.setStatus(STATUS_ACTIVE);
		toSave.add(user);
	}

	/** Safely read a cell value as a trimmed String. */
	private String getAdminCellString(Row row, int col) {
		var cell = row.getCell(col);
		if (cell == null) return "";
		if (cell.getCellType() == CellType.STRING)  return cell.getStringCellValue().trim();
		if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue()).trim();
		if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue()).trim();
		return "";
	}

	// =========================================================
	// EMPLOYEES — TOGGLE STATUS
	// =========================================================

	@PostMapping("/toggle-user/{id}")
	public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
		var user = userRepository.findById(id).orElse(null);
		if (user != null
				&& !ROLE_ADMIN.equalsIgnoreCase(user.getRole())
				&& !ROLE_SUPER_ADMIN.equalsIgnoreCase(user.getRole())) {
			var newStatus = STATUS_ACTIVE.equalsIgnoreCase(user.getStatus()) ? "inactive" : STATUS_ACTIVE;
			user.setStatus(newStatus);
			userRepository.save(user);

			var adminName = SecurityContextHolder.getContext().getAuthentication().getName();
			var tenant = getTenantSegment(adminName);
			notificationService.notifyEmployeeManagementChanged(tenant, "updated", user.getUsername());

			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, user.getUsername() + " is now " + newStatus + ".");
		}
		return REDIRECT_ADMIN_EMPLOYEES;
	}

	// =========================================================
	// EMPLOYEES — DELETE
	// =========================================================

	@PostMapping("/delete-employee/{id}")
	public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
		var user = userRepository.findById(id).orElse(null);
		if (user != null
				&& !ROLE_ADMIN.equalsIgnoreCase(user.getRole())
				&& !ROLE_SUPER_ADMIN.equalsIgnoreCase(user.getRole())) {
			var name = user.getUsername();
			
			var adminName = SecurityContextHolder.getContext().getAuthentication().getName();
			var tenant = getTenantSegment(adminName);
			notificationService.deleteAllForUser(user.getId());
			
			userRepository.delete(user);
			
			notificationService.notifyEmployeeManagementChanged(tenant, "deleted", name);

			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Employee '" + name + "' deleted.");
		}
		return REDIRECT_ADMIN_EMPLOYEES;
	}

	// =========================================================
	// EMPLOYEES — EDIT
	// =========================================================

	@GetMapping("/edit-employee/{id}")
	public String editEmployeePage(@PathVariable Long id, HttpServletRequest request, Model model,
	                               RedirectAttributes ra) {
		injectUser(request, model);
		var emp = userRepository.findById(id).orElse(null);
		if (emp == null
				|| ROLE_ADMIN.equalsIgnoreCase(emp.getRole())
				|| ROLE_SUPER_ADMIN.equalsIgnoreCase(emp.getRole())) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "User not found or cannot be edited.");
			return REDIRECT_ADMIN_EMPLOYEES;
		}
		var tenant = getTenantSegment(getLoggedInUsername(request));
		model.addAttribute("domainCategories", domainCategoryRepository.findByTenantSegment(tenant));
		model.addAttribute("employee", emp);
		return "edit-employee";
	}

	@PostMapping("/edit-employee/{id}")
	public String updateEmployee(@PathVariable Long id,
	                             @RequestParam String username,
	                             @RequestParam String email,
	                             @RequestParam String role,
	                             @RequestParam(required = false) String password,
	                             @RequestParam(required = false) String confirmPassword,
	                             @RequestParam(required = false) String domain,
	                             @RequestParam(required = false) String joiningDate,
	                             HttpServletRequest request,
	                             RedirectAttributes ra) {
		var emp = userRepository.findById(id).orElse(null);
		if (emp == null
				|| ROLE_ADMIN.equalsIgnoreCase(emp.getRole())
				|| ROLE_SUPER_ADMIN.equalsIgnoreCase(emp.getRole())) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "User not found or cannot be edited.");
			return REDIRECT_ADMIN_EMPLOYEES;
		}

		var adminName = getLoggedInUsername(request);
		var tenant = getTenantSegment(adminName);

		var validationError = validateUpdateEmployee(emp, username, email, role, tenant);
		if (validationError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
			return REDIRECT_ADMIN_EDIT_EMPLOYEE + id;
		}

		emp.setUsername(username.trim());
		emp.setEmail(email.trim());

		if (password != null && !password.isBlank()) {
			var passwordError = validatePassword(password, confirmPassword);
			if (passwordError != null) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, passwordError);
				return REDIRECT_ADMIN_EDIT_EMPLOYEE + id;
			}
			emp.setPassword(passwordEncoder.encode(password));
		}
		emp.setRole(role.toUpperCase());
		if (domain != null && !domain.trim().isEmpty()) {
			emp.setDomain(domain.trim());
		}
		if (joiningDate != null && !joiningDate.trim().isEmpty()) {
			try {
				emp.setJoiningDate(LocalDate.parse(joiningDate.trim()));
			} catch (Exception e) {
				// Keep existing
			}
		}
		userRepository.save(emp);

		notificationService.notifyEmployeeManagementChanged(tenant, "updated", emp.getUsername());

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "'" + emp.getUsername() + "' updated successfully.");
		return REDIRECT_ADMIN_EMPLOYEES;
	}



	// =========================================================
	// TASKS
	// =========================================================

	@GetMapping("/tasks")
	public String tasksPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		var tasks = tenant.isBlank()
				? taskRepository.findAll()
				: taskRepository.findByTenantSegment(tenant);
		tasks.sort(java.util.Comparator.comparing(Task::getId).reversed());

		var done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		var pending = tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();

		var teams = tenant.isBlank()
				? teamRepository.findAll()
				: teamRepository.findByTenantSegmentOrderByIdDesc(tenant);

		model.addAttribute("tasks",            tasks);
		model.addAttribute("totalTasks",       tasks.size());
		model.addAttribute("doneTasks",        done);
		model.addAttribute("pendingTaskCount", pending);
		model.addAttribute("teams",            teams);

		return "admin-tasks";
	}


	// =========================================================
	// REPORTS
	// =========================================================

	@GetMapping("/reports")
	public String reportsPage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		String tenant   = getTenantSegment(username);

		List<User>    allUsers  = userRepository.findByTenantSegment(tenant);
		List<Project> projects  = projectRepository.findAll();
		List<Task>    tasks     = taskRepository.findAll();

		long roleAdmin    = allUsers.stream().filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole())).count();
		long roleEmployee = allUsers.stream().filter(u -> ROLE_EMPLOYEE.equalsIgnoreCase(u.getRole())).count();
		long roleManager  = allUsers.stream().filter(u -> ROLE_MANAGER.equalsIgnoreCase(u.getRole())).count();
		long roleHr       = allUsers.stream().filter(u -> "HR".equalsIgnoreCase(u.getRole())).count();

		model.addAttribute("reportUsers",     allUsers);
		model.addAttribute("reportEmployees", allUsers.size());
		model.addAttribute("reportProjects",  projects.size());
		model.addAttribute("reportTasks",     tasks.size());
		model.addAttribute("roleAdmin",       roleAdmin);
		model.addAttribute("roleEmployee",    roleEmployee);
		model.addAttribute("roleManager",     roleManager);
		model.addAttribute("roleHr",          roleHr);

		// Manager-sent reports for this tenant
		var admin = userRepository.findByUsername(username);
		List<Report> allReports;
		if (admin != null) {
			allReports = reportRepository.findByRecipientId(String.valueOf(admin.getId()), tenant);
		} else {
			allReports = java.util.Collections.emptyList();
		}
		model.addAttribute("allReports",  allReports);
		model.addAttribute("reportCount", allReports.size());

		return "admin-reports";
	}

	@GetMapping("/reports/view/{attachmentId}")
	public org.springframework.http.ResponseEntity<byte[]> viewReportAttachment(
			@PathVariable Long attachmentId) {
		var att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return org.springframework.http.ResponseEntity.notFound().build();
		var ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
		return org.springframework.http.ResponseEntity.ok()
				.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
						"inline; filename=\"" + att.getOriginalFilename() + "\"")
				.header(org.springframework.http.HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	@GetMapping("/reports/download/{attachmentId}")
	public org.springframework.http.ResponseEntity<byte[]> downloadReportAttachment(
			@PathVariable Long attachmentId) {
		var att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return org.springframework.http.ResponseEntity.notFound().build();
		var ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
		return org.springframework.http.ResponseEntity.ok()
				.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + att.getOriginalFilename() + "\"")
				.header(org.springframework.http.HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	// =========================================================
	// CALENDAR
	// =========================================================

	@GetMapping("/calendar")
	public String calendarPage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		return "calendar";
	}

	// =========================================================
	// SETTINGS
	// =========================================================

	@GetMapping("/profile")
	public String profilePage(HttpServletRequest request, Model model) {
		injectUser(request, model);

		String username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var admin = userRepository.findByUsername(username);

		model.addAttribute("adminEmail",         admin != null ? admin.getEmail() : "");
		model.addAttribute("settingsTotalUsers", userRepository.count());

		return "admin-profile";
	}

	@PostMapping("/update-profile")
	public String updateProfile(@RequestParam(required = false) String username,
	                            @RequestParam(required = false) String email,
	                            @RequestParam(required = false) String password,
	                            @RequestParam(required = false) String confirmPassword,
	                            HttpServletResponse response,
	                            RedirectAttributes ra) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		var admin = userRepository.findByUsername(currentUsername);

		if (admin == null) {
			return "redirect:/admin/profile?error=auth";
		}

		var success = profileUpdateService.updateProfile(admin, username, email, password, confirmPassword, ra, response);
		return success ? "redirect:/admin/profile?success" : "redirect:/admin/profile?error=validation";
	}

	// =========================================================
	// SCHEDULE MEETING — GET PAGE
	// =========================================================

	/**
	 * Returns upcoming meetings (today + future) where the admin is a participant
	 * or the creator/host who scheduled the meeting.
	 * Excludes today's meetings that have already ended.
	 */
	private List<Meeting> getUpcomingMeetingsForUser(String tenant, String username) {
		var all = meetingRepository
				.findUpcomingMeetingsForUserOrHost(tenant, username, LocalDate.now());
		var today = LocalDate.now();
		var now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (!m.getMeetingDate().equals(today)) return true;
			if (m.getMeetingTime() == null) return true;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).collect(Collectors.toList());
	}

	private List<Meeting> getPastMeetingsForUser(String tenant, String username) {
		var all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
		var today = LocalDate.now();
		var now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (m.getMeetingDate() == null) return false;
			if (m.getMeetingDate().isAfter(today)) return false;
			if (m.getMeetingDate().isBefore(today)) return true;
			if (m.getMeetingTime() == null) return false;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).collect(Collectors.toList());
	}

	@GetMapping("/schedule-meeting")
	public String scheduleMeetingPage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		model.addAttribute("meetingForm", new Meeting());
		populateMeetingModel(model, tenant, username);
		return TEMPLATE_SCHEDULE_MEETING;
	}

	// =========================================================
	// SCHEDULE MEETING — SAVE
	// =========================================================

	@PostMapping("/schedule-meeting")
	public String scheduleMeeting(@Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                              BindingResult result,
	                              HttpServletRequest request,
	                              Model model,
	                              RedirectAttributes ra) {

		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

		if (result.hasErrors()) {
			injectUser(request, model);
			populateMeetingModel(model, tenant, username);
			model.addAttribute(ATTR_ERROR_MESSAGE, "Please fix the errors below.");
			return TEMPLATE_SCHEDULE_MEETING;
		}

		meetingForm.setTenantSegment(tenant);
		meetingForm.setScheduledBy(username != null ? username : "");
		meetingRepository.save(meetingForm);
		notificationService.notifyMeetingParticipants(meetingForm);
		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting scheduled successfully.");
		return REDIRECT_ADMIN_SCHEDULE_MEETING;
	}

	// =========================================================
	// SCHEDULE MEETING — EDIT PAGE
	// =========================================================

	@GetMapping("/schedule-meeting/edit/{id}")
	public String editMeetingPage(@PathVariable Long id,
	                              HttpServletRequest request,
	                              Model model,
	                              RedirectAttributes ra) {

		injectUser(request, model);
		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		var meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
			return REDIRECT_ADMIN_SCHEDULE_MEETING;
		}

		model.addAttribute("meetingForm", meeting);
		populateMeetingModel(model, tenant, username);
		return TEMPLATE_SCHEDULE_MEETING;
	}

	// =========================================================
	// SCHEDULE MEETING — UPDATE
	// =========================================================

	@PostMapping("/schedule-meeting/edit/{id}")
	public String updateMeeting(@PathVariable Long id,
	                            @Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                            BindingResult result,
	                            HttpServletRequest request,
	                            Model model,
	                            RedirectAttributes ra) {

		var username = (String) request.getAttribute(ATTR_LOGGED_IN_USER);
		var tenant   = getTenantSegment(username);

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

		if (result.hasErrors()) {
			injectUser(request, model);
			populateMeetingModel(model, tenant, username);
			return TEMPLATE_SCHEDULE_MEETING;
		}

		var existing = meetingRepository.findById(id).orElse(null);
		if (existing == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
			return REDIRECT_ADMIN_SCHEDULE_MEETING;
		}

		existing.setTitle(meetingForm.getTitle());
		existing.setMeetingDate(meetingForm.getMeetingDate());
		existing.setMeetingTime(meetingForm.getMeetingTime());
		existing.setDuration(meetingForm.getDuration());
		existing.setMeetingType(meetingForm.getMeetingType());
		existing.setLocation(meetingForm.getLocation());
		existing.setParticipants(meetingForm.getParticipants());
		existing.setAgenda(meetingForm.getAgenda());
		existing.setSendNotification(meetingForm.isSendNotification());
		meetingRepository.save(existing);
		notificationService.notifyMeetingParticipants(existing);

		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting updated successfully.");
		return REDIRECT_ADMIN_SCHEDULE_MEETING;
	}

	// =========================================================
	// SCHEDULE MEETING — DELETE
	// =========================================================

	@PostMapping("/schedule-meeting/delete/{id}")
	public String deleteMeeting(@PathVariable Long id, RedirectAttributes ra) {
		var meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_MEETING_NOT_FOUND);
		} else {
			meetingRepository.delete(meeting);
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Meeting deleted successfully.");
		}
		return REDIRECT_ADMIN_SCHEDULE_MEETING;
	}

	@GetMapping("/settings")
	public String settingsPage(HttpServletRequest request, Model model) {
		injectUser(request, model);
		var tenant = getTenantSegment(getLoggedInUsername(request));
		model.addAttribute("categories", domainCategoryRepository.findByTenantSegment(tenant));
		model.addAttribute(ATTR_ACTIVE_PAGE, "settings");
		return "admin-settings";
	}

	@PostMapping("/settings/domain-categories")
	public String addDomainCategory(@RequestParam String name, HttpServletRequest request, RedirectAttributes ra) {
		var tenant = getTenantSegment(getLoggedInUsername(request));
		if (name == null || name.trim().isEmpty()) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Domain category name is required.");
			return REDIRECT_ADMIN_SETTINGS + "?error=required";
		}
		var cleanName = name.trim();
		if (domainCategoryRepository.existsByNameAndTenantSegment(cleanName, tenant)) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Domain category already exists.");
			return REDIRECT_ADMIN_SETTINGS + "?error=exists";
		}
		var cat = new com.crm.demo.model.DomainCategory();
		cat.setName(cleanName);
		cat.setTenantSegment(tenant);
		domainCategoryRepository.save(cat);
		ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Domain category '" + cleanName + "' added successfully.");
		return REDIRECT_ADMIN_SETTINGS + "?success";
	}

	@PostMapping("/settings/domain-categories/delete/{id}")
	public String deleteDomainCategory(@PathVariable Long id, HttpServletRequest request, RedirectAttributes ra) {
		var tenant = getTenantSegment(getLoggedInUsername(request));
		var catOpt = domainCategoryRepository.findById(id);
		if (catOpt.isPresent() && tenant.equals(catOpt.get().getTenantSegment())) {
			domainCategoryRepository.delete(catOpt.get());
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Domain category deleted successfully.");
			return REDIRECT_ADMIN_SETTINGS + "?success=delete";
		} else {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Domain category not found.");
			return REDIRECT_ADMIN_SETTINGS + "?error=notfound";
		}
	}
}
