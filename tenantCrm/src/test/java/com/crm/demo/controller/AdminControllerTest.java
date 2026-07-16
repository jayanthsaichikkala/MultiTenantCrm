package com.crm.demo.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.crm.demo.model.*;
import com.crm.demo.repository.*;
import com.crm.demo.service.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportAttachmentRepository reportAttachmentRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private ProfileUpdateService profileUpdateService;
    @Mock private NotificationService notificationService;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private com.crm.demo.repository.DomainCategoryRepository domainCategoryRepository;
    @Mock private AttendanceService attendanceService;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private AdminController adminController;

    private User adminUser;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");
        mockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setViewResolvers(viewResolver)
                .build();
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("adminuser");

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("adminuser");
        adminUser.setEmail("admin.tcs@crm.com");
        adminUser.setRole("ADMIN");
        adminUser.setStatus("active");

        lenient().when(userRepository.findByUsername("adminuser")).thenReturn(adminUser);
    }

    @Test
    void testDashboard() throws Exception {
        when(userRepository.findEmployeesByTenant("tcs")).thenReturn(Collections.emptyList());
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/dashboard").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"));
    }

    @Test
    void testDashboardAnalytics() throws Exception {
        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/dashboard/analytics").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk());
    }

    @Test
    void testListEmployees() throws Exception {
        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/admin/employees").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("add-users"));
    }

    @Test
    void testEmployeeDetail_Success() throws Exception {
        User emp = new User();
        emp.setId(2L);
        emp.setUsername("emp1");
        emp.setEmail("emp1.tcs@crm.com");
        emp.setRole("EMPLOYEE");
        emp.setStatus("active");

        when(userRepository.findById(2L)).thenReturn(Optional.of(emp));

        mockMvc.perform(get("/admin/api/employee/2").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("emp1"));
    }

    @Test
    void testEmployeeDetail_NotFound() throws Exception {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/api/employee/2").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("User not found."));
    }

    @Test
    void testAddEmployeePage() throws Exception {
        when(domainCategoryRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/add-employee").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-add-employee"));
    }

    @Test
    void testAddUserRedirect() throws Exception {
        mockMvc.perform(get("/admin/add-user"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));
    }

    @Test
    void testAddUserPost_Success() throws Exception {
        when(userRepository.findByEmail("newemp.tcs@crm.com")).thenReturn(null);
        when(userRepository.findByUsername("newemp")).thenReturn(null);
        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(passwordEncoder.encode("pass123")).thenReturn("hashedpwd");

        mockMvc.perform(post("/admin/add-user")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("username", "newemp")
                        .param("email", "newemp.tcs@crm.com")
                        .param("password", "pass123")
                        .param("confirmPassword", "pass123")
                        .param("role", "EMPLOYEE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void testAddUserPost_ValidationFail() throws Exception {
        mockMvc.perform(post("/admin/add-user")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("username", "")
                        .param("email", "newemp.tcs@crm.com")
                        .param("password", "pass123")
                        .param("confirmPassword", "pass123")
                        .param("role", "EMPLOYEE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/add-employee"));
    }

    @Test
    void testAddUserPost_DuplicateEmail() throws Exception {
        User existing = new User();
        existing.setEmail("newemp.tcs@crm.com");

        when(userRepository.findByEmail("newemp.tcs@crm.com")).thenReturn(existing);

        mockMvc.perform(post("/admin/add-user")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("username", "newemp")
                        .param("email", "newemp.tcs@crm.com")
                        .param("password", "pass123")
                        .param("confirmPassword", "pass123")
                        .param("role", "EMPLOYEE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/add-employee"));
    }

    @Test
    void testBulkUpload_EmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", "application/vnd.ms-excel", new byte[0]);

        mockMvc.perform(multipart("/admin/bulk-upload").file(file).requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/add-employee"));
    }

    @Test
    void testBulkUpload_InvalidExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/admin/bulk-upload").file(file).requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/add-employee"));
    }

    @Test
    void testToggleUser() throws Exception {
        User emp = new User();
        emp.setId(2L);
        emp.setUsername("emp1");
        emp.setRole("EMPLOYEE");
        emp.setStatus("active");

        when(userRepository.findById(2L)).thenReturn(Optional.of(emp));

        mockMvc.perform(post("/admin/toggle-user/2").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));

        verify(userRepository).save(emp);
    }

    @Test
    void testDeleteEmployee() throws Exception {
        User emp = new User();
        emp.setId(2L);
        emp.setUsername("emp1");
        emp.setRole("EMPLOYEE");

        when(userRepository.findById(2L)).thenReturn(Optional.of(emp));

        mockMvc.perform(post("/admin/delete-employee/2").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));

        verify(userRepository).delete(emp);
    }

    @Test
    void testEditEmployeeGet() throws Exception {
        User emp = new User();
        emp.setId(2L);
        emp.setEmail("emp.tcs@crm.com");
        emp.setRole("EMPLOYEE");

        when(userRepository.findById(2L)).thenReturn(Optional.of(emp));
        when(domainCategoryRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/edit-employee/2").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-employee"));
    }

    @Test
    void testUpdateEmployeePost_Success() throws Exception {
        User emp = new User();
        emp.setId(2L);
        emp.setUsername("emp2");
        emp.setEmail("emp2.tcs@crm.com");
        emp.setRole("EMPLOYEE");

        when(userRepository.findById(2L)).thenReturn(Optional.of(emp));
        when(userRepository.findByUsername("emp2")).thenReturn(null);
        when(userRepository.findByEmail("emp2.tcs@crm.com")).thenReturn(null);

        mockMvc.perform(post("/admin/edit-employee/2")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("username", "emp2")
                        .param("email", "emp2.tcs@crm.com")
                        .param("role", "EMPLOYEE")
                        .param("status", "active"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));
    }

    @Test
    void testTasksPage() throws Exception {
        when(taskRepository.findByTenantSegment("tcs")).thenReturn(new ArrayList<>());
        when(teamRepository.findByTenantSegmentOrderByIdDesc("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/tasks").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-tasks"));
    }

    @Test
    void testReportsPage() throws Exception {
        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/reports").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-reports"));
    }

    @Test
    void testViewReportAttachment_Success() throws Exception {
        ReportAttachment att = new ReportAttachment();
        att.setId(5L);
        att.setOriginalFilename("test.pdf");
        att.setContentType("application/pdf");
        att.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(5L)).thenReturn(Optional.of(att));

        mockMvc.perform(get("/admin/reports/view/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"test.pdf\""))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void testViewReportAttachment_NotFound() throws Exception {
        when(reportAttachmentRepository.findById(5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/reports/view/5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadReportAttachment_Success() throws Exception {
        ReportAttachment att = new ReportAttachment();
        att.setId(5L);
        att.setOriginalFilename("test.pdf");
        att.setContentType("application/pdf");
        att.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(5L)).thenReturn(Optional.of(att));

        mockMvc.perform(get("/admin/reports/download/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.pdf\""));
    }

    @Test
    void testCalendarPage() throws Exception {
        mockMvc.perform(get("/admin/calendar").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"));
    }

    @Test
    void testProfilePage() throws Exception {
        when(userRepository.findByUsername("adminuser")).thenReturn(adminUser);

        mockMvc.perform(get("/admin/profile").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-profile"));
    }

    @Test
    void testUpdateProfile_Success() throws Exception {
        when(profileUpdateService.updateProfile(eq(adminUser), any(), any(), any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/admin/update-profile")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("username", "adminuser")
                        .param("email", "admin.tcs@crm.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/profile?success"));
    }

    @Test
    void testScheduleMeetingGet() throws Exception {
        when(meetingRepository.findUpcomingMeetingsForUserOrHost("tcs", "adminuser", LocalDate.now())).thenReturn(Collections.emptyList());
        when(meetingRepository.findAllMeetingsForUserOrHost("tcs", "adminuser")).thenReturn(Collections.emptyList());
        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/schedule-meeting").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-scheduleMeeting"));
    }

    @Test
    void testScheduleMeetingPost_Success() throws Exception {
        mockMvc.perform(post("/admin/schedule-meeting")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("title", "Project Review")
                        .param("meetingDate", "2026-07-20")
                        .param("meetingTime", "10:00")
                        .param("duration", "60")
                        .param("meetingType", "online")
                        .param("participants", "emp1,emp2")
                        .param("agenda", "Reviewing progress"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedule-meeting"));

        verify(meetingRepository).save(any(Meeting.class));
    }

    @Test
    void testEditMeetingGet() throws Exception {
        Meeting meeting = new Meeting();
        meeting.setId(10L);
        meeting.setTenantSegment("tcs");
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));
        when(userRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/schedule-meeting/edit/10").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-scheduleMeeting"));
    }

    @Test
    void testEditMeetingPost_Success() throws Exception {
        Meeting meeting = new Meeting();
        meeting.setId(10L);
        meeting.setTenantSegment("tcs");
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        mockMvc.perform(post("/admin/schedule-meeting/edit/10")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("title", "Project Review")
                        .param("meetingDate", "2026-07-20")
                        .param("meetingTime", "10:00")
                        .param("duration", "60")
                        .param("meetingType", "online")
                        .param("participants", "emp1,emp2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedule-meeting"));
    }

    @Test
    void testDeleteMeeting() throws Exception {
        Meeting meeting = new Meeting();
        meeting.setId(10L);
        meeting.setTenantSegment("tcs");
        when(meetingRepository.findById(10L)).thenReturn(Optional.of(meeting));

        mockMvc.perform(post("/admin/schedule-meeting/delete/10").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedule-meeting"));

        verify(meetingRepository).delete(meeting);
    }

    @Test
    void testSettingsPage() throws Exception {
        when(domainCategoryRepository.findByTenantSegment("tcs")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/settings").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-settings"));
    }

    @Test
    void testAddDomainCategory_Success() throws Exception {
        when(domainCategoryRepository.existsByNameAndTenantSegment("Finance", "tcs")).thenReturn(false);

        mockMvc.perform(post("/admin/settings/domain-categories")
                        .requestAttr("loggedInUser", "adminuser")
                        .param("name", "Finance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings?success"));

        verify(domainCategoryRepository).save(any(DomainCategory.class));
    }

    @Test
    void testDeleteDomainCategory_Success() throws Exception {
        DomainCategory cat = new DomainCategory();
        cat.setId(5L);
        cat.setTenantSegment("tcs");

        when(domainCategoryRepository.findById(5L)).thenReturn(Optional.of(cat));

        mockMvc.perform(post("/admin/settings/domain-categories/delete/5").requestAttr("loggedInUser", "adminuser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings?success=delete"));

        verify(domainCategoryRepository).delete(cat);
    }
}
