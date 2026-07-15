package com.crm.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import com.crm.demo.model.Notification;
import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationController notificationController;

    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
        SecurityContextHolder.setContext(securityContext);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole("EMPLOYEE");
    }

    private void mockAuthentication(String username) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(username);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when security context returns null user")
    void shouldReturn401WhenCurrentUserNotFound() throws Exception {
        mockAuthentication("unknown-user");
        when(userRepository.findByUsername("unknown-user")).thenReturn(null);

        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")));

        verify(userRepository).findByUsername("unknown-user");
    }

    @Test
    @DisplayName("Should return current user details when authenticated")
    void shouldReturnUserDetailsWhenAuthenticated() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);

        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.role", is("EMPLOYEE")));

        verify(userRepository).findByUsername(testUser.getUsername());
    }

    @Test
    @DisplayName("Should list notifications and return unread count when authenticated")
    void shouldListNotificationsWhenAuthenticated() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);

        Notification notification = new Notification();
        notification.setId(10L);
        notification.setTitle("Test Title");
        notification.setMessage("Test Message");
        notification.setType("ALERT");
        notification.setLink("/test-link");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.of(2026, 7, 15, 10, 0, 0));

        when(notificationService.getRecentForUser(testUser.getId())).thenReturn(List.of(notification));
        when(notificationService.getUnreadCount(testUser.getId())).thenReturn(5L);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(5)))
                .andExpect(jsonPath("$.notifications", hasSize(1)))
                .andExpect(jsonPath("$.notifications[0].id", is(10)))
                .andExpect(jsonPath("$.notifications[0].title", is("Test Title")))
                .andExpect(jsonPath("$.notifications[0].message", is("Test Message")))
                .andExpect(jsonPath("$.notifications[0].type", is("ALERT")))
                .andExpect(jsonPath("$.notifications[0].link", is("/test-link")))
                .andExpect(jsonPath("$.notifications[0].read", is(false)))
                .andExpect(jsonPath("$.notifications[0].createdAt", is("2026-07-15T10:00")));

        verify(notificationService).getRecentForUser(testUser.getId());
        verify(notificationService).getUnreadCount(testUser.getId());
    }

    @Test
    @DisplayName("Should list empty notifications and null createdAt when notification has no timestamp")
    void shouldListNotificationsWithNullTimestamp() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);

        Notification notification = new Notification();
        notification.setId(11L);
        notification.setCreatedAt(null); // Null timestamp

        when(notificationService.getRecentForUser(testUser.getId())).thenReturn(List.of(notification));
        when(notificationService.getUnreadCount(testUser.getId())).thenReturn(0L);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(0)))
                .andExpect(jsonPath("$.notifications", hasSize(1)))
                .andExpect(jsonPath("$.notifications[0].id", is(11)))
                .andExpect(jsonPath("$.notifications[0].createdAt").value(is(nullValue()))); // null check
    }

    // Helper matcher import alternative for nullValue()
    private static org.hamcrest.Matcher<Object> nullValue() {
        return org.hamcrest.CoreMatchers.nullValue();
    }

    @Test
    @DisplayName("Should return unread notifications count when authenticated")
    void shouldReturnUnreadCount() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);
        when(notificationService.getUnreadCount(testUser.getId())).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(3)));

        verify(notificationService).getUnreadCount(testUser.getId());
    }

    @Test
    @DisplayName("Should return 404 Not Found when marking a non-existent or invalid notification as read")
    void shouldReturnNotFoundWhenMarkingInvalidNotificationRead() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);
        when(notificationService.markAsRead(100L, testUser.getId())).thenReturn(false);

        mockMvc.perform(post("/api/notifications/100/read"))
                .andExpect(status().isNotFound());

        verify(notificationService).markAsRead(100L, testUser.getId());
        verify(notificationService, never()).getUnreadCount(anyLong());
    }

    @Test
    @DisplayName("Should successfully mark notification as read and return updated unread count")
    void shouldMarkNotificationReadSuccessfully() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);
        when(notificationService.markAsRead(10L, testUser.getId())).thenReturn(true);
        when(notificationService.getUnreadCount(testUser.getId())).thenReturn(2L);

        mockMvc.perform(post("/api/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(2)));

        verify(notificationService).markAsRead(10L, testUser.getId());
        verify(notificationService).getUnreadCount(testUser.getId());
    }

    @Test
    @DisplayName("Should mark all notifications as read successfully")
    void shouldMarkAllNotificationsReadSuccessfully() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(0)));

        verify(notificationService).markAllAsRead(testUser.getId());
    }

    @Test
    @DisplayName("Should return 404 Not Found when deleting a non-existent notification")
    void shouldReturnNotFoundWhenDeletingInvalidNotification() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);
        when(notificationService.deleteNotification(100L, testUser.getId())).thenReturn(false);

        mockMvc.perform(delete("/api/notifications/100"))
                .andExpect(status().isNotFound());

        verify(notificationService).deleteNotification(100L, testUser.getId());
    }

    @Test
    @DisplayName("Should successfully delete single notification and return updated status")
    void shouldDeleteSingleNotificationSuccessfully() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);
        when(notificationService.deleteNotification(10L, testUser.getId())).thenReturn(true);
        when(notificationService.getUnreadCount(testUser.getId())).thenReturn(1L);

        mockMvc.perform(delete("/api/notifications/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(1)))
                .andExpect(jsonPath("$.deleted", is(true)));

        verify(notificationService).deleteNotification(10L, testUser.getId());
        verify(notificationService).getUnreadCount(testUser.getId());
    }

    @Test
    @DisplayName("Should successfully clear all notifications for current user")
    void shouldClearAllNotificationsSuccessfully() throws Exception {
        mockAuthentication(testUser.getUsername());
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(testUser);

        mockMvc.perform(delete("/api/notifications/clear-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(0)))
                .andExpect(jsonPath("$.deleted", is(true)));

        verify(notificationService).deleteAllForUser(testUser.getId());
    }
}
