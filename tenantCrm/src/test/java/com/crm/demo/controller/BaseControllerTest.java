package com.crm.demo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.AttendanceDay;
import com.crm.demo.model.Holiday;
import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class BaseControllerTest {

    static class ConcreteBaseController extends BaseController {
        ConcreteBaseController(UserRepository userRepository,
                               HolidayRepository holidayRepository,
                               LeaveRequestRepository leaveRequestRepository) {
            super(userRepository, holidayRepository, leaveRequestRepository);
        }
    }

    @InjectMocks
    private ConcreteBaseController controller;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testGetCurrentUser_Unauthenticated() {
        when(securityContext.getAuthentication()).thenReturn(null);
        assertNull(controller.getCurrentUser());

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        assertNull(controller.getCurrentUser());
    }

    @Test
    void testGetCurrentUser_Authenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("username");
        User user = new User();
        when(userRepository.findByUsername("username")).thenReturn(user);

        assertEquals(user, controller.getCurrentUser());
    }

    @Test
    void testGetTenantSegment() {
        assertEquals("", controller.getTenantSegment(null));
        
        User user = new User();
        assertEquals("", controller.getTenantSegment(user));

        user.setEmail("test@domain.com");
        assertEquals("com", controller.getTenantSegment(user));

        user.setEmail("invalid-email");
        assertEquals("", controller.getTenantSegment(user));

        user.setEmail("user.tenant@crm.com");
        assertEquals("tenant", controller.getTenantSegment(user));
    }

    @Test
    void testHasApprovedLeave() {
        assertFalse(controller.hasApprovedLeave(null, LocalDate.now()));
        assertFalse(controller.hasApprovedLeave(new User(), null));

        User user = new User();
        LocalDate date = LocalDate.of(2026, 7, 15);

        LeaveRequest leave = new LeaveRequest();
        leave.setStatus("Approved");
        leave.setFromDate(LocalDate.of(2026, 7, 10));
        leave.setToDate(LocalDate.of(2026, 7, 20));

        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)).thenReturn(List.of(leave));

        assertTrue(controller.hasApprovedLeave(user, date));
        assertFalse(controller.hasApprovedLeave(user, LocalDate.of(2026, 7, 9)));
        assertFalse(controller.hasApprovedLeave(user, LocalDate.of(2026, 7, 21)));

        leave.setStatus("Pending");
        assertFalse(controller.hasApprovedLeave(user, date));
    }

    @Test
    void testFetchHolidays() {
        Map<LocalDate, String> result = controller.fetchHolidays(null, LocalDate.now(), LocalDate.now());
        assertTrue(result.isEmpty());

        result = controller.fetchHolidays("", LocalDate.now(), LocalDate.now());
        assertTrue(result.isEmpty());

        Holiday h = new Holiday();
        h.setDate("2026-07-15");
        h.setName("New Year");
        when(holidayRepository.findByTenantAndDateRange("tcs", "2026-07-01", "2026-07-31")).thenReturn(List.of(h));

        result = controller.fetchHolidays("tcs", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertEquals(1, result.size());
        assertEquals("New Year", result.get(LocalDate.of(2026, 7, 15)));

        Holiday h2 = new Holiday();
        h2.setDate("2026-07-16");
        h2.setName(null);
        when(holidayRepository.findByTenantAndDateRange("tcs", "2026-07-01", "2026-07-31")).thenReturn(List.of(h2));
        result = controller.fetchHolidays("tcs", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertEquals("Holiday", result.get(LocalDate.of(2026, 7, 16)));
    }

    @Test
    void testResolveDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate[] range = controller.resolveDateRange(null, null);
        assertEquals(today.minusDays(29), range[0]);
        assertEquals(today, range[1]);

        range = controller.resolveDateRange("", "");
        assertEquals(today.minusDays(29), range[0]);
        assertEquals(today, range[1]);

        range = controller.resolveDateRange("2026-07-01", "2026-07-10");
        assertEquals(LocalDate.of(2026, 7, 1), range[0]);
        assertEquals(LocalDate.of(2026, 7, 10), range[1]);

        // filterTo is after today
        range = controller.resolveDateRange("2026-07-01", today.plusDays(5).toString());
        assertEquals(LocalDate.of(2026, 7, 1), range[0]);
        assertEquals(today, range[1]);

        // filterFrom after filterTo
        range = controller.resolveDateRange("2026-07-15", "2026-07-10");
        assertEquals(LocalDate.of(2026, 7, 10), range[0]);
        assertEquals(LocalDate.of(2026, 7, 10), range[1]);
    }

    @Test
    void testBuildAnalyticsMap() {
        Map<String, Object> data = controller.buildAnalyticsMap(null, null, false, false);
        assertEquals(0L, data.get("statusDone"));
        assertEquals(0L, data.get("unverified"));

        Task t = new Task();
        t.setStatus("done");
        t.setPriority("High");
        t.setCreatedBy("creator");
        t.setAssignedTo("employee");
        t.setVerificationStatus("approved");

        User u = new User();
        u.setUsername("employee");
        u.setStatus("active");

        data = controller.buildAnalyticsMap(List.of(t), List.of(u), false, false);
        assertEquals(1L, data.get("statusDone"));
        assertEquals(1, ((List<?>) data.get("memberLabels")).size());
        assertEquals("employee", ((List<?>) data.get("memberLabels")).get(0));

        // countByCreator = true
        data = controller.buildAnalyticsMap(List.of(t), List.of(u), true, false);
        assertEquals(1, ((List<?>) data.get("memberLabels")).size());
        assertEquals("creator", ((List<?>) data.get("memberLabels")).get(0));

        // excludeHrManager = true
        User hr = new User();
        hr.setUsername("hrUser");
        hr.setRole("HR");
        data = controller.buildAnalyticsMap(List.of(t), List.of(hr), false, true);
        assertEquals(0, ((List<?>) data.get("memberLabels")).size());
    }

    @Test
    void testPopulateAnalyticsAttributes() {
        Model model = new ConcurrentModel();
        Map<String, Object> data = new HashMap<>();
        data.put("statusDone", 1L);
        controller.populateAnalyticsAttributes(model, data);
        assertEquals(1L, model.getAttribute("chartStatusDone"));
    }

    @Test
    void testValidateUsername() {
        assertNotNull(controller.validateUsername(null));
        assertNotNull(controller.validateUsername(""));
        assertNotNull(controller.validateUsername("ab"));
        assertNull(controller.validateUsername("validUser"));
    }

    @Test
    void testValidateEmail() {
        assertNotNull(controller.validateEmail(null, "tcs", ".crm.com"));
        assertNotNull(controller.validateEmail("", "tcs", ".crm.com"));
        assertNotNull(controller.validateEmail("invalid-email", "tcs", ".crm.com"));
        assertNotNull(controller.validateEmail("test.other@crm.com", "tcs", ".crm.com"));
        assertNull(controller.validateEmail("test.tcs@crm.com", "tcs", ".crm.com"));
    }

    @Test
    void testValidatePassword() {
        assertNotNull(controller.validatePassword(null, "pass"));
        assertNotNull(controller.validatePassword("pas", "pas"));
        assertNotNull(controller.validatePassword("pass!", "pass!"));
        assertNotNull(controller.validatePassword("password", "confirm"));
        assertNull(controller.validatePassword("password", "password"));
    }

    @Test
    void testBuildDayList() {
        Attendance a = new Attendance();
        a.setDate(LocalDate.of(2026, 7, 15));
        a.setStatus("present");

        Map<LocalDate, String> holidays = new HashMap<>();
        holidays.put(LocalDate.of(2026, 7, 14), "Holiday");

        List<AttendanceDay> days = controller.buildDayList(List.of(a), LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 15), holidays);
        assertEquals(3, days.size());
    }

    @Test
    void testBuildDayList_AllBranches() {
        User user = new User();
        user.setId(1L);

        LocalDate sat = LocalDate.of(2026, 7, 18);
        LocalDate sun = LocalDate.of(2026, 7, 19);

        LeaveRequest leave = new LeaveRequest();
        leave.setStatus("Approved");
        leave.setFromDate(sat);
        leave.setToDate(sun);
        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)).thenReturn(List.of(leave));

        List<AttendanceDay> days = controller.buildDayList(null, sat, sun, null, user);
        assertEquals(2, days.size());
        assertEquals("weekend", days.get(0).getStatus());
        assertEquals("weekend", days.get(1).getStatus());

        LocalDate mon = LocalDate.of(2026, 7, 20);
        LeaveRequest leaveMon = new LeaveRequest();
        leaveMon.setStatus("Approved");
        leaveMon.setFromDate(mon);
        leaveMon.setToDate(mon);
        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)).thenReturn(List.of(leaveMon));

        List<AttendanceDay> daysMon = controller.buildDayList(null, mon, mon, null, user);
        assertEquals(1, daysMon.size());
        assertEquals("leave", daysMon.get(0).getStatus());

        LeaveRequest pendingLeave = new LeaveRequest();
        pendingLeave.setStatus("Pending");
        pendingLeave.setFromDate(mon);
        pendingLeave.setToDate(mon);
        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)).thenReturn(List.of(pendingLeave));

        List<AttendanceDay> daysPending = controller.buildDayList(null, mon, mon, null, user);
        assertEquals(0, daysPending.size());
    }

    @Test
    void testIsNonAdminRole() {
        assertFalse(controller.isNonAdminRole("ADMIN"));
        assertFalse(controller.isNonAdminRole("SUPER_ADMIN"));
        assertTrue(controller.isNonAdminRole("EMPLOYEE"));
        assertTrue(controller.isNonAdminRole("HR"));
        assertTrue(controller.isNonAdminRole("MANAGER"));
        assertFalse(controller.isNonAdminRole(null));
    }
}
