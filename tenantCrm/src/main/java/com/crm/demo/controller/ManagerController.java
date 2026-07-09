package com.crm.demo.controller;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.validation.BindingResult;
import java.util.stream.Collectors;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.PerformanceReview;
import com.crm.demo.model.Project;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.model.Task;
import com.crm.demo.model.TaskAttachment;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.PerformanceReviewRepository;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TaskAttachmentRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.model.PayrollTemplate;
import com.crm.demo.repository.PayrollTemplateRepository;
import com.crm.demo.model.Payslip;
import com.crm.demo.repository.PayslipRepository;
import com.crm.demo.service.PayslipService;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;
import com.crm.demo.service.AttendanceService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	private static final String ROLE_ADMIN = "ADMIN";
	private static final String ROLE_MANAGER = "MANAGER";
	private static final String ROLE_EMPLOYEE = "EMPLOYEE";
	private static final String ROLE_HR = "HR";
	private static final String ATTR_ERROR = "error";
	private static final String ATTR_ERROR_MESSAGE = "errorMessage";
	private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
	private static final String REDIRECT_PREFIX = "redirect:";
	private static final String REDIRECT_MANAGER_TASKS = "redirect:/manager/tasks";
	private static final String REDIRECT_MANAGER_LEAVES = "redirect:/manager/leaves";
	private static final String REDIRECT_MANAGER_MEETINGS = "redirect:/manager/meetings";
	private static final String REDIRECT_MANAGER_ATTENDANCE = "redirect:/manager/attendance";
	private static final String STATUS_PENDING = "pending";
	private static final String STATUS_APPROVED = "Approved";
	private static final String STATUS_REJECTED = "rejected";
	private static final String STATUS_IN_PROGRESS = "in-progress";
	private static final String STATUS_WAITING_FOR_REVIEW = "waiting-for-review";
	private static final String PRIORITY_MEDIUM = "Medium";
	private static final String STATUS_PRESENT = "present";
	private static final String STATUS_ABSENT = "absent";
	private static final String OCTET_STREAM = "application/octet-stream";
	private static final String TIME_FORMAT = "%02d:%02d";
	private static final String MSG_SESSION_EXPIRED = "Session expired. Please log in again.";
	private static final String MSG_MEETING_NOT_FOUND = "Meeting not found.";
	private static final String MSG_NOT_PUNCHED_IN = "You haven't punched in today.";
	private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
	private static final String ATTR_TEAM_MEMBERS = "teamMembers";
	private static final String ATTR_ACTIVE_TEAM = "activeTeam";
	private static final String ATTR_INACTIVE_TEAM = "inactiveTeam";
	private static final String ATTR_TASKS = "tasks";
	private static final String ATTR_TOTAL_TASKS = "totalTasks";
	private static final String ATTR_DONE_TASKS = "doneTasks";
	private static final String ATTR_PENDING_TASK_COUNT = "pendingTaskCount";
	private static final String ATTR_MEETINGS = "meetings";
	private static final String ATTR_PAST_MEETINGS = "pastMeetings";
	private static final String ATTR_MEETING_FORM = "meetingForm";
	private static final String PAGE_MEETINGS = "manager-meetings";
	private static final String ATTR_PERF_LIST = "perfList";
	private static final String ATTR_SELECTED_MONTH = "selectedMonth";
	private static final String REDIRECT_MANAGER_REPORTS = "redirect:/manager/reports";
	private static final String REDIRECT_MANAGER_PERFORMANCE = "redirect:/manager/performance?month=";

	@Value("${app.upload.dir:uploads/tasks}")
	private String uploadDir;

	@Autowired
	private AttendanceService attendanceService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private AttendanceRepository attendanceRepository;

	@Autowired
	private HolidayRepository holidayRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private MeetingRepository meetingRepository;

	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	@Autowired
	private TaskAttachmentRepository taskAttachmentRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private ReportAttachmentRepository reportAttachmentRepository;

	@Autowired
	private PerformanceReviewRepository performanceReviewRepository;

	@Autowired
	private ProfileUpdateService profileUpdateService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private PayrollTemplateRepository payrollTemplateRepository;

	@Autowired
	private PayslipRepository payslipRepository;

	@Autowired
	private PayslipService payslipService;
	// =========================
	private void injectStats(Model model) {

		// Logged-in manager username
		var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

		var manager = userRepository.findByUsername(currentUsername);

		// Safety check
		if (manager == null) {
			model.addAttribute("managerName", "Manager");
			model.addAttribute("managerEmail", "");
			model.addAttribute(ATTR_TEAM_MEMBERS, Collections.emptyList());
			model.addAttribute("teamCount", 0);
			model.addAttribute(ATTR_ACTIVE_TEAM, 0);
			model.addAttribute(ATTR_INACTIVE_TEAM, 0);
			model.addAttribute("myTeam", null);
			model.addAttribute("myTeamName", "—");
			model.addAttribute("managedTeams", Collections.emptyList());
			model.addAttribute("projects", Collections.emptyList());
			model.addAttribute("totalProjects", 0);
			model.addAttribute("activeProjects", 0);
			model.addAttribute("completedProjects", 0);
			model.addAttribute("projectCount", 0);
			model.addAttribute(ATTR_TASKS, Collections.emptyList());
			model.addAttribute(ATTR_TOTAL_TASKS, 0);
			model.addAttribute(ATTR_DONE_TASKS, 0);
			model.addAttribute(ATTR_PENDING_TASK_COUNT, 0);
			model.addAttribute("taskCount", 0);
			model.addAttribute("overdueTasks", 0);
			model.addAttribute("notificationCount", 0);
			model.addAttribute("pendingTaskList", Collections.emptyList());
			return;
		}

		model.addAttribute("managerName", manager.getUsername());
		model.addAttribute("managerEmail", manager.getEmail());

		// ── Load team(s) assigned to this manager ────────────────────────────
		var myTeam = getPrimaryTeam(manager);
		var managedTeams = getManagedTeams(manager);
		var teamMembers = getManagedTeamMembers(manager);

		var active   = teamMembers.stream().filter(User::isActive).count();
		var inactive = teamMembers.size() - active;

		model.addAttribute("myTeam",      myTeam);
		model.addAttribute("myTeamName",  getManagedTeamName(manager));
		model.addAttribute("managedTeams",managedTeams);
		model.addAttribute(ATTR_TEAM_MEMBERS, teamMembers);
		model.addAttribute("teamCount",   teamMembers.size());
		model.addAttribute(ATTR_ACTIVE_TEAM,  active);
		model.addAttribute(ATTR_INACTIVE_TEAM,inactive);

		// ── Projects & Tasks ──────────────────────────────────────────────
		var projects = projectRepository.findAll();
		projects.sort(java.util.Comparator.comparing(Project::getId).reversed());
		// For stats only — scoped tasks loaded per-page where needed
		var tasks    = taskRepository.findAll();

		var done    = tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count(); // Wait, let's keep status check as 'done'
		var doneCount = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		var pending = tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();
		var activeP = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();
		var completedP = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

		model.addAttribute("projects",          projects);
		model.addAttribute("totalProjects",     projects.size());
		model.addAttribute("activeProjects",    activeP);
		model.addAttribute("completedProjects", completedP);
		model.addAttribute("projectCount",      projects.size());

		model.addAttribute(ATTR_TASKS,             tasks);
		model.addAttribute(ATTR_TOTAL_TASKS,        tasks.size());
		model.addAttribute(ATTR_DONE_TASKS,         doneCount);
		model.addAttribute(ATTR_PENDING_TASK_COUNT,  pending);
		model.addAttribute("taskCount",         tasks.size());
		model.addAttribute("overdueTasks",      pending);
		model.addAttribute("notificationCount", 0);
		model.addAttribute("pendingTaskList",   Collections.emptyList());
	}

	private String validateTaskParams(String title, String description, String priority, String status, String dueDate, String startDate) {
		if (title == null || title.trim().isBlank()) {
			return "Task title is required.";
		}
		if (title.trim().length() > 255) {
			return "Task title cannot exceed 255 characters.";
		}
		if (description != null && description.length() > 255) {
			return "Description cannot exceed 255 characters.";
		}
		if (priority == null || (!"High".equalsIgnoreCase(priority) && !PRIORITY_MEDIUM.equalsIgnoreCase(priority) && !"Low".equalsIgnoreCase(priority))) {
			return "Invalid priority selected.";
		}
		if (status == null || (!STATUS_PENDING.equalsIgnoreCase(status) && !STATUS_IN_PROGRESS.equalsIgnoreCase(status) && !"done".equalsIgnoreCase(status))) {
			return "Invalid status selected.";
		}
		if (dueDate == null || dueDate.trim().isBlank() || !dueDate.trim().matches(DATE_REGEX)) {
			return "Please select a valid due date.";
		}
		return null;
	}

	private String validateLeaveParams(String type, String reason, String fromDate, String toDate) {
		if (type == null || type.isBlank()) {
			return "Leave type is required.";
		}
		if (reason == null || reason.isBlank()) {
			return "Leave reason is required.";
		}
		if (reason.trim().length() > 255) {
			return "Reason cannot exceed 255 characters.";
		}
		if (fromDate == null || fromDate.isBlank() || !fromDate.matches(DATE_REGEX)) {
			return "Invalid start date format.";
		}
		if (toDate == null || toDate.isBlank() || !toDate.matches(DATE_REGEX)) {
			return "Invalid end date format.";
		}
		return null;
	}

	private List<EmployeePerf> computeEmployeePerformance(List<User> teamMembers, java.time.YearMonth ym, String tenant) {
		var from = ym.atDay(1);
		var to   = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();

		// Working days in the selected range (Mon–Fri, excluding today if future)
		var workingDays = 0;
		var d = from;
		while (!d.isAfter(to)) {
			var dow = d.getDayOfWeek();
			if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workingDays++;
			d = d.plusDays(1);
		}
		if (workingDays < 1) workingDays = 1;

		var perfList = new ArrayList<EmployeePerf>();

		for (var emp : teamMembers) {
			var p = new EmployeePerf();
			p.setEmployee(emp);

			// ── Task stats ──────────────────────────────────────────────
			var tasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
			p.setTotalTasks(tasks.size());
			p.setDoneTasks((int) tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count());
			p.setPendingTasks((int) tasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count());
			// Overdue = pending/in-progress tasks whose due date is before today
			p.setOverdueTasks((int) tasks.stream()
					.filter(t -> !"done".equalsIgnoreCase(t.getStatus())
							&& t.getDueDate() != null
							&& !t.getDueDate().isBlank()
							&& LocalDate.parse(t.getDueDate()).isBefore(LocalDate.now()))
					.count());

			// Task score: done% out of total, penalise overdue
			if (p.getTotalTasks() > 0) {
				double raw = ((double) p.getDoneTasks() / p.getTotalTasks()) * 100.0
						- (p.getOverdueTasks() * 5.0);
				p.setTaskScore((int) Math.max(0, Math.min(100, raw)));
			} else {
				p.setTaskScore(100); // no tasks = no penalty
			}

			// ── Attendance stats (selected month) ───────────────────────
			var attRecords = attendanceRepository
					.findByUserAndDateBetweenOrderByDateDesc(emp, from, to);

			p.setPresentDays((int) attRecords.stream()
					.filter(a -> STATUS_PRESENT.equalsIgnoreCase(a.getStatus())).count());
			p.setLateDays((int) attRecords.stream()
					.filter(a -> "late".equalsIgnoreCase(a.getStatus())).count());

			// Leave days in this month
			var leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp);
			var leaveDaysCount = 0;
			for (var lr : leaves) {
				if (STATUS_APPROVED.equalsIgnoreCase(lr.getStatus()) && lr.getFromDate() != null && lr.getToDate() != null) {
					var lStart = lr.getFromDate().isBefore(from) ? from : lr.getFromDate();
					var lEnd   = lr.getToDate().isAfter(to)      ? to   : lr.getToDate();
					if (!lStart.isAfter(lEnd)) {
						var c = lStart;
						while (!c.isAfter(lEnd)) {
							var dow2 = c.getDayOfWeek();
							if (dow2 != java.time.DayOfWeek.SATURDAY && dow2 != java.time.DayOfWeek.SUNDAY) leaveDaysCount++;
							c = c.plusDays(1);
						}
					}
				}
			}
			p.setLeaveDays(leaveDaysCount);
			var effectiveWorking = Math.max(1, workingDays - p.getLeaveDays());
			p.setAbsentDays(Math.max(0, effectiveWorking - p.getPresentDays() - p.getLateDays()));

			// Attendance score: present+late as % of effective working days, late counts half
			double attRaw = ((p.getPresentDays() + p.getLateDays() * 0.5) / effectiveWorking) * 100.0;
			p.setAttendanceScore((int) Math.max(0, Math.min(100, attRaw)));

			// Overall: 60% task + 40% attendance
			p.setOverallScore((int) (p.getTaskScore() * 0.6 + p.getAttendanceScore() * 0.4));

			// Grade
			p.setGrade(p.getOverallScore() >= 90 ? "A+" :
			          p.getOverallScore() >= 75 ? "A"  :
			          p.getOverallScore() >= 60 ? "B"  :
			          p.getOverallScore() >= 45 ? "C"  : "D");

			// Existing review this month
			p.setExistingReview(performanceReviewRepository
					.findByEmployeeAndReviewMonthAndTenantSegment(emp, ym.toString(), tenant)
					.orElse(null));
			p.setWeeklyLocked(isWeeklyLocked(emp));

			perfList.add(p);
		}
		return perfList;
	}

	private void populatePunchModel(Optional<Attendance> todayOpt, boolean todayOnLeave, Model model) {
		var punchedIn  = todayOpt.isPresent();
		var punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);
		var onBreak    = todayOpt.map(a ->
				(a.getBreakStart() != null && a.getBreakEnd() == null) ||
				(a.getBreak2Start() != null && a.getBreak2End() == null)).orElse(false);
		var breakDone  = todayOpt.map(a -> a.getBreak2End() != null).orElse(false);
		var canStartBreak = punchedIn && !punchedOut && !onBreak && !breakDone &&
				todayOpt.map(a -> a.getBreakStart() == null ||
						(a.getBreakEnd() != null && a.getBreak2Start() == null)).orElse(false);

		model.addAttribute("punchedIn",         punchedIn);
		model.addAttribute("punchedOut",        punchedOut);
		model.addAttribute("onBreak",           onBreak);
		model.addAttribute("breakDone",         breakDone);
		model.addAttribute("canStartBreak",     canStartBreak);
		model.addAttribute("todayOnLeave",      todayOnLeave);
	}

	private List<Team> getManagedTeams(User manager) {
		if (manager == null) {
			return Collections.emptyList();
		}
		return teamRepository.findByManagerWithMembers(manager);
	}

	private Team getPrimaryTeam(User manager) {
		List<Team> teams = getManagedTeams(manager);
		return teams.isEmpty() ? null : teams.get(0);
	}

	private List<User> getManagedTeamMembers(User manager) {
		if (manager == null) {
			return Collections.emptyList();
		}
		return getManagedTeams(manager).stream()
				.filter(team -> team != null && team.getMembers() != null)
				.flatMap(team -> team.getMembers().stream())
				.filter(member -> member != null && member.getId() != null)
				.collect(Collectors.toMap(User::getId, member -> member, (m1, m2) -> m1, LinkedHashMap::new))
				.values().stream()
				.collect(Collectors.toList());
	}

	private String getManagedTeamName(User manager) {
		List<Team> teams = getManagedTeams(manager);
		if (teams.isEmpty()) {
			return "No Team Assigned";
		}
		if (teams.size() == 1) {
			return teams.get(0).getName();
		}
		return String.join(", ", teams.stream().map(Team::getName).collect(Collectors.toList()));
	}

	// =========================
	// DASHBOARD PAGE
	// =========================
	@GetMapping("/dashboard")
	public String dashboard(Model model) {

		injectStats(model);

		// ── Additional analytics data for charts ──────────────────────────
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);

		if (manager != null) {
			String tenant = getTenantSegmentFromEmail(manager.getEmail());
			List<User> teamMembers = getManagedTeamMembers(manager);

			// Scope tasks to this manager's created tasks within their tenant
			List<Task> myTasks = taskRepository.findByCreatedByAndTenantSegment(
					currentUsername, tenant);

			// Task status breakdown
			long statusDone       = myTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			long statusInProgress = myTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
			long statusPending    = myTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
			long statusReview     = myTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();

			model.addAttribute("chartStatusDone",       statusDone);
			model.addAttribute("chartStatusInProgress", statusInProgress);
			model.addAttribute("chartStatusPending",    statusPending);
			model.addAttribute("chartStatusReview",     statusReview);

			// Task priority breakdown
			long priorityHigh   = myTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
			long priorityMedium = myTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
			long priorityLow    = myTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

			model.addAttribute("chartPriorityHigh",   priorityHigh);
			model.addAttribute("chartPriorityMedium", priorityMedium);
			model.addAttribute("chartPriorityLow",    priorityLow);

			// Per-member task count (bar chart)
			List<String> memberLabels = new ArrayList<>();
			List<Long>   memberTaskCounts = new ArrayList<>();
			for (User member : teamMembers) {
				long count = myTasks.stream()
						.filter(t -> member.getUsername().equalsIgnoreCase(t.getAssignedTo()))
						.count();
				memberLabels.add(member.getUsername());
				memberTaskCounts.add(count);
			}
			model.addAttribute("chartMemberLabels",     memberLabels);
			model.addAttribute("chartMemberTaskCounts", memberTaskCounts);

			// Team active vs inactive
			long activeCount   = teamMembers.stream().filter(User::isActive).count();
			long inactiveCount = teamMembers.size() - activeCount;
			model.addAttribute("chartActiveTeam",   activeCount);
			model.addAttribute("chartInactiveTeam", inactiveCount);

			// Verification status breakdown
			long verified = myTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
			long rejected = myTasks.stream().filter(t -> "rejected".equalsIgnoreCase(t.getVerificationStatus())).count();
			long waiting  = myTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
			long unverified = myTasks.size() - verified - rejected - waiting;

			model.addAttribute("chartVerified",   verified);
			model.addAttribute("chartRejected",   rejected);
			model.addAttribute("chartWaiting",    waiting);
			model.addAttribute("chartUnverified", unverified);

			model.addAttribute("chartTotalMyTasks", myTasks.size());
		} else {
			// zero fallbacks
			model.addAttribute("chartStatusDone", 0); model.addAttribute("chartStatusInProgress", 0);
			model.addAttribute("chartStatusPending", 0); model.addAttribute("chartStatusReview", 0);
			model.addAttribute("chartPriorityHigh", 0); model.addAttribute("chartPriorityMedium", 0);
			model.addAttribute("chartPriorityLow", 0);
			model.addAttribute("chartMemberLabels", new ArrayList<>());
			model.addAttribute("chartMemberTaskCounts", new ArrayList<>());
			model.addAttribute("chartActiveTeam", 0); model.addAttribute("chartInactiveTeam", 0);
			model.addAttribute("chartVerified", 0); model.addAttribute("chartRejected", 0);
			model.addAttribute("chartWaiting", 0); model.addAttribute("chartUnverified", 0);
			model.addAttribute("chartTotalMyTasks", 0);
		}

		return "manager-dashboard";
	}

	/** Extract tenant segment from email: "mgr.tcs@crm.com" → "tcs" */
	@GetMapping("/dashboard/analytics")
	@ResponseBody
	public Map<String, Object> dashboardAnalytics() {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);
		if (manager == null) {
			return buildDashboardAnalytics(Collections.emptyList(), Collections.emptyList());
		}

		String tenant = getTenantSegmentFromEmail(manager.getEmail());
		List<User> teamMembers = getManagedTeamMembers(manager);
		List<Task> myTasks = taskRepository.findByCreatedByAndTenantSegment(currentUsername, tenant);
		return buildDashboardAnalytics(myTasks, teamMembers);
	}

	private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> people) {
		Map<String, Object> data = new LinkedHashMap<>();
		List<Task> scopedTasks = tasks != null ? tasks : Collections.emptyList();
		List<User> scopedPeople = people != null ? people : Collections.emptyList();

		long statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
		long statusInProgress = scopedTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
		long statusPending = scopedTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
		long statusReview = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();
		long priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
		long priorityMedium = scopedTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
		long priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

		List<String> memberLabels = new ArrayList<>();
		List<Long> memberTaskCounts = new ArrayList<>();
		for (User member : scopedPeople) {
			long count = scopedTasks.stream()
					.filter(t -> member.getUsername() != null && member.getUsername().equalsIgnoreCase(t.getAssignedTo()))
					.count();
			memberLabels.add(member.getUsername());
			memberTaskCounts.add(count);
		}

		long activeCount = scopedPeople.stream().filter(User::isActive).count();
		long inactiveCount = scopedPeople.size() - activeCount;
		long verified = scopedTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
		long rejected = scopedTasks.stream().filter(t -> "rejected".equalsIgnoreCase(t.getVerificationStatus())).count();
		long waiting = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
		long unverified = scopedTasks.size() - verified - rejected - waiting;

		data.put("statusDone", statusDone);
		data.put("statusInProgress", statusInProgress);
		data.put("statusPending", statusPending);
		data.put("statusReview", statusReview);
		data.put("priorityHigh", priorityHigh);
		data.put("priorityMedium", priorityMedium);
		data.put("priorityLow", priorityLow);
		data.put("memberLabels", memberLabels);
		data.put("memberTaskCounts", memberTaskCounts);
		data.put("activeTeam", activeCount);
		data.put("inactiveTeam", inactiveCount);
		data.put("verified", verified);
		data.put("rejected", rejected);
		data.put("waiting", waiting);
		data.put("unverified", Math.max(unverified, 0));
		data.put("totalMyTasks", scopedTasks.size());
		return data;
	}

	private String getTenantSegmentFromEmail(String email) {
		if (email == null || !email.contains("@")) return "";
		String local = email.substring(0, email.indexOf('@'));
		int dot = local.lastIndexOf('.');
		return dot >= 0 ? local.substring(dot + 1) : local;
	}

	// =========================
	// TEAM PAGE
	// =========================
	@GetMapping("/team")
	public String teamPage(Model model) {
		injectStats(model);
		return "manager-team";
	}

	/**
	 * REST: GET /manager/api/member/{id}
	 * Returns team member profile + last 30 days attendance as JSON for the modal.
	 */
	@GetMapping("/api/member/{id}")
	@ResponseBody
	public Map<String, Object> memberDetail(@PathVariable Long id) {
		attendanceService.processAutoPunchOuts();
		Map<String, Object> resp = new LinkedHashMap<>();

		User manager = getCurrentManager();
		if (manager == null) { resp.put("error", "Not authenticated."); return resp; }

		// Verify the requested user is actually in this manager's team(s)
		List<Team> myTeams = getManagedTeams(manager);
		boolean inTeam = myTeams.stream().flatMap(t -> t.getMembers().stream()).anyMatch(m -> m.getId().equals(id));
		if (!inTeam) { resp.put("error", "Member not found in your team."); return resp; }

		User user = userRepository.findById(id).orElse(null);
		if (user == null) { resp.put("error", "User not found."); return resp; }

		// Profile
		resp.put("id",       user.getId());
		resp.put("username", user.getUsername());
		resp.put("email",    user.getEmail());
		resp.put("role",     user.getRole());
		resp.put("status",   user.getStatus());

		// Last 30 days attendance
		LocalDate today  = LocalDate.now();
		LocalDate from   = today.minusDays(29);
		String    tenant = getTenantSegment(manager);
		Map<LocalDate, String> holidays = fetchHolidays(tenant, from, today);
		List<Attendance> records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(user, from, today);
		List<AttendanceDay> days = buildDayList(records, from, today, holidays, user);

		long present = days.stream().filter(d -> "present".equals(d.getStatus()) || "late".equals(d.getStatus())).count();
		long absent  = days.stream().filter(d -> "absent".equals(d.getStatus())).count();
		long halfDay = days.stream().filter(d -> "half-day".equals(d.getStatus())).count();
		long holiday = days.stream().filter(d -> "holiday".equals(d.getStatus())).count();
		resp.put("presentDays", present);
		resp.put("absentDays",  absent);
		resp.put("halfDays",    halfDay);
		resp.put("holidays",    holiday);

		List<Map<String, String>> rows = new ArrayList<>();
		for (AttendanceDay d : days) {
			Map<String, String> row = new LinkedHashMap<>();
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


	// =========================
	// TASKS PAGE
	// =========================
	@GetMapping("/tasks")
	public String tasksPage(Model model) {

		injectStats(model);

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);
			// Only show tasks belonging to this manager's tenant
			List<Task> tasks = taskRepository.findByTenantSegment(tenant);
			tasks.sort(java.util.Comparator.comparing(Task::getId).reversed());
			long done    = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
			long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
			model.addAttribute("tasks",            tasks);
			model.addAttribute("totalTasks",       tasks.size());
			model.addAttribute("doneTasks",        done);
			model.addAttribute("pendingTaskCount", pending);

			// Pass team members as "employees" for the assign dropdown
			List<User> teamMembers = getManagedTeamMembers(manager);
			model.addAttribute("employees", teamMembers);

			List<Team> teams = getManagedTeams(manager);
			model.addAttribute("teams", teams);
		}

		return "manager-tasks";
	}

	// =========================
	// ASSIGN TASK (POST)
	// =========================
	@PostMapping("/tasks/assign")
	public String assignTask(
			@RequestParam String title,
			@RequestParam(required = false) String description,
			@RequestParam String priority,
			@RequestParam String status,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String dueDate,
			@RequestParam(value = "assignedToIds", required = false) List<Long> assignedToIds,
			@RequestParam(value = "assignToTeam", required = false, defaultValue = "false") boolean assignToTeam,
			@RequestParam(value = "assignedToTeamId", required = false) String assignedToTeamId,
			@RequestParam(value = "attachments", required = false) MultipartFile[] attachments,
			RedirectAttributes ra) {

		var manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return REDIRECT_MANAGER_TASKS;
		}

		var validationError = validateTaskParams(title, description, priority, status, dueDate, startDate);
		if (validationError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
			return REDIRECT_MANAGER_TASKS;
		}

		LocalDate due;
		try {
			due = LocalDate.parse(dueDate.trim());
		} catch (java.time.format.DateTimeParseException e) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid due date value.");
			return REDIRECT_MANAGER_TASKS;
		}
		if (due.isBefore(LocalDate.now())) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Due date cannot be in the past.");
			return REDIRECT_MANAGER_TASKS;
		}

		LocalDate start = null;
		if (startDate != null && !startDate.trim().isEmpty()) {
			if (!startDate.trim().matches(DATE_REGEX)) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid start date format.");
				return REDIRECT_MANAGER_TASKS;
			}
			try {
				start = LocalDate.parse(startDate.trim());
			} catch (java.time.format.DateTimeParseException e) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid start date value.");
				return REDIRECT_MANAGER_TASKS;
			}
			if (start.isAfter(due)) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Start date cannot be after due date.");
				return REDIRECT_MANAGER_TASKS;
			}
		}

		var tenant = getTenantSegment(manager);

		// Verify team members list
		var teamMembers = getManagedTeamMembers(manager);
		var targetUsers = new ArrayList<User>();
		var groupName = "";

		if (assignToTeam) {
			if (assignedToTeamId == null || "all".equalsIgnoreCase(assignedToTeamId)) {
				if (teamMembers == null || teamMembers.isEmpty()) {
					ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You do not have any employees in your team to assign tasks to.");
					return REDIRECT_MANAGER_TASKS;
				}
				targetUsers.addAll(teamMembers);
				groupName = "entire team";
			} else {
				try {
					var teamId = Long.parseLong(assignedToTeamId);
					var managedTeams = getManagedTeams(manager);
					var targetTeam = managedTeams.stream()
							.filter(t -> t.getId().equals(teamId))
							.findFirst()
							.orElse(null);

					if (targetTeam == null) {
						ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Selected team is not managed by you.");
						return REDIRECT_MANAGER_TASKS;
					}

					var members = targetTeam.getMembers();
					if (members == null || members.isEmpty()) {
						ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Selected team '" + targetTeam.getName() + "' does not have any members.");
						return REDIRECT_MANAGER_TASKS;
					}
					targetUsers.addAll(members);
					groupName = "team " + targetTeam.getName();
				} catch (NumberFormatException e) {
					ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid team ID selected.");
					return REDIRECT_MANAGER_TASKS;
				}
			}
		} else {
			if (assignedToIds == null || assignedToIds.isEmpty()) {
				ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Please select at least one employee to assign the task.");
				return REDIRECT_MANAGER_TASKS;
			}
			for (var empId : assignedToIds) {
				var assignedUser = teamMembers.stream()
						.filter(u -> u.getId().equals(empId))
						.findFirst()
						.orElse(null);

				if (assignedUser == null) {
					ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "One or more selected employees are not in your team.");
					return REDIRECT_MANAGER_TASKS;
				}
				targetUsers.add(assignedUser);
			}
		}

		// Save uploaded files to in-memory cache to avoid duplicate streams parsing
		var attachmentInfos = new ArrayList<TaskAttachmentInfo>();
		if (attachments != null) {
			for (var file : attachments) {
				if (file == null || file.isEmpty()) continue;
				try {
					var fileData = file.getBytes();
					var contentType = file.getContentType();
					if (contentType == null) contentType = OCTET_STREAM;
					attachmentInfos.add(new TaskAttachmentInfo(file.getOriginalFilename(), fileData, contentType));
				} catch (IOException e) {
					ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "File upload failed: " + e.getMessage());
					return REDIRECT_MANAGER_TASKS;
				}
			}
		}

		for (var targetUser : targetUsers) {
			var task = new Task();
			task.setTitle(title.trim());
			task.setDescription(description);
			task.setPriority(priority);
			task.setStatus(status);
			task.setStartDate(startDate);
			task.setDueDate(dueDate);
			task.setAssignedTo(targetUser.getUsername());
			task.setAssignedToId(targetUser.getId());
			task.setTenantSegment(tenant);
			task.setCreatedBy(manager.getUsername());
			taskRepository.save(task);

			var uploadedNames = new ArrayList<String>();
			for (var info : attachmentInfos) {
				var taskAttachment = new TaskAttachment(
					task,
					info.filename(),
					info.fileData(),
					info.contentType(),
					manager.getUsername()
				);
				taskAttachmentRepository.save(taskAttachment);
				uploadedNames.add(info.filename());
			}

			if (!uploadedNames.isEmpty()) {
				task.setAttachmentPaths(String.join(",", uploadedNames));
				taskRepository.save(task);
			}

			notificationService.notifyTaskAssigned(targetUser, manager.getUsername(), task.getTitle());
		}

		if (assignToTeam) {
			ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task assigned to the " + groupName + " successfully.");
		} else {
			if (targetUsers.size() == 1) {
				ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task assigned to " + targetUsers.get(0).getUsername() + " successfully.");
			} else {
				ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Task assigned to " + targetUsers.size() + " employees successfully.");
			}
		}
		return REDIRECT_MANAGER_TASKS;
	}

	// =========================
	// LIST TASK ATTACHMENTS (REST — for modal)
	// =========================
	@GetMapping("/api/task/{taskId}/attachments")
	@ResponseBody
	public List<Map<String, Object>> listTaskAttachments(@PathVariable Long taskId) {
		Task task = taskRepository.findById(taskId).orElse(null);
		if (task == null) return Collections.emptyList();

		List<Map<String, Object>> result = new ArrayList<>();
		if (task.getAttachments() != null) {
			for (TaskAttachment att : task.getAttachments()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id",           att.getId());
				item.put("filename",     att.getOriginalFilename());
				item.put("contentType",  att.getContentType() != null ? att.getContentType() : "application/octet-stream");
				item.put("uploadedBy",   att.getUploadedBy() != null ? att.getUploadedBy() : "");
				item.put("viewUrl",      "/manager/tasks/view/"      + att.getId());
				item.put("downloadUrl",  "/manager/tasks/download/"  + att.getId());
				result.add(item);
			}
		}
		return result;
	}

	// =========================
	// DOWNLOAD TASK ATTACHMENT
	// =========================
	@GetMapping("/tasks/download/{attachmentId}")
	public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
		TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
		if (attachment == null) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM)
				.body(attachment.getFileData());
	}

	// =========================
	// VIEW TASK ATTACHMENT INLINE
	// =========================
	@GetMapping("/tasks/view/{attachmentId}")
	public ResponseEntity<byte[]> viewAttachment(@PathVariable Long attachmentId) {
		TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
		if (attachment == null) {
			return ResponseEntity.notFound().build();
		}

		String contentType = attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM;
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attachment.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, contentType)
				.body(attachment.getFileData());
	}

	// =========================
	// VERIFY TASK (POST)
	// action=approve  → manager verifies task as done
	// action=reject   → manager returns task to employee with feedback
	// action=reopen   → manager marks a verified task as incomplete again
	// =========================
	@PostMapping("/tasks/verify/{id}")
	public String verifyTask(@PathVariable Long id,
                            @RequestParam String action,
                            @RequestParam(required = false) String reason,
                            RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
			return "redirect:/manager/tasks";
		}

		String tenant = getTenantSegment(manager);
		Task task = taskRepository.findById(id).orElse(null);
		if (task == null || !tenant.equals(task.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Task not found.");
			return "redirect:/manager/tasks";
		}

		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		if ("approve".equalsIgnoreCase(action)) {
			// Manager confirms the work is complete
			task.setStatus("done");
			task.setVerificationStatus("approved");
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(null);
			ra.addFlashAttribute("successMessage", "Task verified and marked as done.");

		} else if ("reject".equalsIgnoreCase(action)) {
			if (reason == null || reason.trim().isBlank()) {
				ra.addFlashAttribute("errorMessage", "Reason for return is required.");
				return "redirect:/manager/tasks";
			}
			if (reason.trim().length() > 255) {
				ra.addFlashAttribute("errorMessage", "Reason cannot exceed 255 characters.");
				return "redirect:/manager/tasks";
			}
			// Manager returns the task — employee must redo and resubmit
			task.setStatus("in-progress");
			task.setVerificationStatus("rejected");
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(reason.trim());
			ra.addFlashAttribute("successMessage", "Task returned to employee for rework.");

		} else if ("reopen".equalsIgnoreCase(action)) {
			// Manager re-opens a previously verified task
			task.setStatus("in-progress");
			task.setVerificationStatus("pending");
			task.setLastVerifiedBy(manager.getUsername());
			task.setLastVerifiedAt(timestamp);
			task.setVerificationReason(null);
			ra.addFlashAttribute("successMessage", "Task marked as incomplete and returned to employee.");
		}

		taskRepository.save(task);

		User assignee = resolveTaskAssignee(task);
		if (assignee != null) {
			notificationService.notifyTaskVerified(
					assignee, manager.getUsername(), task.getTitle(), action, reason);
		}

		return "redirect:/manager/tasks";
	}

	private User resolveTaskAssignee(Task task) {
		if (task == null) return null;
		if (task.getAssignedToId() != null) {
			User byId = userRepository.findById(task.getAssignedToId()).orElse(null);
			if (byId != null) return byId;
		}
		if (task.getAssignedTo() != null && !task.getAssignedTo().isBlank()) {
			return userRepository.findByUsername(task.getAssignedTo());
		}
		return null;
	}

	// =========================
	// DELETE TASK (POST)
	// =========================
	@PostMapping("/tasks/delete/{id}")
	public String deleteTask(@PathVariable Long id, RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant = manager != null ? getTenantSegment(manager) : "";

		Task task = taskRepository.findById(id).orElse(null);
		if (task == null || !tenant.equals(task.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Task not found.");
		} else {
			taskRepository.deleteById(id);
			ra.addFlashAttribute("successMessage", "Task deleted successfully.");
		}
		return "redirect:/manager/tasks";
	}

	// =========================
	// LEAVE REQUESTS PAGE
	// =========================
	@GetMapping("/leave")
	public String legacyLeaveRedirect() {
		return "redirect:/manager/leaves";
	}

	@GetMapping("/leaves")
	public String leavesPage(Model model) {
		injectStats(model);

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);
			List<LeaveRequest> requests = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(manager);
			long pending = leaveRequestRepository.countByEmployeeAndStatus(manager, "Pending");
			long approved = leaveRequestRepository.countByEmployeeAndStatus(manager, "Approved");
			long rejected = leaveRequestRepository.countByEmployeeAndStatus(manager, "Rejected");

			model.addAttribute("leaveRequests", requests);
			model.addAttribute("pendingLeaves", pending);
			model.addAttribute("approvedLeaves", approved);
			model.addAttribute("rejectedLeaves", rejected);
			model.addAttribute("casualBalance", 12);
			model.addAttribute("sickBalance", 6);
			model.addAttribute("annualBalance", 18);
			model.addAttribute("compBalance", 3);
			model.addAttribute("tenantSegment", tenant);
		} else {
			model.addAttribute("leaveRequests", Collections.emptyList());
			model.addAttribute("pendingLeaves", 0);
			model.addAttribute("approvedLeaves", 0);
			model.addAttribute("rejectedLeaves", 0);
			model.addAttribute("casualBalance", 12);
			model.addAttribute("sickBalance", 6);
			model.addAttribute("annualBalance", 18);
			model.addAttribute("compBalance", 3);
		}

		return "manager-leave";
	}

	@PostMapping("/leaves")
	public String submitLeave(@RequestParam String type,
	                          @RequestParam String fromDate,
	                          @RequestParam String toDate,
	                          @RequestParam String reason,
	                          @RequestParam(value = "attachment", required = false) MultipartFile attachment,
	                          RedirectAttributes ra) {
		var manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_SESSION_EXPIRED);
			return REDIRECT_MANAGER_LEAVES;
		}

		var validationError = validateLeaveParams(type, reason, fromDate, toDate);
		if (validationError != null) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
			return REDIRECT_MANAGER_LEAVES;
		}

		LocalDate from;
		LocalDate to;
		try {
			from = LocalDate.parse(fromDate);
			to = LocalDate.parse(toDate);
		} catch (java.time.format.DateTimeParseException e) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid date value.");
			return REDIRECT_MANAGER_LEAVES;
		}

		if (to.isBefore(from)) {
			ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "To date cannot be before from date.");
			return REDIRECT_MANAGER_LEAVES;
		}

		LeaveRequest leave = new LeaveRequest();
		leave.setEmployee(manager);
		leave.setEmployeeName(manager.getUsername());
		leave.setTenantSegment(getTenantSegment(manager));
		leave.setType(type.trim());
		leave.setFromDate(from);
		leave.setToDate(to);
		leave.setReason(reason.trim());
		leave.setStatus("Pending");

		if (attachment != null && !attachment.isEmpty()) {
			try {
				leave.setAttachmentName(attachment.getOriginalFilename());
				leave.setAttachmentContentType(attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream");
				leave.setAttachmentData(attachment.getBytes());
			} catch (IOException e) {
				ra.addFlashAttribute("errorMessage", "Attachment upload failed: " + e.getMessage());
				return "redirect:/manager/leaves";
			}
		}

		leaveRequestRepository.save(leave);
		notificationService.notifyLeaveSubmitted(leave);
		ra.addFlashAttribute("successMessage", "Leave request submitted successfully.");
		return "redirect:/manager/leaves";
	}

	// =========================
	// REPORTS PAGE
	// =========================
	@GetMapping("/reports")
	public String reportsPage(Model model) {

		injectStats(model);

		User manager = getCurrentManager();
		if (manager != null) {
			String tenant = getTenantSegment(manager);

			// Team members with full performance stats
			List<User> teamMembers = getManagedTeamMembers(manager);
			model.addAttribute("teamMembers", teamMembers);

			// All possible recipients (Team members + HR + Admin)
			List<User> allTenantUsers = userRepository.findByTenantSegment(tenant);
			List<User> allRecipients = allTenantUsers.stream()
					.filter(u -> "HR".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()) || teamMembers.contains(u))
					.distinct()
					.collect(Collectors.toList());
			model.addAttribute("allRecipients", allRecipients);

			// Sent reports history (for "Send Report" tracking)
			List<Report> sentReports = reportRepository
					.findBySentByAndTenantSegmentOrderBySentAtDesc(manager.getUsername(), tenant);
			model.addAttribute("sentReports", sentReports);
			model.addAttribute("sentReportCount", sentReports.size());

			// Build per-employee detail for click-to-view
			var ym = java.time.YearMonth.now();
			var perfList = computeEmployeePerformance(teamMembers, ym, tenant);
			model.addAttribute(ATTR_PERF_LIST,      perfList);
			model.addAttribute(ATTR_SELECTED_MONTH, ym.toString());
		} else {
			model.addAttribute("teamMembers", java.util.Collections.emptyList());
			model.addAttribute("sentReports", java.util.Collections.emptyList());
			model.addAttribute("sentReportCount", 0);
			model.addAttribute("perfList", java.util.Collections.emptyList());
			model.addAttribute("selectedMonth", java.time.YearMonth.now().toString());
		}

		return "manager-reports";
	}

	// =========================
	// SEND REPORT (POST)
	// =========================
	@PostMapping("/reports/send")
	public String sendReport(
			@RequestParam String title,
			@RequestParam(required = false) String message,
			@RequestParam(value = "recipientIds", required = false) List<Long> recipientIds,
			@RequestParam(value = "attachments", required = false) MultipartFile[] attachments,
			@RequestParam(required = false) Integer taskScore,
			@RequestParam(required = false) Integer attendanceScore,
			@RequestParam(required = false) Integer overallScore,
			@RequestParam(required = false) String grade,
			@RequestParam(required = false) Integer totalTasks,
			@RequestParam(required = false) Integer doneTasks,
			@RequestParam(required = false) Integer pendingTasks,
			@RequestParam(required = false) Integer overdueTasks,
			@RequestParam(required = false) Integer presentDays,
			@RequestParam(required = false) Integer absentDays,
			@RequestParam(required = false) Integer lateDays,
			@RequestParam(required = false) Integer leaveDays,
			RedirectAttributes ra) {

		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired. Please log in again.");
			return "redirect:/manager/reports";
		}
		if (title == null || title.isBlank()) {
			ra.addFlashAttribute("errorMessage", "Report title is required.");
			return "redirect:/manager/reports";
		}
		if (title.trim().length() > 200) {
			ra.addFlashAttribute("errorMessage", "Report title cannot exceed 200 characters.");
			return "redirect:/manager/reports";
		}
		if (message != null && message.length() > 255) {
			ra.addFlashAttribute("errorMessage", "Message cannot exceed 255 characters.");
			return "redirect:/manager/reports";
		}
		if (recipientIds == null || recipientIds.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "Please select at least one recipient.");
			return "redirect:/manager/reports";
		}

		String tenant = getTenantSegment(manager);

		// Verify recipients are in this manager's tenant and have valid roles (Employee in team, HR, or Admin)
		List<User> allTenantUsers = userRepository.findByTenantSegment(tenant);
		List<User> teamMembers = getManagedTeamMembers(manager);
		List<User> validRecipients = allTenantUsers.stream()
				.filter(u -> recipientIds.contains(u.getId()))
				.filter(u -> "HR".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()) || teamMembers.contains(u))
				.collect(Collectors.toList());

		if (validRecipients.size() != recipientIds.size()) {
			ra.addFlashAttribute("errorMessage", "One or more selected recipients are invalid or outside your tenant.");
			return "redirect:/manager/reports";
		}

		// Build CSV strings for IDs and names
		String idsCsv   = validRecipients.stream().map(u -> String.valueOf(u.getId())).collect(java.util.stream.Collectors.joining(","));
		String namesCsv = validRecipients.stream().map(User::getUsername).collect(java.util.stream.Collectors.joining(", "));

		Report report = new Report();
		report.setTitle(title.trim());
		report.setMessage(message != null ? message.trim() : "");
		report.setSentBy(manager.getUsername());
		report.setTenantSegment(tenant);
		report.setRecipientIds(idsCsv);
		report.setRecipientNames(namesCsv);

		// Performance Snapshots
		report.setTaskScore(taskScore);
		report.setAttendanceScore(attendanceScore);
		report.setOverallScore(overallScore);
		report.setGrade(grade);
		report.setTotalTasks(totalTasks);
		report.setDoneTasks(doneTasks);
		report.setPendingTasks(pendingTasks);
		report.setOverdueTasks(overdueTasks);
		report.setPresentDays(presentDays);
		report.setAbsentDays(absentDays);
		report.setLateDays(lateDays);
		report.setLeaveDays(leaveDays);

		reportRepository.save(report);

		// Save attachments
		if (attachments != null) {
			for (MultipartFile file : attachments) {
				if (file == null || file.isEmpty()) continue;
				try {
					String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
					ReportAttachment ra2 = new ReportAttachment(report, file.getOriginalFilename(), file.getBytes(), ct);
					reportAttachmentRepository.save(ra2);
				} catch (IOException e) {
					ra.addFlashAttribute("errorMessage", "File upload failed: " + e.getMessage());
					return "redirect:/manager/reports";
				}
			}
		}

		for (User recipient : validRecipients) {
			notificationService.notifyReportReceived(recipient, manager.getUsername(), title.trim());
		}

		ra.addFlashAttribute("successMessage", "Report sent to " + validRecipients.size() + " recipient(s) successfully.");
		return "redirect:/manager/reports";
	}

	// =========================
	// VIEW REPORT ATTACHMENT INLINE
	// =========================
	@GetMapping("/reports/view/{attachmentId}")
	public ResponseEntity<byte[]> viewReportAttachment(@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	// =========================
	// DOWNLOAD REPORT ATTACHMENT
	// =========================
	@GetMapping("/reports/download/{attachmentId}")
	public ResponseEntity<byte[]> downloadReportAttachment(@PathVariable Long attachmentId) {
		ReportAttachment att = reportAttachmentRepository.findById(attachmentId).orElse(null);
		if (att == null) return ResponseEntity.notFound().build();
		String ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + att.getOriginalFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, ct)
				.body(att.getFileData());
	}

	// =========================
	// CALENDAR PAGE (read-only)
	// =========================
	@GetMapping("/calendar")
	public String calendarPage(Model model) {
		injectStats(model);
		return "manager-calendar";
	}

	// =========================
	// MEETINGS PAGE
	// =========================

	/**
	 * Filter a list of today's meetings to only those that have not yet ended.
	 * A meeting ends at meetingTime + duration minutes. Meetings with no time are always shown.
	 */
	private List<Meeting> getPastMeetings(String tenant, String username) {
		List<Meeting> all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
		LocalDate today = LocalDate.now();
		LocalTime now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (m.getMeetingDate().isBefore(today)) return true;
			if (!m.getMeetingDate().equals(today)) return false;
			if (m.getMeetingTime() == null) return false;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).collect(Collectors.toList());
	}

	/** Returns upcoming meetings (today + future) where the user is a participant OR the host,
	 *  excluding today's meetings that have already ended. */
	private List<Meeting> getUpcomingMeetings(String tenant, String username) {
		List<Meeting> all = meetingRepository
				.findUpcomingMeetingsForUserOrHost(tenant, username, LocalDate.now());
		LocalDate today = LocalDate.now();
		LocalTime now   = LocalTime.now();
		return all.stream().filter(m -> {
			if (!m.getMeetingDate().equals(today)) return true;
			if (m.getMeetingTime() == null) return true;
			int dur = (m.getDuration() != null) ? m.getDuration() : 0;
			return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
		}).collect(Collectors.toList());
	}

	/** GET /manager/meetings — show schedule form + list of meetings where manager is a participant */
	@GetMapping("/meetings")
	public String meetingsPage(Model model) {
		injectStats(model);
		User manager = getCurrentManager();
		if (manager != null) {
			String tenant   = getTenantSegment(manager);
			String username = manager.getUsername();
			model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
			model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
			// Team members available as participants
			model.addAttribute("teamMembers", getManagedTeamMembers(manager));
		} else {
			model.addAttribute("meetings", Collections.emptyList());
			model.addAttribute("teamMembers", Collections.emptyList());
		}
		if (!model.containsAttribute("meetingForm")) {
			model.addAttribute("meetingForm", new Meeting());
		}
		return "manager-meetings";
	}

	/** POST /manager/meetings — create a new meeting */
	@PostMapping("/meetings")
	public String scheduleMeeting(@Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                              BindingResult result,
	                              Model model,
	                              RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant   = manager != null ? getTenantSegment(manager) : "";
		String username = manager != null ? manager.getUsername() : "";

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

		if (result.hasErrors()) {
			injectStats(model);
			if (manager != null) {
				model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
				model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
				model.addAttribute("teamMembers", getManagedTeamMembers(manager));
			} else {
				model.addAttribute("meetings", Collections.emptyList());
				model.addAttribute("teamMembers", Collections.emptyList());
			}
			model.addAttribute("errorMessage", "Please fix the errors below.");
			return "manager-meetings";
		}

		meetingForm.setTenantSegment(tenant);
		meetingForm.setScheduledBy(username);
		meetingRepository.save(meetingForm);
		notificationService.notifyMeetingParticipants(meetingForm);
		ra.addFlashAttribute("successMessage", "Meeting scheduled successfully.");
		return "redirect:/manager/meetings";
	}

	/** GET /manager/meetings/edit/{id} — load meeting into form */
	@GetMapping("/meetings/edit/{id}")
	public String editMeetingPage(@PathVariable Long id, Model model, RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant   = manager != null ? getTenantSegment(manager) : "";
		String username = manager != null ? manager.getUsername() : "";

		Meeting meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
			return "redirect:/manager/meetings";
		}

		injectStats(model);
		model.addAttribute("meetingForm", meeting);
		model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
		model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
		model.addAttribute("teamMembers", getManagedTeamMembers(manager));
		return "manager-meetings";
	}

	/** POST /manager/meetings/edit/{id} — update existing meeting */
	@PostMapping("/meetings/edit/{id}")
	public String updateMeeting(@PathVariable Long id,
	                            @Valid @ModelAttribute("meetingForm") Meeting meetingForm,
	                            BindingResult result,
	                            Model model,
	                            RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant   = manager != null ? getTenantSegment(manager) : "";
		String username = manager != null ? manager.getUsername() : "";

		if ("in-person".equalsIgnoreCase(meetingForm.getMeetingType()) && (meetingForm.getLocation() == null || meetingForm.getLocation().isBlank())) {
			result.rejectValue("location", "NotBlank", "Location is required for in-person meetings.");
		}

		if (result.hasErrors()) {
			injectStats(model);
			model.addAttribute("meetings", getUpcomingMeetings(tenant, username));
			model.addAttribute("pastMeetings", getPastMeetings(tenant, username));
			model.addAttribute("teamMembers", getManagedTeamMembers(manager));
			model.addAttribute("errorMessage", "Please fix the errors below.");
			return "manager-meetings";
		}

		Meeting existing = meetingRepository.findById(id).orElse(null);
		if (existing == null || !tenant.equals(existing.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
			return "redirect:/manager/meetings";
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

		ra.addFlashAttribute("successMessage", "Meeting updated successfully.");
		return "redirect:/manager/meetings";
	}

	/** POST /manager/meetings/delete/{id} — delete a meeting */
	@PostMapping("/meetings/delete/{id}")
	public String deleteMeeting(@PathVariable Long id, RedirectAttributes ra) {
		User manager = getCurrentManager();
		String tenant = manager != null ? getTenantSegment(manager) : "";

		Meeting meeting = meetingRepository.findById(id).orElse(null);
		if (meeting == null || !tenant.equals(meeting.getTenantSegment())) {
			ra.addFlashAttribute("errorMessage", "Meeting not found.");
		} else {
			meetingRepository.delete(meeting);
			ra.addFlashAttribute("successMessage", "Meeting deleted successfully.");
		}
		return "redirect:/manager/meetings";
	}

	// =========================
	// PROFILE PAGE
	// =========================
	@GetMapping("/profile")
	public String profilePage(Model model) {

		injectStats(model);

		return "manager-profile";
	}

	// =========================
	// UPDATE PROFILE
	// =========================
	@PostMapping("/update-profile")
	public String updateProfile(@RequestParam(required = false) String username,
								@RequestParam(required = false) String email,
								@RequestParam(required = false) String password,
								@RequestParam(required = false) String confirmPassword,
								HttpServletResponse response,
								RedirectAttributes ra) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		User manager = userRepository.findByUsername(currentUsername);

		if (manager == null) {
			return "redirect:/manager/profile";
		}

		profileUpdateService.updateProfile(manager, username, email, password, confirmPassword, ra, response);
		return "redirect:/manager/profile";
	}

	// ═══════════════════════════════════════════════════════════════════════
	//  ATTENDANCE PAGE
	// ═══════════════════════════════════════════════════════════════════════

	/** Helper: resolve current manager + tenant segment */
	private User getCurrentManager() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		return userRepository.findByUsername(username);
	}

	private String getTenantSegment(User manager) {
		if (manager == null || manager.getEmail() == null) return "";
		String email = manager.getEmail();
		try {
			return email.split("\\.")[1].split("@")[0];
		} catch (Exception e) {
			return "";
		}
	}

	private boolean hasApprovedLeave(User user, LocalDate date) {
		if (user == null || date == null) return false;
		return leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user).stream()
				.anyMatch(leave -> "Approved".equalsIgnoreCase(leave.getStatus())
						&& !date.isBefore(leave.getFromDate())
						&& !date.isAfter(leave.getToDate()));
	}

	/**
	 * Build a merged day list for the given date range.
	 * Priority: holiday > weekend > real record > absent.
	 * Result is sorted newest-first.
	 */
	private List<AttendanceDay> buildDayList(List<Attendance> records,
	                                          LocalDate from, LocalDate to,
	                                          Map<LocalDate, String> holidays,
	                                          User user) {
		Map<LocalDate, Attendance> byDate = new LinkedHashMap<>();
		for (Attendance a : records) byDate.put(a.getDate(), a);

		Set<LocalDate> approvedLeaveDates = new LinkedHashSet<>();
		if (user != null) {
			for (LeaveRequest leave : leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)) {
				if (!"Approved".equalsIgnoreCase(leave.getStatus()) || leave.getFromDate() == null || leave.getToDate() == null) {
					continue;
				}
				LocalDate cursor = leave.getFromDate();
				while (!cursor.isAfter(leave.getToDate())) {
					if (!cursor.isBefore(from) && !cursor.isAfter(to)) {
						approvedLeaveDates.add(cursor);
					}
					cursor = cursor.plusDays(1);
				}
			}
		}

		List<AttendanceDay> days = new ArrayList<>();
		LocalDate today  = LocalDate.now();
		LocalDate cursor = to;
		while (!cursor.isBefore(from)) {
			if (holidays.containsKey(cursor)) {
				days.add(new AttendanceDay(cursor, holidays.get(cursor), true));
			} else {
				DayOfWeek dow = cursor.getDayOfWeek();
				if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
					days.add(new AttendanceDay(cursor, "weekend"));
				} else if (byDate.containsKey(cursor)) {
					days.add(new AttendanceDay(byDate.get(cursor)));
				} else if (approvedLeaveDates.contains(cursor)) {
					days.add(new AttendanceDay(cursor, "leave"));
				} else if (!cursor.isAfter(today)) {
					days.add(new AttendanceDay(cursor, "absent"));
				}
			}
			cursor = cursor.minusDays(1);
		}
		return days;
	}

	/** Build holiday map (date → name) for a tenant within a date range. */
	private Map<LocalDate, String> fetchHolidays(String tenant, LocalDate from, LocalDate to) {
		Map<LocalDate, String> map = new LinkedHashMap<>();
		if (tenant == null || tenant.isBlank()) return map;
		List<Holiday> list = holidayRepository.findByTenantAndDateRange(
				tenant, from.toString(), to.toString());
		for (Holiday h : list) map.put(LocalDate.parse(h.getDate()), h.getName());
		return map;
	}

	@GetMapping("/attendance")
	public String attendancePage(
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(required = false) String status,
			Model model) {
		attendanceService.processAutoPunchOuts();
		injectStats(model);

		var manager = getCurrentManager();
		if (manager == null) {
			return "redirect:/manager/dashboard";
		}

		var today = LocalDate.now();

		// Date range (default: last 30 days)
		var filterFrom = (from != null && !from.isBlank()) ? LocalDate.parse(from) : today.minusDays(29);
		var filterTo   = (to   != null && !to.isBlank())   ? LocalDate.parse(to)   : today;
		if (filterTo.isAfter(today))      filterTo   = today;
		if (filterFrom.isAfter(filterTo)) filterFrom = filterTo;

		// Fetch real records in range
		var tenant = getTenantSegment(manager);
		var records = attendanceRepository
				.findByUserAndDateBetweenOrderByDateDesc(manager, filterFrom, filterTo);

		// Holidays in range
		var holidays = fetchHolidays(tenant, filterFrom, filterTo);

		// Today's record — drives punch-in / punch-out button state
		var todayOpt = attendanceRepository.findByUserAndDate(manager, today);

		// Is today a holiday?
		var todayHolidayName = holidays.get(today);
		var isHolidayToday  = todayHolidayName != null;

		var todayOnLeave = hasApprovedLeave(manager, today);

		// Build merged day list (fills leave/absent/weekend/holiday gaps)
		var allDays = buildDayList(records, filterFrom, filterTo, holidays, manager);

		// Apply status filter
		var filteredDays = allDays;
		if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
			filteredDays = allDays.stream()
					.filter(d -> d.getStatus().equalsIgnoreCase(status))
					.collect(Collectors.toList());
		}

		// Stats from all-time records
		var allRecords = attendanceRepository.findByUserOrderByDateDesc(manager);
		var presentCount = allRecords.stream()
				.filter(a -> STATUS_PRESENT.equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
				.count();
		var lateCount = allRecords.stream()
				.filter(a -> "late".equalsIgnoreCase(a.getStatus()))
				.count();

		model.addAttribute("attendanceDays",    filteredDays);
		model.addAttribute("totalRecords",      filteredDays.size());
		model.addAttribute("todayRecord",       todayOpt.orElse(null));
		populatePunchModel(todayOpt, todayOnLeave, model);
		model.addAttribute("isHolidayToday",    isHolidayToday);
		model.addAttribute("todayHolidayName",  todayHolidayName);
		model.addAttribute("presentCount",      presentCount);
		model.addAttribute("lateCount",         lateCount);
		model.addAttribute("filterFrom",        filterFrom.toString());
		model.addAttribute("filterTo",          filterTo.toString());
		model.addAttribute("filterStatus",      status != null ? status : "all");

		return "manager-attendance";
	}

	/** Punch In */
	@PostMapping("/attendance/punch-in")
	public String punchIn(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		String    tenant = getTenantSegment(manager);
		LocalDate today  = LocalDate.now();

		// Block punch-in on holidays
		if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
			ra.addFlashAttribute("errorMessage", "Today is a holiday. Punch-in is not allowed.");
			return "redirect:/manager/attendance";
		}

		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Punch-in is not allowed.");
			return "redirect:/manager/attendance";
		}

		// Prevent duplicate punch-in
		if (attendanceRepository.findByUserAndDate(manager, today).isPresent()) {
			ra.addFlashAttribute("errorMessage", "You have already punched in today.");
			return "redirect:/manager/attendance";
		}

		LocalTime now    = LocalTime.now();
		String    status = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";

		Attendance att = new Attendance();
		att.setUser(manager);
		att.setDate(today);
		att.setCheckIn(now);
		att.setStatus(status);
		att.setTenantSegment(tenant);
		attendanceRepository.save(att);
		notificationService.notifyAttendanceUpdated(manager, "punch-in");

		ra.addFlashAttribute("successMessage",
				"Punched in at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
		return "redirect:/manager/attendance";
	}

	/** Punch Out */
	@PostMapping("/attendance/punch-out")
	public String punchOut(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		LocalDate today = LocalDate.now();
		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Punch-out is not allowed.");
			return "redirect:/manager/attendance";
		}

		Optional<Attendance> opt = attendanceRepository.findByUserAndDate(manager, today);

		if (opt.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
			return "redirect:/manager/attendance";
		}

		Attendance att = opt.get();
		if (att.getCheckOut() != null) {
			ra.addFlashAttribute("errorMessage", "You have already punched out today.");
			return "redirect:/manager/attendance";
		}

		LocalTime now = LocalTime.now();
		att.setCheckOut(now);
		
		// Recalculate status based on worked hours:
		long mins = att.getWorkedMinutes();
		if (mins >= 0 && mins < 240) {
			att.setStatus("absent");
		} else if (mins >= 240 && mins < 360) {
			att.setStatus("half-day");
		}
		
		attendanceRepository.save(att);
		notificationService.notifyAttendanceUpdated(manager, "punch-out");

		ra.addFlashAttribute("successMessage",
				"Punched out at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
		return "redirect:/manager/attendance";
	}

	/** Break Start */
	@PostMapping("/attendance/break-start")
	public String breakStart(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		LocalDate today = LocalDate.now();
		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Break actions are not allowed.");
			return "redirect:/manager/attendance";
		}

		Optional<Attendance> opt = attendanceRepository.findByUserAndDate(manager, today);

		if (opt.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
			return "redirect:/manager/attendance";
		}
		Attendance att = opt.get();
		if (att.getCheckOut() != null) {
			ra.addFlashAttribute("errorMessage", "You have already punched out.");
			return "redirect:/manager/attendance";
		}

		LocalTime now = LocalTime.now();

		if (att.getBreakStart() == null) {
			att.setBreakStart(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-1-start");
			ra.addFlashAttribute("successMessage",
					"Break 1 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
			att.setBreak2Start(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-2-start");
			ra.addFlashAttribute("successMessage",
					"Break 2 started at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		ra.addFlashAttribute("errorMessage", "No more breaks available today.");
		return "redirect:/manager/attendance";
	}

	/** Break End */
	@PostMapping("/attendance/break-end")
	public String breakEnd(RedirectAttributes ra) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/manager/attendance";

		LocalDate today = LocalDate.now();
		if (hasApprovedLeave(manager, today)) {
			ra.addFlashAttribute("errorMessage", "You are on approved leave today. Break actions are not allowed.");
			return "redirect:/manager/attendance";
		}

		Optional<Attendance> opt = attendanceRepository.findByUserAndDate(manager, today);

		if (opt.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "You haven't punched in today.");
			return "redirect:/manager/attendance";
		}
		Attendance att = opt.get();

		LocalTime now = LocalTime.now();

		if (att.getBreak2Start() != null && att.getBreak2End() == null) {
			att.setBreak2End(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-2-end");
			ra.addFlashAttribute("successMessage",
					"Break 2 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		if (att.getBreakStart() != null && att.getBreakEnd() == null) {
			att.setBreakEnd(now);
			attendanceRepository.save(att);
			notificationService.notifyAttendanceUpdated(manager, "break-1-end");
			ra.addFlashAttribute("successMessage",
					"Break 1 ended at " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + ".");
			return "redirect:/manager/attendance";
		}

		ra.addFlashAttribute("errorMessage", "No active break to end.");
		return "redirect:/manager/attendance";
	}

	// =========================
	// PERFORMANCE PAGE
	// =========================

	/** Inner DTO holding computed stats for one employee. */
	public static class EmployeePerf {
		private User   employee;
		private int    totalTasks;
		private int    doneTasks;
		private int    pendingTasks;
		private int    overdueTasks;
		private int    presentDays;
		private int    absentDays;
		private int    lateDays;
		private int    leaveDays;
		private int    attendanceScore; // 0-100
		private int    taskScore;       // 0-100
		private int    overallScore;    // 0-100
		private String grade;           // A+/A/B/C/D
		private PerformanceReview existingReview; // null if not yet reviewed this month
		private boolean weeklyLocked;

		public User getEmployee() { return employee; }
		public void setEmployee(User employee) { this.employee = employee; }

		public int getTotalTasks() { return totalTasks; }
		public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }

		public int getDoneTasks() { return doneTasks; }
		public void setDoneTasks(int doneTasks) { this.doneTasks = doneTasks; }

		public int getPendingTasks() { return pendingTasks; }
		public void setPendingTasks(int pendingTasks) { this.pendingTasks = pendingTasks; }

		public int getOverdueTasks() { return overdueTasks; }
		public void setOverdueTasks(int overdueTasks) { this.overdueTasks = overdueTasks; }

		public int getPresentDays() { return presentDays; }
		public void setPresentDays(int presentDays) { this.presentDays = presentDays; }

		public int getAbsentDays() { return absentDays; }
		public void setAbsentDays(int absentDays) { this.absentDays = absentDays; }

		public int getLateDays() { return lateDays; }
		public void setLateDays(int lateDays) { this.lateDays = lateDays; }

		public int getLeaveDays() { return leaveDays; }
		public void setLeaveDays(int leaveDays) { this.leaveDays = leaveDays; }

		public int getAttendanceScore() { return attendanceScore; }
		public void setAttendanceScore(int attendanceScore) { this.attendanceScore = attendanceScore; }

		public int getTaskScore() { return taskScore; }
		public void setTaskScore(int taskScore) { this.taskScore = taskScore; }

		public int getOverallScore() { return overallScore; }
		public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

		public String getGrade() { return grade; }
		public void setGrade(String grade) { this.grade = grade; }

		public PerformanceReview getExistingReview() { return existingReview; }
		public void setExistingReview(PerformanceReview existingReview) { this.existingReview = existingReview; }

		public boolean isWeeklyLocked() { return weeklyLocked; }
		public void setWeeklyLocked(boolean weeklyLocked) { this.weeklyLocked = weeklyLocked; }
	}

	private boolean isWeeklyLocked(User employee) {
		return performanceReviewRepository.findByEmployeeOrderByReviewMonthDesc(employee).stream()
				.max(java.util.Comparator.comparing(PerformanceReview::getReviewedAt))
				.map(PerformanceReview::getReviewedAt)
				.map(reviewedAt -> System.currentTimeMillis() - reviewedAt < 7L * 24 * 60 * 60 * 1000)
				.orElse(false);
	}

	@GetMapping("/performance")
	public String performancePage(
			@RequestParam(required = false) String month,
			Model model) {

		attendanceService.processAutoPunchOuts();
		injectStats(model);

		var manager = getCurrentManager();
		if (manager == null) return "manager-performance";

		var tenant = getTenantSegment(manager);

		// Default to current month
		var ym = (month != null && !month.isBlank())
				? java.time.YearMonth.parse(month)
				: java.time.YearMonth.now();

		var from = ym.atDay(1);
		var to   = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();

		// Working days in the selected range (Mon–Fri, excluding today if future)
		var workingDays = 0;
		var d = from;
		while (!d.isAfter(to)) {
			var dow = d.getDayOfWeek();
			if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) workingDays++;
			d = d.plusDays(1);
		}
		if (workingDays < 1) workingDays = 1;

		var teamMembers = getManagedTeamMembers(manager).stream()
				.filter(u -> ROLE_EMPLOYEE.equalsIgnoreCase(u.getRole()))
				.collect(Collectors.toList());
		var perfList = computeEmployeePerformance(teamMembers, ym, tenant);

		// Pre-compute summary stats (lambdas not supported in Thymeleaf SpEL)
		var avgScore = perfList.isEmpty() ? 0
				: (int) Math.round(perfList.stream().mapToInt(EmployeePerf::getOverallScore).average().orElse(0));
		var totalDone = perfList.stream().mapToInt(EmployeePerf::getDoneTasks).sum();
		var reviewedCount = perfList.stream().filter(p -> p.getExistingReview() != null).count();

		model.addAttribute(ATTR_PERF_LIST,      perfList);
		model.addAttribute(ATTR_SELECTED_MONTH, ym.toString());
		model.addAttribute("workingDays",   workingDays);
		model.addAttribute("filterFrom",    from.toString());
		model.addAttribute("filterTo",      to.toString());
		model.addAttribute("avgScore",      avgScore);
		model.addAttribute("totalDone",     totalDone);
		model.addAttribute("reviewedCount", reviewedCount);

		return "manager-performance";
	}

	@PostMapping("/performance/review")
	public String saveReview(
			@RequestParam Long employeeId,
			@RequestParam int rating,
			@RequestParam(required = false) String remarks,
			@RequestParam String reviewMonth,
			RedirectAttributes ra) {

		User manager = getCurrentManager();
		if (manager == null) {
			ra.addFlashAttribute("errorMessage", "Session expired.");
			return "redirect:/manager/performance";
		}

		if (reviewMonth == null || reviewMonth.trim().isBlank() || !reviewMonth.trim().matches("^\\d{4}-\\d{2}$")) {
			ra.addFlashAttribute("errorMessage", "Invalid review month format.");
			return "redirect:/manager/performance";
		}

		if (remarks != null && remarks.length() > 255) {
			ra.addFlashAttribute("errorMessage", "Remarks cannot exceed 255 characters.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		String tenant = getTenantSegment(manager);

		// Verify employee belongs to this manager's team
		List<User> teamMembers = getManagedTeamMembers(manager);
		User emp = teamMembers.stream()
				.filter(u -> u.getId().equals(employeeId))
				.findFirst().orElse(null);

		if (emp == null) {
			ra.addFlashAttribute("errorMessage", "Employee not found in your team.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		if (rating < 1 || rating > 5) {
			ra.addFlashAttribute("errorMessage", "Rating must be between 1 and 5.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		Optional<PerformanceReview> existingOpt = performanceReviewRepository
				.findByEmployeeAndReviewMonthAndTenantSegment(emp, reviewMonth, tenant);
		if (existingOpt.isPresent()) {
			ra.addFlashAttribute("errorMessage", "Performance reviews cannot be updated once submitted.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		if (isWeeklyLocked(emp)) {
			ra.addFlashAttribute("errorMessage", "You can only submit a performance review once a week for this employee.");
			return "redirect:/manager/performance?month=" + reviewMonth;
		}

		PerformanceReview review = new PerformanceReview();
		review.setEmployee(emp);
		review.setReviewedBy(manager.getUsername());
		review.setTenantSegment(tenant);
		review.setReviewMonth(reviewMonth);
		review.setRating(rating);
		review.setRemarks(remarks != null ? remarks.trim() : "");
		review.setReviewedAt(System.currentTimeMillis());
		performanceReviewRepository.save(review);
		notificationService.notifyPerformanceReview(emp, manager.getUsername(), reviewMonth, rating);

		ra.addFlashAttribute("successMessage",
				"Performance review submitted successfully.");
		return "redirect:/manager/performance?month=" + reviewMonth;
	}

	private static record TaskAttachmentInfo(String filename, byte[] fileData, String contentType) {}

	// ── PAYROLL ───────────────────────────────────────────────────────────

	@GetMapping("/payroll")
	public String payrollPage(Model model) {
		User manager = getCurrentManager();
		if (manager == null) return "redirect:/login";

		injectStats(model);
		String tenant = getTenantSegment(manager);
		List<User> teamMembers = getManagedTeamMembers(manager);
		List<PayrollTemplate> payrolls = payrollTemplateRepository.findByEmployeesAndTenant(teamMembers, tenant);
		model.addAttribute("payrolls", payrolls);
		model.addAttribute("teamMembers", teamMembers);

		java.util.Optional<PayrollTemplate> myPayrollOpt = payrollTemplateRepository.findByEmployeeAndTenantSegment(manager, tenant);
		if (myPayrollOpt.isPresent()) {
			PayrollTemplate myPayroll = myPayrollOpt.get();
			model.addAttribute("myPayroll", myPayroll);
			
			// Calculate real-time estimated leave deductions for the current month
			int currentMonth = java.time.LocalDate.now().getMonthValue();
			int currentYear = java.time.LocalDate.now().getYear();
			java.math.BigDecimal leaveDeduction = payslipService.calculateLeaveDeduction(manager, myPayroll.getBasicSalary(), currentMonth, currentYear);
			java.math.BigDecimal estimatedNet = myPayroll.getNetSalary().subtract(leaveDeduction);
			if (estimatedNet.compareTo(java.math.BigDecimal.ZERO) < 0) {
				estimatedNet = java.math.BigDecimal.ZERO;
			}
			model.addAttribute("myPayrollLeaveDeduction", leaveDeduction);
			model.addAttribute("myPayrollEstimatedNet", estimatedNet);
		} else {
			model.addAttribute("myPayroll", null);
		}

		// Personal Generated Payslips
		List<Payslip> myPayslips = payslipRepository.findByEmployeeOrderByIdDesc(manager);
		model.addAttribute("myPayslips", myPayslips);

		// Team Generated Payslips
		List<Payslip> teamPayslips = new java.util.ArrayList<>();
		for (User member : teamMembers) {
			teamPayslips.addAll(payslipRepository.findByEmployeeOrderByIdDesc(member));
		}
		teamPayslips.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));
		model.addAttribute("teamPayslips", teamPayslips);

		model.addAttribute("activePage", "payroll");
		return "manager-payroll";
	}
}
