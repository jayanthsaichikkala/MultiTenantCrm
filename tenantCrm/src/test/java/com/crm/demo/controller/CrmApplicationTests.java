package com.crm.demo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Method;
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

import com.crm.demo.controller.ManagerController.FileUploadException;
import com.crm.demo.model.DomainCategory;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.ReportAttachmentRepository;

@ExtendWith(MockitoExtension.class)
class CrmApplicationTests {

    private static final String TEST_PDF = "test.pdf";
    private static final String APP_PDF = "application/pdf";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_TYPE = "Content-Type";

    // ── Method name constants ─────────────────────────────────────────────────
    private static final String M_VALIDATE_TASK_PARAMS   = "validateTaskParams";
    private static final String M_VALIDATE_LEAVE_PARAMS  = "validateLeaveParams";
    private static final String M_VALIDATE_TASK_DATES    = "validateTaskDates";
    private static final String M_VALIDATE_REPORT_PARAMS = "validateReportParams";
    private static final String M_GET_TENANT_SEGMENT     = "getTenantSegmentFromEmail";
    private static final String M_CALC_WORKING_DAYS      = "calculateWorkingDays";
    private static final String M_BUILD_ANALYTICS        = "buildDashboardAnalytics";
    private static final String M_DETERMINE_GRADE        = "determineGrade";
    private static final String M_CALC_OVERLAP_LEAVE     = "calculateOverlapLeaveDays";

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Invoke a private method on ManagerController via reflection. */
    private Object invokePrivate(ManagerController ctrl, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = ManagerController.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(ctrl, args);
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
        ManagerController managerController = new ManagerController();

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
        ManagerController managerController = new ManagerController();
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
        ManagerController managerController = new ManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk error"));

        try {
            managerController.processAttachments(new MultipartFile[]{file}, new ArrayList<>());
            fail("Expected exception");
        } catch (FileUploadException e) {
            assertEquals("File upload failed: Disk error", e.getMessage());
        }
    }

    // ── ManagerController.processReportAttachments ────────────────────────────

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testManagerControllerProcessReportAttachments(String contentType) throws Exception {
        ManagerController managerController = new ManagerController();
        managerController.reportAttachmentRepository = reportAttachmentRepository;

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
        ManagerController managerController = new ManagerController();
        Report report = new Report();

        managerController.processReportAttachments(report, null);

        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);
        managerController.processReportAttachments(report, new MultipartFile[]{null, fileEmpty});

        verifyNoInteractions(reportAttachmentRepository);
    }

    @Test
    void testManagerControllerProcessReportAttachmentsException() throws Exception {
        ManagerController managerController = new ManagerController();
        managerController.reportAttachmentRepository = reportAttachmentRepository;

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk write error"));

        try {
            managerController.processReportAttachments(report, new MultipartFile[]{file});
            fail("Expected exception");
        } catch (FileUploadException e) {
            assertEquals("File upload failed: Disk write error", e.getMessage());
        }
    }

    // ── ManagerController.validateTaskParams ──────────────────────────────────

    @Test
    void testValidateTaskParams_valid() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                TASK_TITLE, "desc", "High", STATUS_PENDING_VAL, future);
        assertNull(result);
    }

    @Test
    void testValidateTaskParams_blankTitle() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                "  ", "desc", "High", STATUS_PENDING_VAL, future);
        assertEquals("Task title is required.", result);
    }

    @Test
    void testValidateTaskParams_nullTitle() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                null, "desc", "High", STATUS_PENDING_VAL, future);
        assertEquals("Task title is required.", result);
    }

    @Test
    void testValidateTaskParams_longTitle() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        String longTitle = "a".repeat(256);
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                longTitle, null, "High", STATUS_PENDING_VAL, future);
        assertEquals("Task title cannot exceed 255 characters.", result);
    }

    @Test
    void testValidateTaskParams_longDescription() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        String longDesc = "d".repeat(256);
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                TASK_TITLE, longDesc, "High", STATUS_PENDING_VAL, future);
        assertEquals("Description cannot exceed 255 characters.", result);
    }

    @Test
    void testValidateTaskParams_invalidPriority() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                TASK_TITLE, null, "Critical", STATUS_PENDING_VAL, future);
        assertEquals("Invalid priority selected.", result);
    }

    @Test
    void testValidateTaskParams_invalidStatus() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(1).toString();
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                TASK_TITLE, null, "High", "invalid-status", future);
        assertEquals("Invalid status selected.", result);
    }

    @Test
    void testValidateTaskParams_blankDueDate() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                TASK_TITLE, null, "High", STATUS_PENDING_VAL, "");
        assertEquals("Please select a valid due date.", result);
    }

    // ── ManagerController.validateLeaveParams ─────────────────────────────────

    @Test
    void testValidateLeaveParams_valid() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_LEAVE_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class},
                "sick", "flu", LEAVE_FROM, LEAVE_TO);
        assertNull(result);
    }

    @Test
    void testValidateLeaveParams_blankType() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_LEAVE_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class},
                "", LEAVE_REASON, LEAVE_FROM, LEAVE_TO);
        assertEquals("Leave type is required.", result);
    }

    @Test
    void testValidateLeaveParams_blankReason() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_LEAVE_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class},
                "sick", "  ", LEAVE_FROM, LEAVE_TO);
        assertEquals("Leave reason is required.", result);
    }

    @Test
    void testValidateLeaveParams_longReason() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_LEAVE_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class},
                "sick", "r".repeat(256), LEAVE_FROM, LEAVE_TO);
        assertEquals("Reason cannot exceed 255 characters.", result);
    }

    @Test
    void testValidateLeaveParams_invalidFromDate() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_LEAVE_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class},
                "sick", LEAVE_REASON, "not-a-date", LEAVE_TO);
        assertEquals("Invalid start date format.", result);
    }

    @Test
    void testValidateLeaveParams_invalidToDate() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_LEAVE_PARAMS,
                new Class[]{String.class, String.class, String.class, String.class},
                "sick", LEAVE_REASON, LEAVE_FROM, "bad");
        assertEquals("Invalid end date format.", result);
    }

    // ── ManagerController.validateTaskDates ───────────────────────────────────

    @Test
    void testValidateTaskDates_valid() throws Exception {
        ManagerController ctrl = new ManagerController();
        String future = LocalDate.now().plusDays(5).toString();
        LocalDate[] parsed = new LocalDate[2];
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_DATES,
                new Class[]{String.class, String.class, LocalDate[].class},
                null, future, parsed);
        assertNull(result);
        assertEquals(LocalDate.parse(future), parsed[1]);
    }

    @Test
    void testValidateTaskDates_pastDueDate() throws Exception {
        ManagerController ctrl = new ManagerController();
        String past = LocalDate.now().minusDays(1).toString();
        LocalDate[] parsed = new LocalDate[2];
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_DATES,
                new Class[]{String.class, String.class, LocalDate[].class},
                null, past, parsed);
        assertEquals("Due date cannot be in the past.", result);
    }

    @Test
    void testValidateTaskDates_invalidDueDate() throws Exception {
        ManagerController ctrl = new ManagerController();
        LocalDate[] parsed = new LocalDate[2];
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_DATES,
                new Class[]{String.class, String.class, LocalDate[].class},
                null, "not-a-date", parsed);
        assertEquals("Invalid due date value.", result);
    }

    @Test
    void testValidateTaskDates_startAfterDue() throws Exception {
        ManagerController ctrl = new ManagerController();
        String due   = LocalDate.now().plusDays(2).toString();
        String start = LocalDate.now().plusDays(5).toString();
        LocalDate[] parsed = new LocalDate[2];
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_DATES,
                new Class[]{String.class, String.class, LocalDate[].class},
                start, due, parsed);
        assertEquals("Start date cannot be after due date.", result);
    }

    @Test
    void testValidateTaskDates_validWithStart() throws Exception {
        ManagerController ctrl = new ManagerController();
        String start = LocalDate.now().plusDays(1).toString();
        String due   = LocalDate.now().plusDays(5).toString();
        LocalDate[] parsed = new LocalDate[2];
        Object result = invokePrivate(ctrl, M_VALIDATE_TASK_DATES,
                new Class[]{String.class, String.class, LocalDate[].class},
                start, due, parsed);
        assertNull(result);
    }

    // ── ManagerController.getTenantSegmentFromEmail ───────────────────────────

    @Test
    void testGetTenantSegmentFromEmail_normal() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_GET_TENANT_SEGMENT,
                new Class[]{String.class}, "mgr.tcs@crm.com");
        assertEquals("tcs", result);
    }

    @Test
    void testGetTenantSegmentFromEmail_noDot() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_GET_TENANT_SEGMENT,
                new Class[]{String.class}, "admin@crm.com");
        assertEquals("admin", result);
    }

    @Test
    void testGetTenantSegmentFromEmail_null() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_GET_TENANT_SEGMENT,
                new Class[]{String.class}, (Object) null);
        assertEquals("", result);
    }

    @Test
    void testGetTenantSegmentFromEmail_noAt() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_GET_TENANT_SEGMENT,
                new Class[]{String.class}, "notanemail");
        assertEquals("", result);
    }

    // ── ManagerController.calculateWorkingDays ────────────────────────────────

    @Test
    void testCalculateWorkingDays_weekdays() throws Exception {
        ManagerController ctrl = new ManagerController();
        // Monday to Friday = 5 working days
        LocalDate mon = LocalDate.of(2025, 1, 6);
        LocalDate fri = LocalDate.of(2025, 1, 10);
        Object result = invokePrivate(ctrl, M_CALC_WORKING_DAYS,
                new Class[]{LocalDate.class, LocalDate.class}, mon, fri);
        assertEquals(5, result);
    }

    @Test
    void testCalculateWorkingDays_weekend() throws Exception {
        ManagerController ctrl = new ManagerController();
        // Saturday only - returns min 1
        LocalDate sat = LocalDate.of(2025, 1, 11);
        Object result = invokePrivate(ctrl, M_CALC_WORKING_DAYS,
                new Class[]{LocalDate.class, LocalDate.class}, sat, sat);
        assertEquals(1, result);
    }

    @Test
    void testCalculateWorkingDays_singleWeekday() throws Exception {
        ManagerController ctrl = new ManagerController();
        LocalDate mon = LocalDate.of(2025, 1, 6);
        Object result = invokePrivate(ctrl, M_CALC_WORKING_DAYS,
                new Class[]{LocalDate.class, LocalDate.class}, mon, mon);
        assertEquals(1, result);
    }

    // ── ManagerController.validateReportParams ────────────────────────────────

    @Test
    void testValidateReportParams_valid() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                "Q1 Report", "Summary", Arrays.asList(1L, 2L));
        assertNull(result);
    }

    @Test
    void testValidateReportParams_blankTitle() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                "", "msg", Arrays.asList(1L));
        assertEquals("Report title is required.", result);
    }

    @Test
    void testValidateReportParams_nullTitle() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                null, "msg", Arrays.asList(1L));
        assertEquals("Report title is required.", result);
    }

    @Test
    void testValidateReportParams_longTitle() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                "t".repeat(201), "msg", Arrays.asList(1L));
        assertEquals("Report title cannot exceed 200 characters.", result);
    }

    @Test
    void testValidateReportParams_longMessage() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                TASK_TITLE, "m".repeat(256), Arrays.asList(1L));
        assertEquals("Message cannot exceed 255 characters.", result);
    }

    @Test
    void testValidateReportParams_noRecipients() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                TASK_TITLE, "msg", Collections.emptyList());
        assertEquals("Please select at least one recipient.", result);
    }

    @Test
    void testValidateReportParams_nullRecipients() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_VALIDATE_REPORT_PARAMS,
                new Class[]{String.class, String.class, List.class},
                TASK_TITLE, "msg", null);
        assertEquals("Please select at least one recipient.", result);
    }

    // ── ManagerController.buildDashboardAnalytics ─────────────────────────────

    @Test
    void testBuildDashboardAnalytics_empty() throws Exception {
        ManagerController ctrl = new ManagerController();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> result = (java.util.Map<String, Object>) invokePrivate(
                ctrl, M_BUILD_ANALYTICS,
                new Class[]{List.class, List.class},
                Collections.emptyList(), Collections.emptyList());

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
    void testBuildDashboardAnalytics_withTasks() throws Exception {
        ManagerController ctrl = new ManagerController();

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

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> result = (java.util.Map<String, Object>) invokePrivate(
                ctrl, M_BUILD_ANALYTICS,
                new Class[]{List.class, List.class},
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
    void testBuildDashboardAnalytics_nullInputs() throws Exception {
        ManagerController ctrl = new ManagerController();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> result = (java.util.Map<String, Object>) invokePrivate(
                ctrl, M_BUILD_ANALYTICS,
                new Class[]{List.class, List.class},
                null, null);
        assertNotNull(result);
        assertEquals(0L, result.get(KEY_STATUS_DONE));
    }

    // ── ManagerController.determineGrade ──────────────────────────────────────

    @Test
    void testDetermineGrade_APlus() throws Exception {
        ManagerController ctrl = new ManagerController();
        assertEquals("A+", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 90));
        assertEquals("A+", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 100));
    }

    @Test
    void testDetermineGrade_A() throws Exception {
        ManagerController ctrl = new ManagerController();
        assertEquals("A", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 75));
        assertEquals("A", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 89));
    }

    @Test
    void testDetermineGrade_B() throws Exception {
        ManagerController ctrl = new ManagerController();
        assertEquals("B", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 60));
        assertEquals("B", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 74));
    }

    @Test
    void testDetermineGrade_C() throws Exception {
        ManagerController ctrl = new ManagerController();
        assertEquals("C", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 45));
        assertEquals("C", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 59));
    }

    @Test
    void testDetermineGrade_D() throws Exception {
        ManagerController ctrl = new ManagerController();
        assertEquals("D", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 0));
        assertEquals("D", invokePrivate(ctrl, M_DETERMINE_GRADE, new Class[]{int.class}, 44));
    }

    // ── ManagerController.calculateOverlapLeaveDays ───────────────────────────

    @Test
    void testCalculateOverlapLeaveDays_null() throws Exception {
        ManagerController ctrl = new ManagerController();
        Object result = invokePrivate(ctrl, M_CALC_OVERLAP_LEAVE,
                new Class[]{LeaveRequest.class, LocalDate.class, LocalDate.class},
                null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(0, result);
    }

    @Test
    void testCalculateOverlapLeaveDays_notApproved() throws Exception {
        ManagerController ctrl = new ManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus("Pending");
        lr.setFromDate(LocalDate.of(2025, 1, 6));
        lr.setToDate(LocalDate.of(2025, 1, 10));
        Object result = invokePrivate(ctrl, M_CALC_OVERLAP_LEAVE,
                new Class[]{LeaveRequest.class, LocalDate.class, LocalDate.class},
                lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(0, result);
    }

    @Test
    void testCalculateOverlapLeaveDays_approvedFullOverlap() throws Exception {
        ManagerController ctrl = new ManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(LocalDate.of(2025, 1, 6));  // Monday
        lr.setToDate(LocalDate.of(2025, 1, 10));   // Friday = 5 weekdays
        Object result = invokePrivate(ctrl, M_CALC_OVERLAP_LEAVE,
                new Class[]{LeaveRequest.class, LocalDate.class, LocalDate.class},
                lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(5, result);
    }

    @Test
    void testCalculateOverlapLeaveDays_approvedPartialOverlap() throws Exception {
        ManagerController ctrl = new ManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(LocalDate.of(2025, 1, 1));
        lr.setToDate(LocalDate.of(2025, 1, 10));
        Object result = invokePrivate(ctrl, M_CALC_OVERLAP_LEAVE,
                new Class[]{LeaveRequest.class, LocalDate.class, LocalDate.class},
                lr, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10));
        assertEquals(5, result);
    }

    @Test
    void testCalculateOverlapLeaveDays_noOverlap() throws Exception {
        ManagerController ctrl = new ManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(LocalDate.of(2025, 2, 1));
        lr.setToDate(LocalDate.of(2025, 2, 5));
        Object result = invokePrivate(ctrl, M_CALC_OVERLAP_LEAVE,
                new Class[]{LeaveRequest.class, LocalDate.class, LocalDate.class},
                lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(0, result);
    }

    @Test
    void testCalculateOverlapLeaveDays_nullDates() throws Exception {
        ManagerController ctrl = new ManagerController();
        LeaveRequest lr = new LeaveRequest();
        lr.setStatus(STATUS_APPROVED_VAL);
        lr.setFromDate(null);
        lr.setToDate(null);
        Object result = invokePrivate(ctrl, M_CALC_OVERLAP_LEAVE,
                new Class[]{LeaveRequest.class, LocalDate.class, LocalDate.class},
                lr, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(0, result);
    }
}
