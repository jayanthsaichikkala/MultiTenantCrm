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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Task;
import com.crm.demo.model.TaskAttachment;
import com.crm.demo.model.User;
import com.crm.demo.repository.AttendanceRepository;

import com.crm.demo.repository.MeetingRepository;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.ReportRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.TaskAttachmentRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.PayrollTemplateRepository;
import com.crm.demo.repository.PayslipRepository;
import com.crm.demo.service.PayslipService;
import com.crm.demo.service.NotificationService;
import com.crm.demo.service.ProfileUpdateService;
import com.crm.demo.service.AttendanceService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/employee")
public class EmployeeController extends BaseController {

    private static final String STATUS_APPROVED = "Approved";
    private static final String STATUS_PENDING_UPPER = "Pending";
    private static final String STATUS_REJECTED_UPPER = "Rejected";
    private static final String ATTR_COMPLETED_TASKS = "completedTasks";
    private static final String ATTR_PENDING_TASKS = "pendingTasks";
    private static final String ATTR_MY_TEAM_NAME = "myTeamName";
    private static final String ATTR_MY_TEAM_MANAGER = "myTeamManager";
    private static final String ATTR_MY_TEAM_MEMBERS = "myTeamMembers";
    private static final String ATTR_MY_TEAM_SIZE = "myTeamSize";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_IN_PROGRESS = "in-progress";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_WAITING_FOR_REVIEW = "waiting-for-review";
    private static final String STATUS_REJECTED = "rejected";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_EMPLOYEE_TASKS = "redirect:/employee/tasks";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String REDIRECT_EMPLOYEE_ATTENDANCE = "redirect:/employee/attendance";
    private static final String TIME_FORMAT = "%02d:%02d";
    private static final String MSG_NOT_PUNCHED_IN = "You haven't punched in today.";
    private static final String REDIRECT_EMPLOYEE_LEAVES = "redirect:/employee/leaves";
    private static final String STATUS_ACTIVE = "active";
    private static final String ATTR_ACTIVE_PAGE = "activePage";
    private static final String PAYROLL = "payroll";
    private static final String STATUS_DONE_KEY = "statusDone";
    private static final String ERROR_AUTH = "?error=auth";
    private static final String SUCCESS = "?success";
    private static final String ERROR_INVALID = "?error=invalid";

    @Value("${app.upload.dir:uploads/tasks}")
    private String uploadDir;

    private final TeamRepository teamRepository;
    private final MeetingRepository meetingRepository;
    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ReportRepository reportRepository;
    private final ReportAttachmentRepository reportAttachmentRepository;
    private final ProfileUpdateService profileUpdateService;
    private final NotificationService notificationService;
    private final AttendanceService attendanceService;
    private final PayrollTemplateRepository payrollTemplateRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipService payslipService;
    private final AttendanceRepository attendanceRepository;

    public EmployeeController() {
        this.teamRepository = null;
        this.meetingRepository = null;
        this.taskRepository = null;
        this.taskAttachmentRepository = null;
        this.reportRepository = null;
        this.reportAttachmentRepository = null;
        this.profileUpdateService = null;
        this.notificationService = null;
        this.attendanceService = null;
        this.payrollTemplateRepository = null;
        this.payslipRepository = null;
        this.payslipService = null;
        this.attendanceRepository = null;
    }

    @Autowired
    public EmployeeController(UserRepository userRepository,
            HolidayRepository holidayRepository,
            LeaveRequestRepository leaveRequestRepository,
            TeamRepository teamRepository,
            MeetingRepository meetingRepository,
            TaskRepository taskRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            ReportRepository reportRepository,
            ReportAttachmentRepository reportAttachmentRepository,
            ProfileUpdateService profileUpdateService,
            NotificationService notificationService,
            AttendanceService attendanceService,
            PayrollTemplateRepository payrollTemplateRepository,
            PayslipRepository payslipRepository,
            PayslipService payslipService,
            AttendanceRepository attendanceRepository) {
        super(userRepository, holidayRepository, leaveRequestRepository);
        this.teamRepository = teamRepository;
        this.meetingRepository = meetingRepository;
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.reportRepository = reportRepository;
        this.reportAttachmentRepository = reportAttachmentRepository;
        this.profileUpdateService = profileUpdateService;
        this.notificationService = notificationService;
        this.attendanceService = attendanceService;
        this.payrollTemplateRepository = payrollTemplateRepository;
        this.payslipRepository = payslipRepository;
        this.payslipService = payslipService;
        this.attendanceRepository = attendanceRepository;
    }
    // ── helpers ───────────────────────────────────────────────────────────

    private void injectUser(Model model) {
        var emp = getCurrentEmployee();
        model.addAttribute("employeeName", emp != null ? emp.getUsername() : "Employee");
        model.addAttribute("employeeRole", "Employee");
    }

    private User getCurrentEmployee() {
        var username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    private Attendance getAndValidateTodayAttendance(User emp, LocalDate today, String leaveErrorMessage,
            RedirectAttributes ra) {
        if (hasApprovedLeave(emp, today)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, leaveErrorMessage);
            return null;
        }
        var opt = attendanceRepository.findByUserAndDate(emp, today);
        if (opt.isEmpty()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, MSG_NOT_PUNCHED_IN);
            return null;
        }
        return opt.get();
    }

    // buildDayList (with leave-date support) is inherited from BaseController.

    private void injectStats(Model model) {
        model.addAttribute("activeProjectsCount", 0);
        model.addAttribute(ATTR_COMPLETED_TASKS, 0);
        model.addAttribute("attendanceRate", "0%");
        var emp = getCurrentEmployee();
        var pendingLeaves = emp != null ? leaveRequestRepository.countByEmployeeAndStatus(emp, STATUS_PENDING_UPPER)
                : 0;
        var approvedLeaves = emp != null ? leaveRequestRepository.countByEmployeeAndStatus(emp, STATUS_APPROVED) : 0;
        var rejectedLeaves = emp != null ? leaveRequestRepository.countByEmployeeAndStatus(emp, STATUS_REJECTED_UPPER)
                : 0;

        model.addAttribute("pendingLeaves", pendingLeaves);
        model.addAttribute("approvedLeaves", approvedLeaves);
        model.addAttribute("rejectedLeaves", rejectedLeaves);
        model.addAttribute("attendanceMonth", "May 2026");
        model.addAttribute("presentDays", 0);
        model.addAttribute("absentDays", 0);
        model.addAttribute("leaveDays", 0);
        model.addAttribute("attendancePercent", 0);
        model.addAttribute("lastCheckin", "—");
        var pendingTasks = Collections.<Task>emptyList();
        if (emp != null) {
            var tenant = getTenantSegment(emp);
            var allTasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
            pendingTasks = allTasks.stream()
                    .filter(t -> !STATUS_DONE.equalsIgnoreCase(t.getStatus()))
                    .sorted(java.util.Comparator.comparing(Task::getId).reversed())
                    .toList();
        }
        model.addAttribute("myProjects", Collections.emptyList());
        model.addAttribute(ATTR_PENDING_TASKS, pendingTasks);
        model.addAttribute("leaveRequests",
                emp != null ? leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(emp) : Collections.emptyList());
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        injectUser(model);
        injectStats(model);

        var emp = getCurrentEmployee();
        if (emp != null) {
            model.addAttribute("employeeStatus", emp.getStatus());
        } else {
            model.addAttribute("employeeStatus", STATUS_ACTIVE);
        }

        // ── Team info ─────────────────────────────────────────────────────
        if (emp != null) {
            var tenant = getTenantSegment(emp);
            var myTeams = teamRepository.findByMemberAndTenant(emp, tenant);
            if (!myTeams.isEmpty()) {
                var team = myTeams.get(0);
                model.addAttribute(ATTR_MY_TEAM_NAME, team.getName());
                model.addAttribute(ATTR_MY_TEAM_MANAGER,
                        team.getManager() != null ? team.getManager().getUsername() : "—");
                model.addAttribute(ATTR_MY_TEAM_MEMBERS, team.getMembers());
                model.addAttribute(ATTR_MY_TEAM_SIZE, team.getMembers().size());
            } else {
                model.addAttribute(ATTR_MY_TEAM_NAME, null);
                model.addAttribute(ATTR_MY_TEAM_MANAGER, "—");
                model.addAttribute(ATTR_MY_TEAM_MEMBERS, Collections.emptyList());
                model.addAttribute(ATTR_MY_TEAM_SIZE, 0);
            }
        } else {
            model.addAttribute(ATTR_MY_TEAM_NAME, null);
            model.addAttribute(ATTR_MY_TEAM_MANAGER, "—");
            model.addAttribute(ATTR_MY_TEAM_MEMBERS, Collections.emptyList());
            model.addAttribute(ATTR_MY_TEAM_SIZE, 0);
        }

        addAnalyticsAttributes(model, dashboardAnalytics());
        return "employee-dashboard";
    }

    @GetMapping("/dashboard/analytics")
    @ResponseBody
    public Map<String, Object> dashboardAnalytics() {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return buildDashboardAnalytics(Collections.emptyList(), Collections.emptyList());
        }

        var tenant = getTenantSegment(emp);
        var myTasks = taskRepository.findByAssignedToAndTenantSegment(emp.getUsername(), tenant);
        var teammates = new ArrayList<User>();
        var myTeams = teamRepository.findByMemberAndTenant(emp, tenant);
        if (!myTeams.isEmpty()) {
            teammates = new ArrayList<>(myTeams.get(0).getMembers());
        }

        var data = buildDashboardAnalytics(myTasks, teammates);
        data.put(ATTR_COMPLETED_TASKS, data.get(STATUS_DONE_KEY));
        data.put("pendingTaskTotal", myTasks.stream()
                .filter(t -> !STATUS_DONE.equalsIgnoreCase(t.getStatus()))
                .count());
        return data;
    }

    private Map<String, Object> buildDashboardAnalytics(List<Task> tasks, List<User> teammates) {
        return buildAnalyticsMap(tasks, teammates, true, false);
    }

    private void addAnalyticsAttributes(Model model, Map<String, Object> data) {
        populateAnalyticsAttributes(model, data);
        model.addAttribute(ATTR_COMPLETED_TASKS, data.get(STATUS_DONE_KEY));
    }

    @GetMapping("/tasks")
    public String tasksPage(Model model) {
        injectUser(model);
        injectStats(model);

        var emp = getCurrentEmployee();
        if (emp != null) {
            var tenant = getTenantSegment(emp);
            var username = emp.getUsername();

            // Load only tasks assigned to this employee in their tenant
            var myTasks = taskRepository.findByAssignedToAndTenantSegment(username, tenant);
            myTasks.sort(java.util.Comparator.comparing(Task::getId).reversed());

            var completed = myTasks.stream().filter(t -> STATUS_DONE.equalsIgnoreCase(t.getStatus())).count();
            var inProgress = myTasks.stream().filter(t -> STATUS_IN_PROGRESS.equalsIgnoreCase(t.getStatus())).count();
            var pending = myTasks.stream().filter(t -> STATUS_PENDING.equalsIgnoreCase(t.getStatus())).count();

            model.addAttribute("myTasks", myTasks);
            model.addAttribute("taskHistory", myTasks);
            model.addAttribute("taskHistoryCount", myTasks.size());
            model.addAttribute(ATTR_COMPLETED_TASKS, completed);
            model.addAttribute("inProgressTasks", inProgress);
            model.addAttribute(ATTR_PENDING_TASKS, pending);
            model.addAttribute("totalTasks", myTasks.size());
        } else {
            model.addAttribute("myTasks", java.util.Collections.emptyList());
            model.addAttribute("taskHistory", java.util.Collections.emptyList());
            model.addAttribute("taskHistoryCount", 0);
            model.addAttribute(ATTR_COMPLETED_TASKS, 0);
            model.addAttribute("inProgressTasks", 0);
            model.addAttribute(ATTR_PENDING_TASKS, 0);
            model.addAttribute("totalTasks", 0);
        }

        return "employee-tasks";
    }

    private void handleTaskAttachment(MultipartFile attachment, Task task, User emp) throws IOException {
        if (attachment == null || attachment.isEmpty()) {
            return;
        }
        var fileData = attachment.getBytes();
        var contentType = attachment.getContentType();
        if (contentType == null) {
            contentType = OCTET_STREAM;
        }

        var taskAttachment = new TaskAttachment(
                task,
                attachment.getOriginalFilename(),
                fileData,
                contentType,
                emp.getUsername());
        taskAttachmentRepository.save(taskAttachment);

        var existingNames = new ArrayList<String>();
        if (task.getAttachmentPaths() != null && !task.getAttachmentPaths().isBlank()) {
            existingNames.addAll(java.util.Arrays.asList(task.getAttachmentPaths().split(",")));
        }
        existingNames.add(attachment.getOriginalFilename());
        task.setAttachmentPaths(String.join(",", existingNames));
    }

    @PostMapping("/tasks/update-status/{id}")
    public String updateTaskStatus(@PathVariable Long id,
            @RequestParam String status,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You need to sign in to update a task status.");
            return REDIRECT_EMPLOYEE_TASKS + ERROR_AUTH;
        }

        var task = taskRepository.findById(id).orElse(null);
        if (task == null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Task not found.");
            return REDIRECT_EMPLOYEE_TASKS + "?error=notfound";
        }

        var tenant = getTenantSegment(emp);
        if (!emp.getUsername().equals(task.getAssignedTo()) || !tenant.equals(task.getTenantSegment())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You can only update your own assigned tasks.");
            return REDIRECT_EMPLOYEE_TASKS + "?error=denied";
        }

        // Handle file attachment if provided (optional now)
        if (attachment != null && !attachment.isEmpty()) {
            try {
                handleTaskAttachment(attachment, task, emp);
            } catch (IOException e) {
                ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "File upload failed: " + e.getMessage());
                return REDIRECT_EMPLOYEE_TASKS + "?error=upload";
            }
        }

        // Update task status
        var normalizedStatus = STATUS_PENDING;
        if (STATUS_DONE.equalsIgnoreCase(status)) {
            normalizedStatus = STATUS_DONE;
        } else if (STATUS_IN_PROGRESS.equalsIgnoreCase(status)) {
            normalizedStatus = STATUS_IN_PROGRESS;
        }
        task.setStatus(normalizedStatus);

        // If employee marks as done, set verification status to waiting-for-review
        if (STATUS_DONE.equalsIgnoreCase(normalizedStatus)) {
            task.setVerificationStatus(STATUS_WAITING_FOR_REVIEW);
        } else {
            // If marking as in-progress or pending, reset verification to pending
            task.setVerificationStatus(STATUS_PENDING);
        }

        taskRepository.save(task);

        notificationService.notifyTaskStatusUpdated(emp, task, normalizedStatus);

        var message = "Task status updated to " + normalizedStatus + ".";
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, message);
        return REDIRECT_EMPLOYEE_TASKS + SUCCESS;
    }

    // ── ATTENDANCE ────────────────────────────────────────────────────────

    private void populatePunchModel(Model model, Optional<Attendance> todayOpt) {
        var punchedIn = todayOpt.isPresent();
        var punchedOut = todayOpt.map(a -> a.getCheckOut() != null).orElse(false);
        var onBreak = todayOpt.map(a -> (a.getBreakStart() != null && a.getBreakEnd() == null) ||
                (a.getBreak2Start() != null && a.getBreak2End() == null)).orElse(false);
        var breakDone = todayOpt.map(a -> a.getBreak2End() != null).orElse(false);
        var canStartBreak = punchedIn && !punchedOut && !onBreak && !breakDone &&
                todayOpt.map(a -> a.getBreakStart() == null ||
                        (a.getBreakEnd() != null && a.getBreak2Start() == null)).orElse(false);

        model.addAttribute("punchedIn", punchedIn);
        model.addAttribute("punchedOut", punchedOut);
        model.addAttribute("onBreak", onBreak);
        model.addAttribute("breakDone", breakDone);
        model.addAttribute("canStartBreak", canStartBreak);
    }

    @GetMapping("/attendance")
    public String attendancePage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            Model model) {

        attendanceService.processAutoPunchOuts();

        injectUser(model);
        injectStats(model);

        var emp = getCurrentEmployee();
        if (emp == null) {
            return "redirect:/login";
        }
        var today = LocalDate.now();

        var range = resolveDateRange(from, to);
        var filterFrom = range[0];
        var filterTo = range[1];

        // Fetch real records in range
        var tenant = getTenantSegment(emp);
        var records = attendanceRepository.findByUserAndDateBetweenOrderByDateDesc(emp, filterFrom, filterTo);

        // Holidays in range
        var holidays = fetchHolidays(tenant, filterFrom, filterTo);

        // Today's record — drives button state
        var todayOpt = attendanceRepository.findByUserAndDate(emp, today);

        // Is today a holiday?
        var todayHolidayName = holidays.get(today);
        var isHolidayToday = todayHolidayName != null;

        populatePunchModel(model, todayOpt);

        // Build merged day list (fills absent/weekend/holiday gaps)
        var todayOnLeave = hasApprovedLeave(emp, today);

        var allDays = buildDayList(records, filterFrom, filterTo, holidays, emp);

        // Apply status filter
        var filteredDays = allDays;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            filteredDays = allDays.stream()
                    .filter(d -> d.getStatus().equalsIgnoreCase(status))
                    .toList();
        }

        // Stats from all-time records
        var allRecords = attendanceRepository.findByUserOrderByDateDesc(emp);
        var presentCount = allRecords.stream()
                .filter(a -> "present".equalsIgnoreCase(a.getStatus()) || "late".equalsIgnoreCase(a.getStatus()))
                .count();
        var lateCount = allRecords.stream()
                .filter(a -> "late".equalsIgnoreCase(a.getStatus()))
                .count();

        model.addAttribute("attendanceDays", filteredDays);
        model.addAttribute("totalRecords", filteredDays.size());
        model.addAttribute("todayRecord", todayOpt.orElse(null));
        model.addAttribute("todayOnLeave", todayOnLeave);
        model.addAttribute("isHolidayToday", isHolidayToday);
        model.addAttribute("todayHolidayName", todayHolidayName);
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("filterFrom", filterFrom != null ? filterFrom.toString() : "");
        model.addAttribute("filterTo", filterTo != null ? filterTo.toString() : "");
        model.addAttribute("filterStatus", status != null ? status : "all");

        return "employee-attendance";
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_AUTH;
        }

        var today = LocalDate.now();
        var tenant = getTenantSegment(emp);

        // Block punch-in on holidays
        if (holidayRepository.findByDateAndTenantSegment(today.toString(), tenant).isPresent()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Today is a holiday. Punch-in is not allowed.");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=holiday";
        }

        if (hasApprovedLeave(emp, today)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You are on approved leave today. Punch-in is not allowed.");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=leave";
        }

        if (attendanceRepository.findByUserAndDate(emp, today).isPresent()) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched in today.");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=already";
        }

        var now = LocalTime.now();
        var status = now.isAfter(LocalTime.of(9, 30)) ? "late" : "present";

        var att = new Attendance();
        att.setUser(emp);
        att.setDate(today);
        att.setCheckIn(now);
        att.setStatus(status);
        att.setTenantSegment(tenant);
        attendanceRepository.save(att);
        notificationService.notifyAttendanceUpdated(emp, "punch-in");

        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Punched in at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
        return REDIRECT_EMPLOYEE_ATTENDANCE + SUCCESS;
    }

    @PostMapping("/attendance/punch-out")
    public String punchOut(RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_AUTH;
        }

        var today = LocalDate.now();
        var att = getAndValidateTodayAttendance(emp, today,
                "You are on approved leave today. Punch-out is not allowed.", ra);
        if (att == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_INVALID;
        }
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched out today.");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=already";
        }

        var now = LocalTime.now();
        att.setCheckOut(now);

        // Recalculate status based on worked hours:
        var mins = att.getWorkedMinutes();
        if (mins >= 0 && mins < 240) {
            att.setStatus("absent");
        } else if (mins >= 240 && mins < 360) {
            att.setStatus("half-day");
        }

        attendanceRepository.save(att);
        notificationService.notifyAttendanceUpdated(emp, "punch-out");

        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Punched out at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
        return REDIRECT_EMPLOYEE_ATTENDANCE + SUCCESS;
    }

    @PostMapping("/attendance/break-start")
    public String breakStart(RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_AUTH;
        }

        var today = LocalDate.now();
        var att = getAndValidateTodayAttendance(emp, today,
                "You are on approved leave today. Break actions are not allowed.", ra);
        if (att == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_INVALID;
        }
        if (att.getCheckOut() != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "You have already punched out.");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=punchedout";
        }

        var now = LocalTime.now();

        // Use break 1 slot if not yet started
        if (att.getBreakStart() == null) {
            att.setBreakStart(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(emp, "break-1-start");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 1 started at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?success=break1";
        }

        // Use break 2 slot if break 1 is done and break 2 not yet started
        if (att.getBreakEnd() != null && att.getBreak2Start() == null) {
            att.setBreak2Start(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(emp, "break-2-start");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 2 started at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?success=break2";
        }

        ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No more breaks available today.");
        return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=nobreaks";
    }

    @PostMapping("/attendance/break-end")
    public String breakEnd(RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_AUTH;
        }

        var today = LocalDate.now();
        var att = getAndValidateTodayAttendance(emp, today,
                "You are on approved leave today. Break actions are not allowed.", ra);
        if (att == null) {
            return REDIRECT_EMPLOYEE_ATTENDANCE + ERROR_INVALID;
        }

        var now = LocalTime.now();

        // End break 2 if it's active
        if (att.getBreak2Start() != null && att.getBreak2End() == null) {
            att.setBreak2End(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(emp, "break-2-end");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 2 ended at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?success=break2end";
        }

        // End break 1 if it's active
        if (att.getBreakStart() != null && att.getBreakEnd() == null) {
            att.setBreakEnd(now);
            attendanceRepository.save(att);
            notificationService.notifyAttendanceUpdated(emp, "break-1-end");
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    "Break 1 ended at " + String.format(TIME_FORMAT, now.getHour(), now.getMinute()) + ".");
            return REDIRECT_EMPLOYEE_ATTENDANCE + "?success=break1end";
        }

        ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "No active break to end.");
        return REDIRECT_EMPLOYEE_ATTENDANCE + "?error=noactivebreak";
    }

    // ── other pages ───────────────────────────────────────────────────────

    @GetMapping("/calendar")
    public String calendarPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-calendar";
    }

    private List<Meeting> filterActiveMeetings(List<Meeting> all, LocalDate today, LocalTime now) {
        return all.stream().filter(m -> {
            if (m.getMeetingDate().isBefore(today))
                return false;
            if (m.getMeetingDate().isAfter(today))
                return true;
            if (m.getMeetingTime() == null)
                return true;
            int dur = (m.getDuration() != null) ? m.getDuration() : 0;
            return !m.getMeetingTime().plusMinutes(dur).isBefore(now);
        }).toList();
    }

    private List<Meeting> filterPastMeetings(List<Meeting> all, LocalDate today, LocalTime now) {
        return all.stream().filter(m -> {
            if (m.getMeetingDate().isBefore(today))
                return true;
            if (!m.getMeetingDate().equals(today))
                return false;
            if (m.getMeetingTime() == null)
                return false;
            int dur = (m.getDuration() != null) ? m.getDuration() : 0;
            return m.getMeetingTime().plusMinutes(dur).isBefore(now);
        }).toList();
    }

    @GetMapping("/meetings")
    public String meetingsPage(Model model) {
        injectUser(model);
        injectStats(model);
        var emp = getCurrentEmployee();
        if (emp != null) {
            var tenant = getTenantSegment(emp);
            var username = emp.getUsername();
            var all = meetingRepository.findAllMeetingsForUserOrHost(tenant, username);
            var today = LocalDate.now();
            var now = LocalTime.now();
            var active = filterActiveMeetings(all, today, now);
            var past = filterPastMeetings(all, today, now);
            model.addAttribute("meetings", active);
            model.addAttribute("pastMeetings", past);
        } else {
            model.addAttribute("meetings", Collections.<Meeting>emptyList());
            model.addAttribute("pastMeetings", Collections.<Meeting>emptyList());
        }
        return "employee-meetings";
    }

    @GetMapping("/leaves")
    public String leavesPage(Model model) {
        injectUser(model);
        injectStats(model);
        return "employee-leaves";
    }

    private String validateLeaveRequest(String type, String fromDate, String toDate, String reason) {
        if (type == null || type.isBlank()) {
            return "Leave type is required.";
        }
        if (reason == null || reason.isBlank()) {
            return "Leave reason is required.";
        }
        if (reason.trim().length() > 255) {
            return "Reason cannot exceed 255 characters.";
        }
        if (fromDate == null || fromDate.isBlank() || !fromDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return "Invalid start date format.";
        }
        if (toDate == null || toDate.isBlank() || !toDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return "Invalid end date format.";
        }
        return null;
    }

    @PostMapping("/leaves")
    public String submitLeave(@RequestParam String type,
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam String reason,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Session expired. Please log in again.");
            return REDIRECT_EMPLOYEE_LEAVES + ERROR_AUTH;
        }

        var validationError = validateLeaveRequest(type, fromDate, toDate, reason);
        if (validationError != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
            return REDIRECT_EMPLOYEE_LEAVES + "?error=validation";
        }

        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromDate);
            to = LocalDate.parse(toDate);
        } catch (java.time.format.DateTimeParseException e) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Invalid date value.");
            return REDIRECT_EMPLOYEE_LEAVES + "?error=parse";
        }

        if (to.isBefore(from)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "To date cannot be before from date.");
            return REDIRECT_EMPLOYEE_LEAVES + "?error=dates";
        }

        var leave = new LeaveRequest();
        leave.setEmployee(emp);
        leave.setEmployeeName(emp.getUsername());
        leave.setTenantSegment(getTenantSegment(emp));
        leave.setType(type.trim());
        leave.setFromDate(from);
        leave.setToDate(to);
        leave.setReason(reason.trim());
        leave.setStatus(STATUS_PENDING_UPPER);

        if (attachment != null && !attachment.isEmpty()) {
            try {
                leave.setAttachmentName(attachment.getOriginalFilename());
                leave.setAttachmentContentType(
                        attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM);
                leave.setAttachmentData(attachment.getBytes());
            } catch (IOException e) {
                ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Attachment upload failed: " + e.getMessage());
                return REDIRECT_EMPLOYEE_LEAVES + "?error=upload";
            }
        }

        leaveRequestRepository.save(leave);
        notificationService.notifyLeaveSubmitted(leave);
        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Leave request submitted to HR.");
        return REDIRECT_EMPLOYEE_LEAVES + SUCCESS;
    }

    // ── REPORTS ───────────────────────────────────────────────────────────

    @GetMapping("/reports")
    public String reportsPage(Model model) {
        injectUser(model);
        injectStats(model);

        var emp = getCurrentEmployee();
        if (emp != null) {
            var tenant = getTenantSegment(emp);
            var myReports = reportRepository.findByRecipientId(
                    String.valueOf(emp.getId()), tenant);
            model.addAttribute("myReports", myReports);
            model.addAttribute("reportCount", myReports.size());
        } else {
            model.addAttribute("myReports", java.util.Collections.emptyList());
            model.addAttribute("reportCount", 0);
        }

        return "employee-reports";
    }

    @GetMapping("/reports/view/{attachmentId}")
    public ResponseEntity<byte[]> viewReportAttachment(@PathVariable Long attachmentId) {
        var att = reportAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null) {
            return ResponseEntity.notFound().build();
        }
        var ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(att.getFileData());
    }

    @GetMapping("/reports/download/{attachmentId}")
    public ResponseEntity<byte[]> downloadReportAttachment(@PathVariable Long attachmentId) {
        var att = reportAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null) {
            return ResponseEntity.notFound().build();
        }
        var ct = att.getContentType() != null ? att.getContentType() : OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + att.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(att.getFileData());
    }

    @GetMapping("/profile")
    public String profilePage(Model model) {
        injectUser(model);
        injectStats(model);
        var emp = getCurrentEmployee();
        model.addAttribute("employeeEmail", emp != null ? emp.getEmail() : "");
        return "employee-profile";
    }

    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirmPassword,
            HttpServletResponse response,
            RedirectAttributes ra) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return "redirect:/employee/profile" + ERROR_AUTH;
        }

        var success = profileUpdateService.updateProfile(emp, username, email, password, confirmPassword, ra, response);
        return success ? "redirect:/employee/profile" + SUCCESS : "redirect:/employee/profile?error=validation";
    }

    // ── DOWNLOAD TASK ATTACHMENT ──────────────────────────────────────────
    @GetMapping("/tasks/download/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
        var attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        var contentType = attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(attachment.getFileData());
    }

    // ── VIEW TASK ATTACHMENT INLINE ───────────────────────────────────────
    @GetMapping("/tasks/view/{attachmentId}")
    public ResponseEntity<byte[]> viewAttachment(@PathVariable Long attachmentId) {
        var attachment = taskAttachmentRepository.findById(attachmentId).orElse(null);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        var contentType = attachment.getContentType() != null ? attachment.getContentType() : OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + attachment.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(attachment.getFileData());
    }

    // ── PAYROLL ───────────────────────────────────────────────────────────

    @GetMapping("/payroll")
    public String payrollPage(Model model) {
        var emp = getCurrentEmployee();
        if (emp == null) {
            return "redirect:/login";
        }

        injectUser(model);
        injectStats(model);
        var tenant = getTenantSegment(emp);
        var ptOpt = payrollTemplateRepository.findByEmployeeAndTenantSegment(emp, tenant);
        if (ptOpt.isPresent()) {
            var pt = ptOpt.get();
            model.addAttribute(PAYROLL, pt);

            // Calculate real-time estimated leave deductions for the current month
            var currentMonth = java.time.LocalDate.now().getMonthValue();
            var currentYear = java.time.LocalDate.now().getYear();
            var leaveDeduction = payslipService.calculateLeaveDeduction(emp, pt.getBasicSalary(), currentMonth,
                    currentYear);
            var estimatedNet = pt.getNetSalary().subtract(leaveDeduction);
            if (estimatedNet.compareTo(java.math.BigDecimal.ZERO) < 0) {
                estimatedNet = java.math.BigDecimal.ZERO;
            }
            model.addAttribute("payrollLeaveDeduction", leaveDeduction);
            model.addAttribute("payrollEstimatedNet", estimatedNet);
        } else {
            model.addAttribute(PAYROLL, null);
        }

        // Fetch personal generated payslips
        var myPayslips = payslipRepository.findByEmployeeOrderByIdDesc(emp);
        model.addAttribute("myPayslips", myPayslips);

        model.addAttribute(ATTR_ACTIVE_PAGE, PAYROLL);
        return "employee-payroll";
    }

}
