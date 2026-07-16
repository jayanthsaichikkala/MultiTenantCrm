package com.crm.demo.controller;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.crm.demo.model.User;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class HrControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private NotificationService notificationService;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private HrController hrController;

    private MockMvc mockMvc;
    private User hrUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(hrController).build();
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("hruser");

        hrUser = new User();
        hrUser.setUsername("hruser");
        hrUser.setRole("HR");
        hrUser.setEmail("hr.tcs@crm.com");
        lenient().when(userRepository.findByUsername("hruser")).thenReturn(hrUser);
    }

    @Test
    void testToggleUser() throws Exception {
        User emp = new User();
        emp.setId(2L);
        emp.setUsername("emp");
        emp.setRole("EMPLOYEE");
        emp.setStatus("active");

        when(userRepository.findById(2L)).thenReturn(Optional.of(emp));

        mockMvc.perform(post("/hr/toggle-user/2").requestAttr("loggedInUser", "hruser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/employees"));

        assertEquals("inactive", emp.getStatus());
        verify(userRepository, times(1)).save(emp);
    }

    @Test
    void testReviewLeave_Approve() throws Exception {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(5L);
        leave.setTenantSegment("tcs");
        leave.setStatus("Pending");

        when(leaveRequestRepository.findById(5L)).thenReturn(Optional.of(leave));

        mockMvc.perform(post("/hr/leaves/5/review")
                .requestAttr("loggedInUser", "hruser")
                .param("action", "approve"))
                .andExpect(status().is3xxRedirection());

        assertEquals("Approved", leave.getStatus());
    }

    @Test
    void testReviewLeave_Reject() throws Exception {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(5L);
        leave.setTenantSegment("tcs");
        leave.setStatus("Pending");

        when(leaveRequestRepository.findById(5L)).thenReturn(Optional.of(leave));

        mockMvc.perform(post("/hr/leaves/5/review")
                .requestAttr("loggedInUser", "hruser")
                .param("action", "reject")
                .param("rejectionMessage", "Not approved"))
                .andExpect(status().is3xxRedirection());

        assertEquals("rejected", leave.getStatus());
    }
}
