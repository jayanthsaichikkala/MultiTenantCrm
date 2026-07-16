package com.crm.demo.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.security.JwtUtil;
import com.crm.demo.security.SessionManager;

@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private BCryptPasswordEncoder passwordEncoder;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SessionManager sessionManager;

    private User employeeUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        employeeUser = new User();
        employeeUser.setId(1L);
        employeeUser.setUsername("empUser");
        employeeUser.setEmail("emp.tcs@crm.com");
        employeeUser.setPassword("hashedPassword");
        employeeUser.setRole("EMPLOYEE");
        employeeUser.setStatus("active");

        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("adminUser");
        adminUser.setEmail("admin.tcs@crm.com");
        adminUser.setPassword("hashedPassword");
        adminUser.setRole("ADMIN");
        adminUser.setStatus("active");
    }

    @Test
    @DisplayName("Should serve the login page view name")
    void shouldServeLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when username or password parameter is missing")
    void shouldReturnBadRequestWhenCredentialsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Username and password are required.")));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when user is not found in repository")
    void shouldReturnUnauthorizedWhenUserNotFound() throws Exception {
        when(userRepository.findByUsernameOrEmail("nonexistent", "nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nonexistent\",\"password\":\"wrongPass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid username or password.")));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when credentials match but user status is inactive")
    void shouldReturnForbiddenWhenUserIsInactive() throws Exception {
        employeeUser.setStatus("inactive");
        when(userRepository.findByUsernameOrEmail("empUser", "empUser")).thenReturn(Optional.of(employeeUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"empUser\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Your account is inactive. Please contact your administrator.")));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when tenant admin is inactive")
    void shouldReturnForbiddenWhenTenantAdminIsInactive() throws Exception {
        when(userRepository.findByUsernameOrEmail("empUser", "empUser")).thenReturn(Optional.of(employeeUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);

        // Mock tenant admin search returning inactive admin user
        User inactiveAdmin = new User();
        inactiveAdmin.setRole("ADMIN");
        inactiveAdmin.setEmail("admin.tcs@crm.com");
        inactiveAdmin.setStatus("inactive");

        when(userRepository.findAll()).thenReturn(List.of(inactiveAdmin));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"empUser\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Access is currently disabled. Please contact your administrator.")));
    }

    @Test
    @DisplayName("Should return 409 Conflict when user session is active on another device and force is false")
    void shouldReturnConflictWhenUserSessionActive() throws Exception {
        when(userRepository.findByUsernameOrEmail("empUser", "empUser")).thenReturn(Optional.of(employeeUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(sessionManager.isUserLoggedIn("empUser")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"empUser\",\"password\":\"password123\",\"force\":\"false\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.alreadyLoggedIn", is(true)))
                .andExpect(jsonPath("$.error", is("Your account is already active on another device. Do you want to continue and sign out from the previous session?")));
    }

    @Test
    @DisplayName("Should invalidate old session and authenticate successfully when force is true")
    void shouldAuthenticateSuccessfullyWithForce() throws Exception {
        when(userRepository.findByUsernameOrEmail("empUser", "empUser")).thenReturn(Optional.of(employeeUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtUtil.generateToken("empUser", "EMPLOYEE")).thenReturn("jwtToken123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"empUser\",\"password\":\"password123\",\"force\":\"true\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwtToken123")))
                .andExpect(jsonPath("$.username", is("empUser")))
                .andExpect(jsonPath("$.role", is("EMPLOYEE")))
                .andExpect(jsonPath("$.redirect", is("/employee/dashboard")));

        verify(sessionManager).invalidateSession("empUser");
        verify(sessionManager).registerSession("empUser", "jwtToken123");
    }

    @Test
    @DisplayName("Should authenticate and redirect to manager dashboard successfully")
    void shouldAuthenticateSuccessfullyForManager() throws Exception {
        employeeUser.setRole("MANAGER");
        when(userRepository.findByUsernameOrEmail("empUser", "empUser")).thenReturn(Optional.of(employeeUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtUtil.generateToken("empUser", "MANAGER")).thenReturn("jwtTokenManager");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"empUser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect", is("/manager/dashboard")));
    }

    @Test
    @DisplayName("Should logout via API call and invalidate active token")
    void shouldLogoutViaApiSuccessfully() throws Exception {
        when(jwtUtil.isValid("validToken")).thenReturn(true);
        when(jwtUtil.extractUsername("validToken")).thenReturn("empUser");

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer validToken"))
                .andExpect(status().isOk());

        verify(sessionManager).invalidateSession("empUser");
    }

    @Test
    @DisplayName("Should do nothing on API logout when token is invalid or missing")
    void shouldDoNothingOnApiLogoutWhenTokenInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());

        verify(sessionManager, never()).invalidateSession(anyString());
    }

    @Test
    @DisplayName("Should clear security context, session, cookie and redirect to login on standard logout")
    void shouldLogoutAndClearCookieSuccessfully() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("empUser");
        SecurityContextHolder.setContext(securityContext);

        mockMvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(cookie().maxAge("jwt_token", 0))
                .andExpect(cookie().path("jwt_token", "/"));

        verify(sessionManager).invalidateSession("empUser");
    }

    @Test
    @DisplayName("Should redirect to login on legacy dashboard links when user is unauthenticated")
    void shouldRedirectToLoginWhenLegacyUserUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Should redirect to login on legacy dashboard links when user is not found in repository")
    void shouldRedirectToLoginWhenLegacyUserNotFound() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("unknown");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("unknown")).thenReturn(null);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Should redirect to role specific dashboard on handleLegacyRedirects")
    void shouldRedirectToRoleDashboardOnLegacyRedirect() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("empUser");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("empUser")).thenReturn(employeeUser);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/dashboard"));
    }

    @Test
    @DisplayName("Should redirect legacy tasks to manager tasks page")
    void shouldRedirectLegacyTasksForManager() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("empUser");
        SecurityContextHolder.setContext(securityContext);

        employeeUser.setRole("MANAGER");
        when(userRepository.findByUsername("empUser")).thenReturn(employeeUser);

        mockMvc.perform(get("/tasks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manager/tasks"));
    }

    @Test
    @DisplayName("Should redirect legacy meetings to admin schedule page for ADMIN")
    void shouldRedirectLegacyMeetingsForAdmin() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("adminUser");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("adminUser")).thenReturn(adminUser);

        mockMvc.perform(get("/meetings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedule-meeting"));
    }

    @Test
    @DisplayName("Should redirect legacy teams links for HR and Manager roles")
    void shouldRedirectLegacyTeamsForHrAndManager() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("empUser");
        SecurityContextHolder.setContext(securityContext);

        // HR Case
        employeeUser.setRole("HR");
        when(userRepository.findByUsername("empUser")).thenReturn(employeeUser);
        mockMvc.perform(get("/teams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/teams"));

        // Manager Case
        employeeUser.setRole("MANAGER");
        mockMvc.perform(get("/teams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manager/team"));
    }
}
