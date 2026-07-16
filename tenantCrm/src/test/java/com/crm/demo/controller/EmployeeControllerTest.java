package com.crm.demo.controller;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.*;
import com.crm.demo.service.*;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskAttachmentRepository taskAttachmentRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportAttachmentRepository reportAttachmentRepository;
    @Mock private PayrollTemplateRepository payrollTemplateRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;

    @Mock private ProfileUpdateService profileUpdateService;
    @Mock private NotificationService notificationService;
    @Mock private AttendanceService attendanceService;
    @Mock private PayslipService payslipService;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private EmployeeController employeeController;

    private MockMvc mockMvc;
    private User employeeUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("empuser");
        lenient().when(authentication.isAuthenticated()).thenReturn(true);

        employeeUser = new User();
        employeeUser.setUsername("empuser");
        employeeUser.setRole("EMPLOYEE");
        employeeUser.setEmail("emp.tcs@crm.com");
        lenient().when(userRepository.findByUsername("empuser")).thenReturn(employeeUser);
    }

    @Test
    void testTasksPage() throws Exception {
        Task t = new Task();
        t.setStatus("pending");
        when(taskRepository.findByAssignedToAndTenantSegment("empuser", "tcs")).thenReturn(Collections.singletonList(t));

        mockMvc.perform(get("/employee/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateTaskStatus() throws Exception {
        Task t = new Task();
        t.setId(10L);
        t.setStatus("pending");
        t.setTenantSegment("tcs");
        t.setAssignedTo("empuser");

        when(taskRepository.findById(10L)).thenReturn(Optional.of(t));

        mockMvc.perform(post("/employee/tasks/update-status/10")
                .param("status", "done")
                .param("action", "submit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/tasks?success"));

        assertEquals("done", t.getStatus());
        assertEquals("waiting-for-review", t.getVerificationStatus());
    }
}
