package com.crm.demo.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.crm.demo.model.Holiday;
import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.HolidayService;
import com.crm.demo.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class HolidayControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HolidayService holidayService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private HolidayController holidayController;

    private User testUser;

    @BeforeEach
    void setUp() {
        org.springframework.web.servlet.view.InternalResourceViewResolver viewResolver = 
                new org.springframework.web.servlet.view.InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(holidayController)
                .setViewResolvers(viewResolver)
                .build();

        testUser = new User();
        testUser.setId(10L);
        testUser.setUsername("testadmin");
        testUser.setEmail("admin.tcs@crm.com");
    }

    // =========================================================================
    // GET /api/holidays (list)
    // =========================================================================

    @Test
    @DisplayName("Should list holidays for caller's segment (with dot segment)")
    void shouldListHolidaysForTenantWithDotSegment() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        mockMvc.perform(get("/api/holidays")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk());

        verify(userRepository).findByUsername("testadmin");
        verify(holidayService).getByTenant("tcs");
    }

    @Test
    @DisplayName("Should list holidays for caller's segment (without dot segment)")
    void shouldListHolidaysForTenantWithoutDotSegment() throws Exception {
        testUser.setEmail("admin@crm.com");
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        mockMvc.perform(get("/api/holidays")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk());

        verify(userRepository).findByUsername("testadmin");
        verify(holidayService).getByTenant("admin");
    }

    @Test
    @DisplayName("Should list holidays for default tenant when username is null")
    void shouldListHolidaysForDefaultTenantWhenUsernameIsNull() throws Exception {
        mockMvc.perform(get("/api/holidays"))
                .andExpect(status().isOk());

        verify(holidayService).getByTenant("default");
    }

    @Test
    @DisplayName("Should list holidays for default tenant when user is not found")
    void shouldListHolidaysForDefaultTenantWhenUserNotFound() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(null);

        mockMvc.perform(get("/api/holidays")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk());

        verify(holidayService).getByTenant("default");
    }

    @Test
    @DisplayName("Should list holidays for default tenant when user email is null")
    void shouldListHolidaysForDefaultTenantWhenEmailIsNull() throws Exception {
        testUser.setEmail(null);
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        mockMvc.perform(get("/api/holidays")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk());

        verify(holidayService).getByTenant("default");
    }

    // =========================================================================
    // POST /api/holidays (add)
    // =========================================================================

    @Test
    @DisplayName("Should return validation error when date is empty or invalid format")
    void shouldFailToAddHolidayWhenDateIsInvalid() throws Exception {
        mockMvc.perform(post("/api/holidays")
                .param("date", "2026/07/15") // Invalid format
                .param("name", "New Year")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Please select a valid holiday date.")));
    }

    @Test
    @DisplayName("Should return validation error when name is empty or blank")
    void shouldFailToAddHolidayWhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/holidays")
                .param("date", "2026-07-15")
                .param("name", "   ")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday name is required.")));
    }

    @Test
    @DisplayName("Should return validation error when name exceeds 255 characters")
    void shouldFailToAddHolidayWhenNameTooLong() throws Exception {
        String longName = String.join("", Collections.nCopies(256, "a"));
        mockMvc.perform(post("/api/holidays")
                .param("date", "2026-07-15")
                .param("name", longName)
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday name cannot exceed 255 characters.")));
    }

    @Test
    @DisplayName("Should return validation error when type is empty or blank")
    void shouldFailToAddHolidayWhenTypeIsBlank() throws Exception {
        mockMvc.perform(post("/api/holidays")
                .param("date", "2026-07-15")
                .param("name", "New Year")
                .param("type", "")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday type is required.")));
    }

    @Test
    @DisplayName("Should fail when holiday already exists on date for tenant")
    void shouldFailToAddHolidayWhenDateAlreadyExists() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);
        when(holidayService.dateExists("2026-07-15", "tcs")).thenReturn(true);

        mockMvc.perform(post("/api/holidays")
                .param("date", "2026-07-15")
                .param("name", "New Year")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("A holiday already exists on 2026-07-15 for your company.")));
    }

    @Test
    @DisplayName("Should add holiday successfully and trigger notification")
    void shouldAddHolidaySuccessfully() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);
        when(holidayService.dateExists("2026-07-15", "tcs")).thenReturn(false);

        mockMvc.perform(post("/api/holidays")
                .param("date", "2026-07-15")
                .param("name", "  New Year  ")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Holiday added.")))
                .andExpect(jsonPath("$.holiday.name", is("New Year")));

        verify(holidayService).save(any(Holiday.class));
        verify(notificationService).notifyHolidayAdded("tcs", "New Year", "2026-07-15");
    }

    // =========================================================================
    // PUT /api/holidays/{id} (update)
    // =========================================================================

    @Test
    @DisplayName("Should return validation error when updating with invalid parameters")
    void shouldFailToUpdateHolidayWhenParametersAreInvalid() throws Exception {
        mockMvc.perform(put("/api/holidays/1")
                .param("date", "")
                .param("name", "New Year")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Please select a valid holiday date.")));
    }

    @Test
    @DisplayName("Should return holiday not found when id does not exist")
    void shouldFailToUpdateHolidayWhenIdNotFound() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);
        when(holidayService.getById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/holidays/99")
                .param("date", "2026-07-15")
                .param("name", "New Year")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday not found.")));
    }

    @Test
    @DisplayName("Should return holiday not found when holiday belongs to different tenant")
    void shouldFailToUpdateHolidayWhenBelongsToDifferentTenant() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setTenantSegment("different-tenant");
        when(holidayService.getById(1L)).thenReturn(Optional.of(holiday));

        mockMvc.perform(put("/api/holidays/1")
                .param("date", "2026-07-15")
                .param("name", "New Year")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday not found.")));
    }

    @Test
    @DisplayName("Should fail when another holiday exists on the target date")
    void shouldFailToUpdateHolidayWhenTargetDateAlreadyExists() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setTenantSegment("tcs");
        when(holidayService.getById(1L)).thenReturn(Optional.of(holiday));
        when(holidayService.dateExistsExcluding("2026-07-15", "tcs", 1L)).thenReturn(true);

        mockMvc.perform(put("/api/holidays/1")
                .param("date", "2026-07-15")
                .param("name", "New Year")
                .param("type", "public")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Another holiday already exists on 2026-07-15 for your company.")));
    }

    @Test
    @DisplayName("Should update holiday successfully and trigger live update")
    void shouldUpdateHolidaySuccessfully() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setTenantSegment("tcs");
        holiday.setName("Old Name");
        when(holidayService.getById(1L)).thenReturn(Optional.of(holiday));
        when(holidayService.dateExistsExcluding("2026-07-15", "tcs", 1L)).thenReturn(false);

        mockMvc.perform(put("/api/holidays/1")
                .param("date", "2026-07-15")
                .param("name", "  Updated Name  ")
                .param("type", "optional")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Holiday updated.")))
                .andExpect(jsonPath("$.holiday.name", is("Updated Name")));

        verify(holidayService).save(holiday);
        verify(notificationService).sendLiveUpdateToTenant("tcs", "HOLIDAY", "Holiday Updated", "Holiday was updated");
    }

    // =========================================================================
    // DELETE /api/holidays/{id}
    // =========================================================================

    @Test
    @DisplayName("Should return holiday not found when deleting nonexistent holiday")
    void shouldFailToDeleteHolidayWhenIdNotFound() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);
        when(holidayService.getById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/holidays/99")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday not found.")));
    }

    @Test
    @DisplayName("Should return holiday not found when deleting holiday of another tenant")
    void shouldFailToDeleteHolidayWhenBelongsToDifferentTenant() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setTenantSegment("different-tenant");
        when(holidayService.getById(1L)).thenReturn(Optional.of(holiday));

        mockMvc.perform(delete("/api/holidays/1")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Holiday not found.")));
    }

    @Test
    @DisplayName("Should delete holiday successfully and notify tenant")
    void shouldDeleteHolidaySuccessfully() throws Exception {
        when(userRepository.findByUsername("testadmin")).thenReturn(testUser);

        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setTenantSegment("tcs");
        when(holidayService.getById(1L)).thenReturn(Optional.of(holiday));

        mockMvc.perform(delete("/api/holidays/1")
                .requestAttr("loggedInUser", "testadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Holiday deleted.")));

        verify(holidayService).deleteById(1L);
        verify(notificationService).sendLiveUpdateToTenant("tcs", "HOLIDAY", "Holiday Deleted", "Holiday was deleted");
    }
}
