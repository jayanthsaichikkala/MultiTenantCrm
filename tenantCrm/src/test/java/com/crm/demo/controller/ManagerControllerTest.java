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

import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.*;
import com.crm.demo.service.*;

@ExtendWith(MockitoExtension.class)
class ManagerControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private TaskAttachmentRepository taskAttachmentRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportAttachmentRepository reportAttachmentRepository;
    @Mock private PerformanceReviewRepository performanceReviewRepository;
    @Mock private PayrollTemplateRepository payrollTemplateRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private ProjectRepository projectRepository;

    @Mock private AttendanceService attendanceService;
    @Mock private ProfileUpdateService profileUpdateService;
    @Mock private NotificationService notificationService;
    @Mock private PayslipService payslipService;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private ManagerController managerController;

    private MockMvc mockMvc;
    private User managerUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(managerController).build();
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("mgruser");
        lenient().when(authentication.isAuthenticated()).thenReturn(true);

        managerUser = new User();
        managerUser.setUsername("mgruser");
        managerUser.setRole("MANAGER");
        managerUser.setEmail("mgr.tcs@crm.com");
        lenient().when(userRepository.findByUsername("mgruser")).thenReturn(managerUser);
    }

    @Test
    void testTasksPage() throws Exception {
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(teamRepository.findByManagerWithMembers(any(User.class))).thenReturn(Collections.emptyList());
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/manager/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    void testReviewTask_Reject() throws Exception {
        Task t = new Task();
        t.setId(10L);
        t.setStatus("pending");
        t.setTenantSegment("tcs");

        when(taskRepository.findById(10L)).thenReturn(Optional.of(t));

        mockMvc.perform(post("/manager/tasks/verify/10")
                .param("action", "reject")
                .param("reason", "Please redo it"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manager/tasks"));

        assertEquals("in-progress", t.getStatus());
        assertEquals("rejected", t.getVerificationStatus());
    }

    @Test
    void testReviewTask_Reopen() throws Exception {
        Task t = new Task();
        t.setId(10L);
        t.setStatus("done");
        t.setTenantSegment("tcs");

        when(taskRepository.findById(10L)).thenReturn(Optional.of(t));

        mockMvc.perform(post("/manager/tasks/verify/10")
                .param("action", "reopen"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manager/tasks"));

        assertEquals("in-progress", t.getStatus());
        assertEquals("pending", t.getVerificationStatus());
    }
}
