package com.crm.demo.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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

import com.crm.demo.model.User;
import com.crm.demo.repository.*;
import com.crm.demo.service.*;

@ExtendWith(MockitoExtension.class)
class ControllerDelegationTest {

    @Mock private UserRepository userRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportAttachmentRepository reportAttachmentRepository;
    @Mock private PayrollTemplateRepository payrollTemplateRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PerformanceReviewRepository performanceReviewRepository;
    @Mock private com.crm.demo.repository.DomainCategoryRepository domainCategoryRepository;

    @Mock private AttendanceService attendanceService;
    @Mock private ProfileUpdateService profileUpdateService;
    @Mock private NotificationService notificationService;
    @Mock private PayslipService payslipService;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private AdminController adminController;
    @InjectMocks private EmployeeController employeeController;
    @InjectMocks private HrController hrController;
    @InjectMocks private ManagerController managerController;

    private User testUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("testuser");

        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test.tcs@crm.com");
        testUser.setRole("EMPLOYEE");
        testUser.setStatus("active");

        lenient().when(userRepository.findByUsername("testuser")).thenReturn(testUser);
    }

    @Test
    void testAdminDashboard() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();

        when(userRepository.findEmployeesByTenant("tcs")).thenReturn(Collections.emptyList());
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/dashboard").requestAttr("loggedInUser", "testuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"));
    }

    @Test
    void testAdminDashboardAnalytics() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();

        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/dashboard/analytics").requestAttr("loggedInUser", "testuser"))
                .andExpect(status().isOk());
    }

    @Test
    void testEmployeeDashboard() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();

        when(taskRepository.findByAssignedToAndTenantSegment("testuser", "tcs")).thenReturn(Collections.emptyList());
        when(teamRepository.findByMemberAndTenant(any(User.class), anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/employee/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-dashboard"));
    }

    @Test
    void testEmployeeDashboardAnalytics() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();

        when(taskRepository.findByAssignedToAndTenantSegment("testuser", "tcs")).thenReturn(Collections.emptyList());
        when(teamRepository.findByMemberAndTenant(any(User.class), anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/employee/dashboard/analytics"))
                .andExpect(status().isOk());
    }

    @Test
    void testHrDashboard() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(hrController).build();

        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/hr/dashboard").requestAttr("loggedInUser", "testuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("hr-dashboard"));
    }

    @Test
    void testHrDashboardAnalytics() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(hrController).build();

        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/hr/dashboard/analytics").requestAttr("loggedInUser", "testuser"))
                .andExpect(status().isOk());
    }

    @Test
    void testManagerDashboard() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(managerController).build();

        User managerUser = new User();
        managerUser.setUsername("testuser");
        managerUser.setEmail("mgr.tcs@crm.com");
        managerUser.setRole("MANAGER");
        when(userRepository.findByUsername("testuser")).thenReturn(managerUser);

        when(teamRepository.findByManagerWithMembers(any(User.class))).thenReturn(Collections.emptyList());
        when(taskRepository.findByCreatedByAndTenantSegment("testuser", "tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/manager/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager-dashboard"));
    }

    @Test
    void testManagerDashboardAnalytics() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(managerController).build();

        User managerUser = new User();
        managerUser.setUsername("testuser");
        managerUser.setEmail("mgr.tcs@crm.com");
        managerUser.setRole("MANAGER");
        when(userRepository.findByUsername("testuser")).thenReturn(managerUser);

        when(teamRepository.findByManagerWithMembers(any(User.class))).thenReturn(Collections.emptyList());
        when(taskRepository.findByCreatedByAndTenantSegment("testuser", "tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/manager/dashboard/analytics"))
                .andExpect(status().isOk());
    }
}
