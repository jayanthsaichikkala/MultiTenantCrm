package com.crm.demo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import com.crm.demo.controller.ManagerController.FileUploadException;
import com.crm.demo.model.DomainCategory;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.ReportAttachmentRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class CrmApplicationTests {

    private static final String TEST_PDF = "test.pdf";
    private static final String APP_PDF = "application/pdf";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_TYPE = "Content-Type";

    // ── Value constants ───────────────────────────────────────────────────────
    private static final String TASK_TITLE   = "Title";
    private static final String STATUS_PENDING_VAL = "pending";
    private static final String LEAVE_REASON = "reason";
    private static final String LEAVE_FROM   = "2030-01-01";
    private static final String LEAVE_TO     = "2030-01-02";
    private static final String STATUS_APPROVED_VAL = "Approved";
    private static final String KEY_STATUS_DONE = "statusDone";

    @InjectMocks
    private AdminController adminController;

    @Mock
    private ReportAttachmentRepository reportAttachmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ManagerController createManagerController(ReportAttachmentRepository repo) {
        return new ManagerController(
                null, null, null, null, null, null, null, null, null, null, null,
                repo, null, null, null, null, null, null
        );
    }

    private ManagerController createManagerController() {
        return createManagerController(null);
    }

    static Stream<String> contentTypeVariants() {
        return Stream.of(APP_PDF, null);
    }

    // ── DomainCategory ────────────────────────────────────────────────────────

    @Test
    void testDomainCategory() {
        DomainCategory category = new DomainCategory();
        category.setId(1L);
        category.setName("Sales");
        category.setTenantSegment("tenant1");

        assertEquals(1L, category.getId());
        assertEquals("Sales", category.getName());
        assertEquals("tenant1", category.getTenantSegment());
    }

    // ── AdminController ───────────────────────────────────────────────────────

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testAdminControllerViewReportAttachment(String contentType) {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setOriginalFilename(TEST_PDF);
        attachment.setContentType(contentType);
        attachment.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ResponseEntity<byte[]> response = adminController.viewReportAttachment(1L);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        assertEquals("inline; filename=\"" + TEST_PDF + "\"", response.getHeaders().getFirst(CONTENT_DISPOSITION));
        String expectedType = contentType != null ? contentType : "application/octet-stream";
        assertEquals(expectedType, response.getHeaders().getFirst(CONTENT_TYPE));
    }

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testAdminControllerDownloadReportAttachment(String contentType) {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setOriginalFilename(TEST_PDF);
        attachment.setContentType(contentType);
        attachment.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ResponseEntity<byte[]> response = adminController.downloadReportAttachment(1L);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        assertEquals("attachment; filename=\"" + TEST_PDF + "\"", response.getHeaders().getFirst(CONTENT_DISPOSITION));
        String expectedType = contentType != null ? contentType : "application/octet-stream";
        assertEquals(expectedType, response.getHeaders().getFirst(CONTENT_TYPE));
    }

    @Test
    void testAdminControllerReportAttachmentNotFound() {
        when(reportAttachmentRepository.findById(99L)).thenReturn(Optional.empty());
        ResponseEntity<byte[]> response = adminController.viewReportAttachment(99L);
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testAdminControllerDownloadReportAttachmentNotFound() {
        when(reportAttachmentRepository.findById(99L)).thenReturn(Optional.empty());
        ResponseEntity<byte[]> response = adminController.downloadReportAttachment(99L);
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
    }

    // ── ManagerController.processAttachments ─────────────────────────────────

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testManagerControllerProcessAttachments(String contentType) throws Exception {
        ManagerController managerController = createManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getBytes()).thenReturn(new byte[]{4, 5});
        when(file.getContentType()).thenReturn(contentType);

        List<ManagerController.TaskAttachmentInfo> attachmentInfos = new ArrayList<>();
        managerController.processAttachments(new MultipartFile[]{file}, attachmentInfos);
        assertEquals(1, attachmentInfos.size());
    }

    @Test
    void testManagerControllerProcessAttachmentsNullAndEmpty() throws Exception {
        ManagerController managerController = createManagerController();
        List<ManagerController.TaskAttachmentInfo> attachmentInfos = new ArrayList<>();

        managerController.processAttachments(null, attachmentInfos);
        assertTrue(attachmentInfos.isEmpty());

        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);
        managerController.processAttachments(new MultipartFile[]{null, fileEmpty}, attachmentInfos);
        assertTrue(attachmentInfos.isEmpty());
    }

    @Test
    void testManagerControllerProcessAttachmentsException() throws Exception {
        ManagerController managerController = createManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk error"));

        FileUploadException e = assertThrows(FileUploadException.class, () -> {
            managerController.processAttachments(new MultipartFile[]{file}, new ArrayList<>());
        });
        assertEquals("File upload failed: Disk error", e.getMessage());
    }

    // ── ManagerController.processReportAttachments ────────────────────────────

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testManagerControllerProcessReportAttachments(String contentType) throws Exception {
        ManagerController managerController = createManagerController(reportAttachmentRepository);

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getBytes()).thenReturn(new byte[]{9, 9});
        when(file.getContentType()).thenReturn(contentType);

        managerController.processReportAttachments(report, new MultipartFile[]{file});
        verify(reportAttachmentRepository, times(1)).save(any(ReportAttachment.class));
    }

    @Test
    void testManagerControllerProcessReportAttachmentsNullAndEmpty() throws Exception {
        ManagerController managerController = createManagerController();
        Report report = new Report();

        managerController.processReportAttachments(report, null);

        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);
        managerController.processReportAttachments(report, new MultipartFile[]{null, fileEmpty});

        verifyNoInteractions(reportAttachmentRepository);
    }

    @Test
    void testManagerControllerProcessReportAttachmentsException() throws Exception {
        ManagerController managerController = createManagerController(reportAttachmentRepository);

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk write error"));

        FileUploadException e = assertThrows(FileUploadException.class, () -> {
            managerController.processReportAttachments(report, new MultipartFile[]{file});
        });
        assertEquals("File upload failed: Disk write error", e.getMessage());
    }

    // ── ManagerController.validateTaskParams ──────────────────────────────────

    @Test
    void testValidateTaskParamsValid() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertNull(ctrl.validateTaskParams(TASK_TITLE, "desc", "High", STATUS_PENDING_VAL, future));
    }

    @Test
    void testValidateTaskParamsBlankTitle() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertEquals("Task title is required.", ctrl.validateTaskParams("  ", "desc", "High", STATUS_PENDING_VAL, future));
    }

    @Test
    void testValidateTaskParamsNullTitle() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertEquals("Task title is required.", ctrl.validateTaskParams(null, "desc", "High", STATUS_PENDING_VAL, future));
    }

    @Test
    void testValidateTaskParamsLongTitle() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertEquals("Task title cannot exceed 255 characters.", ctrl.validateTaskParams("a".repeat(256), null, "High", STATUS_PENDING_VAL, future));
    }

    @Test
    void testValidateTaskParamsLongDescription() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertEquals("Description cannot exceed 255 characters.", ctrl.validateTaskParams(TASK_TITLE, "d".repeat(256), "High", STATUS_PENDING_VAL, future));
    }

    @Test
    void testValidateTaskParamsInvalidPriority() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertEquals("Invalid priority selected.", ctrl.validateTaskParams(TASK_TITLE, null, "Critical", STATUS_PENDING_VAL, future));
    }

    @Test
    void testValidateTaskParamsInvalidStatus() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        assertEquals("Invalid status selected.", ctrl.validateTaskParams(TASK_TITLE, null, "High", "invalid-status", future));
    }

    @Test
    void testValidateTaskParamsBlankDueDate() {
        ManagerController ctrl = createManagerController();
        assertEquals("Please select a valid due date.", ctrl.validateTaskParams(TASK_TITLE, null, "High", STATUS_PENDING_VAL, ""));
    }

    // ── ManagerController.validateLeaveParams ─────────────────────────────────

    @Test
    void testValidateLeaveParamsValid() {
        ManagerController ctrl = createManagerController();
        assertNull(ctrl.validateLeaveParams("sick", "flu", LEAVE_FROM, LEAVE_TO));
    }

    @Test
    void testValidateLeaveParamsBlankType() {
        ManagerController ctrl = createManagerController();
        assertEquals("Leave type is required.", ctrl.validateLeaveParams("", LEAVE_REASON, LEAVE_FROM, LEAVE_TO));
    }

    @Test
    void testValidateLeaveParamsBlankReason() {
        ManagerController ctrl = createManagerController();
        assertEquals("Leave reason is required.", ctrl.validateLeaveParams("sick", "  ", LEAVE_FROM, LEAVE_TO));
    }

    @Test
    void testValidateLeaveParamsLongReason() {
        ManagerController ctrl = createManagerController();
        assertEquals("Reason cannot exceed 255 characters.", ctrl.validateLeaveParams("sick", "r".repeat(256), LEAVE_FROM, LEAVE_TO));
    }

    @Test
    void testValidateLeaveParamsInvalidFromDate() {
        ManagerController ctrl = createManagerController();
        assertEquals("Invalid start date format.", ctrl.validateLeaveParams("sick", LEAVE_REASON, "not-a-date", LEAVE_TO));
    }

    @Test
    void testValidateLeaveParamsInvalidToDate() {
        ManagerController ctrl = createManagerController();
        assertEquals("Invalid end date format.", ctrl.validateLeaveParams("sick", LEAVE_REASON, LEAVE_FROM, "bad"));
    }

    // ── ManagerController.validateTaskDates ───────────────────────────────────

    @Test
    void testValidateTaskDatesValid() {
        ManagerController ctrl = createManagerController();
        String future = LocalDate.now().plusDays(5).toString();
        LocalDate[] parsed = new LocalDate[2];
        assertNull(ctrl.validateTaskDates(null, future, parsed));
        assertEquals(LocalDate.parse(future), parsed[1]);
    }

    @Test
    void testValidateTaskDatesPastDueDate() {
        ManagerController ctrl = createManagerController();
        String past = LocalDate.now().minusDays(1).toString();
        LocalDate[] parsed = new LocalDate[2];
        assertEquals("Due date cannot be in the past.", ctrl.validateTaskDates(null, past, parsed));
    }

    @Test
    void testValidateTaskDatesInvalidDueDate() {
        ManagerController ctrl = createManagerController();
        LocalDate[] parsed = new LocalDate[2];
        assertEquals("Invalid due date value.", ctrl.validateTaskDates(null, "not-a-date", parsed));
    }

    @Test
    void testValidateTaskDatesStartAfterDue() {
        ManagerController ctrl = createManagerController();
        String due   = LocalDate.now().plusDays(2).toString();
        String start = LocalDate.now().plusDays(5).toString();
        LocalDate[] parsed = new LocalDate[2];
        assertEquals("Start date cannot be after due date.", ctrl.validateTaskDates(start, due, parsed));
    }

    @Test
    void testValidateTaskDatesValidWithStart() {
        ManagerController ctrl = createManagerController();
        String start = LocalDate.now().plusDays(1).toString();
        String due   = LocalDate.now().plusDays(5).toString();
        LocalDate[] parsed = new LocalDate[2];
        assertNull(ctrl.validateTaskDates(start, due, parsed));
    }

    // ── ManagerController.getTenantSegmentFromEmail ───────────────────────────

    @Test
    void testGetTenantSegmentFromEmailNormal() {
        ManagerController ctrl = createManagerController();
        assertEquals("tcs", ctrl.getTenantSegmentFromEmail("mgr.tcs@crm.com"));
    }

    @Test
    void testGetTenantSegmentFromEmailNoDot() {
        ManagerController ctrl = createManagerController();
        assertEquals("admin", ctrl.getTenantSegmentFromEmail("admin@crm.com"));
    }

    @Test
    void testGetTenantSegmentFromEmailNull() {
        ManagerController ctrl = createManagerController();
        assertEquals("", ctrl.getTenantSegmentFromEmail(null));
    }

    @Test
    void testGetTenantSegmentFromEmailNoAt() {
        ManagerController ctrl = createManagerController();
        assertEquals("", ctrl.getTenantSegmentFromEmail("notanemail"));
    }

    // ── ManagerController.calculateWorkingDays ────────────────────────────────

    @Test
    void testCalculateWorkingDaysWeekdays() {
        ManagerController ctrl = createManagerController();
        // Monday to Friday = 5 working days
        assertEquals(5, ctrl.calculateWorkingDays(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)));
    }

    @Test
    void testCalculateWorkingDaysWeekend() {
        ManagerController ctrl = createManagerController();
        // Saturday only - returns min 1
        LocalDate sat = LocalDate.of(2025, 1, 11);
        assertEquals(1, ctrl.calculateWorkingDays(sat, sat));
    }

    @Test
    void testCalculateWorkingDaysSingleWeekday() {
        ManagerController ctrl = createManagerController();
        LocalDate mon = LocalDate.of(2025, 1, 6);
        assertEquals(1, ctrl.calculateWorkingDays(mon, mon));
    }

    // ── ManagerController.validateReportParams ────────────────────────────────

    @Test
    void testValidateReportParamsValid() {
        ManagerController ctrl = createManagerController();
        assertNull(ctrl.validateReportParams("Q1 Report", "Summary", Arrays.asList(1L, 2L)));
    }

    @Test
    void testValidateReportParamsBlankTitle() {
        ManagerController ctrl = createManagerController();
        assertEquals("Report title is required.", ctrl.validateReportParams("", "msg", Arrays.asList(1L)));
    }

    @Test
    void testValidateReportParamsNullTitle() {
        ManagerController ctrl = createManagerController();
        assertEquals("Report title is required.", ctrl.validateReportParams(null, "msg", Arrays.asList(1L)));
    }

    @Test
    void testValidateReportParamsLongTitle() {
        ManagerController ctrl = createManagerController();
        assertEquals("Report title cannot exceed 200 characters.", ctrl.validateReportParams("t".repeat(201), "msg", Arrays.asList(1L)));
    }

    @Test
    void testValidateReportParamsLongMessage() {
        ManagerController ctrl = createManagerController();
        assertEquals("Message cannot exceed 255 characters.", ctrl.validateReportParams(TASK_TITLE, "m".repeat(256), Arrays.asList(1L)));
    }

    @Test
    void testValidateReportParamsNoRecipients() {
        ManagerController ctrl = createManagerController();
        assertEquals("Please select at least one recipient.", ctrl.validateReportParams(TASK_TITLE, "msg", Collections.emptyList()));
    }

    @Test
    void testValidateReportParamsNullRecipients() {
        ManagerController ctrl = createManagerController();
        assertEquals("Please select at least one recipient.", ctrl.validateReportParams(TASK_TITLE, "msg", null));
    }

    // ── ManagerController.buildDashboardAnalytics ─────────────────────────────

    @Test
    void testBuildDashboardAnalyticsEmpty() {
        ManagerController ctrl = createManagerController();
        java.util.Map<String, Object> result = ctrl.buildDashboardAnalytics(Collections.emptyList(), Collections.emptyList());

        assertNotNull(result);
        assertEquals(0L, result.get(KEY_STATUS_DONE));
        assertEquals(0L, result.get("statusInProgress"));
        assertEquals(0L, result.get("statusPending"));
        assertEquals(0L, result.get("priorityHigh"));
        assertEquals(0L, result.get("activeTeam"));
        assertEquals(0L, result.get("inactiveTeam"));
        assertTrue(result.containsKey("memberLabels"));
        assertTrue(result.containsKey("memberTaskCounts"));
    }

    @Test
    void testBuildDashboardAnalyticsWithTasks() {
        ManagerController ctrl = createManagerController();

        Task doneTask = new Task();
        doneTask.setStatus("done");
        doneTask.setPriority("High");
        doneTask.setVerificationStatus("approved");
        doneTask.setAssignedTo("alice");

        Task pendingTask = new Task();
        pendingTask.setStatus(STATUS_PENDING_VAL);
        pendingTask.setPriority("Medium");
        pendingTask.setAssignedTo("bob");

        User alice = new User();
        alice.setUsername("alice");
        alice.setStatus("active");

        java.util.Map<String, Object> result = ctrl.buildDashboardAnalytics(
                Arrays.asList(doneTask, pendingTask), Arrays.asList(alice));

        assertEquals(1L, result.get(KEY_STATUS_DONE));
        assertEquals(1L, result.get("statusPending"));
        assertEquals(1L, result.get("priorityHigh"));
        assertEquals(1L, result.get("priorityMedium"));
        assertEquals(1L, result.get("verified"));
        assertEquals(1L, result.get("activeTeam"));
        assertEquals(0L, result.get("inactiveTeam"));
    }

    @Test
    void testBuildDashboardAnalyticsNullInputs() {
        ManagerController ctrl = createManagerController();
        java.util.Map<String, Object> result = ctrl.buildDashboardAnalytics(null, null);
        assertNotNull(result);
        assertEquals(0L, result.get(KEY_STATUS_DONE));
    }

    // ── ManagerController.determineGrade ──────────────────────────────────────

    @Test
    void testDetermineGradeAPlus() {
        ManagerController ctrl = createManagerController();
        assertEquals("A+", ctrl.determineGrade(90));
        assertEquals("A+", ctrl.determineGrade(100));
    }

    @Test
    void testDetermineGradeA() {
        ManagerController ctrl = createManagerController();
        assertEquals("A", ctrl.determineGrade(75));
        assertEquals("A", ctrl.determineGrade(89));
    }

    @Test
    void testDetermineGradeB() {
        ManagerController ctrl = createManagerController();
        assertEquals("B", ctrl.determineGrade(60));
        assertEquals("B", ctrl.determineGrade(74));
    }

    @Test
    void testDetermineGradeC() {
        ManagerController ctrl = createManagerController();
        assertEquals("C", ctrl.determineGrade(45));
        assertEquals("C", ctrl.determineGrade(59));
    }

    @Test
    void testDetermineGradeD() {
        ManagerController ctrl = createManagerController();
        assertEquals("D", ctrl.determineGrade(0));
        assertEquals("D", ctrl.determineGrade(44));
    }

    // ── ManagerController.calculateOverlapLeaveDays ───────────────────────────

    @Test
    void testCalculateOverlapLeaveDaysNull() {
        ManagerController ctrl = createManagerController();
        assertEquals(0, ctrl.calculateOverlapLeaveDays(null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));
    }

    @Test
    void testCalculateOverlapLeaveDaysNotApproved() {
        ManagerController ctrl = createManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus("Pending");
        lr.setFromDate(LocalDate.of(2025, 1, 6));
        lr.setToDate(LocalDate.of(2025, 1, 10));
        assertEquals(0, ctrl.calculateOverlapLeaveDays(lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));
    }

    @Test
    void testCalculateOverlapLeaveDaysApprovedFullOverlap() {
        ManagerController ctrl = createManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(LocalDate.of(2025, 1, 6));  // Monday
        lr.setToDate(LocalDate.of(2025, 1, 10));   // Friday = 5 weekdays
        assertEquals(5, ctrl.calculateOverlapLeaveDays(lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));
    }

    @Test
    void testCalculateOverlapLeaveDaysApprovedPartialOverlap() {
        ManagerController ctrl = createManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(LocalDate.of(2025, 1, 1));
        lr.setToDate(LocalDate.of(2025, 1, 10));
        assertEquals(5, ctrl.calculateOverlapLeaveDays(lr, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)));
    }

    @Test
    void testCalculateOverlapLeaveDaysNoOverlap() {
        ManagerController ctrl = createManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(LocalDate.of(2025, 2, 1));
        lr.setToDate(LocalDate.of(2025, 2, 5));
        assertEquals(0, ctrl.calculateOverlapLeaveDays(lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));
    }

    @Test
    void testCalculateOverlapLeaveDaysNullDates() {
        ManagerController ctrl = createManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(null);
        lr.setToDate(null);
        assertEquals(0, ctrl.calculateOverlapLeaveDays(lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));
    }

    // =========================================================================
    // ADDITIONAL TEST CASES FOR AdminController
    // =========================================================================

    @Test
    void testAdminControllerAddUserRedirect() {
        String viewName = adminController.addUserRedirect();
        assertEquals("redirect:/admin/employees", viewName);
    }

    @Test
    void testAdminControllerToggleUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.toggleUser(99L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        verify(userRepository).findById(99L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAdminControllerToggleUserAdmin() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole("ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.toggleUser(2L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        verify(userRepository).findById(2L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAdminControllerToggleUserSuperAdmin() {
        User superAdmin = new User();
        superAdmin.setId(3L);
        superAdmin.setRole("SUPER_ADMIN");
        when(userRepository.findById(3L)).thenReturn(Optional.of(superAdmin));
        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.toggleUser(3L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        verify(userRepository).findById(3L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAdminControllerToggleUserActiveToInactive() {
        User employee = new User();
        employee.setId(4L);
        employee.setUsername("emp");
        employee.setRole("EMPLOYEE");
        employee.setStatus("active");
        employee.setEmail("emp.tenant@crm.com");

        User loggedInAdmin = new User();
        loggedInAdmin.setUsername("adminName");
        loggedInAdmin.setEmail("admin.tenant@crm.com");

        when(userRepository.findById(4L)).thenReturn(Optional.of(employee));
        when(userRepository.findByUsername("adminName")).thenReturn(loggedInAdmin);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("adminName");
        SecurityContextHolder.setContext(securityContext);

        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.toggleUser(4L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        assertEquals("inactive", employee.getStatus());
        verify(userRepository).save(employee);
        verify(notificationService).notifyEmployeeManagementChanged("tenant", "updated", "emp");
        verify(ra).addFlashAttribute("successMessage", "emp is now inactive.");
    }

    @Test
    void testAdminControllerToggleUserInactiveToActive() {
        User employee = new User();
        employee.setId(4L);
        employee.setUsername("emp");
        employee.setRole("EMPLOYEE");
        employee.setStatus("inactive");
        employee.setEmail("emp.tenant@crm.com");

        User loggedInAdmin = new User();
        loggedInAdmin.setUsername("adminName");
        loggedInAdmin.setEmail("admin.tenant@crm.com");

        when(userRepository.findById(4L)).thenReturn(Optional.of(employee));
        when(userRepository.findByUsername("adminName")).thenReturn(loggedInAdmin);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("adminName");
        SecurityContextHolder.setContext(securityContext);

        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.toggleUser(4L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        assertEquals("active", employee.getStatus());
        verify(userRepository).save(employee);
        verify(notificationService).notifyEmployeeManagementChanged("tenant", "updated", "emp");
        verify(ra).addFlashAttribute("successMessage", "emp is now active.");
    }

    @Test
    void testAdminControllerDeleteEmployeeNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.deleteEmployee(99L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        verify(userRepository).findById(99L);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void testAdminControllerDeleteEmployeeAdmin() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole("ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.deleteEmployee(2L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        verify(userRepository).findById(2L);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void testAdminControllerDeleteEmployeeSuccess() {
        User employee = new User();
        employee.setId(4L);
        employee.setUsername("emp");
        employee.setRole("EMPLOYEE");
        employee.setEmail("emp.tenant@crm.com");

        when(userRepository.findById(4L)).thenReturn(Optional.of(employee));

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("adminName");
        SecurityContextHolder.setContext(securityContext);

        RedirectAttributes ra = mock(RedirectAttributes.class);

        String viewName = adminController.deleteEmployee(4L, ra);

        assertEquals("redirect:/admin/employees", viewName);
        verify(notificationService).deleteAllForUser(4L);
        verify(userRepository).delete(employee);
        verify(notificationService).notifyEmployeeManagementChanged("", "deleted", "emp");
        verify(ra).addFlashAttribute("successMessage", "Employee 'emp' deleted.");
    }
}
