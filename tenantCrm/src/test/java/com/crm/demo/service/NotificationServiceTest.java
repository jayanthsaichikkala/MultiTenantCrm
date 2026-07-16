package com.crm.demo.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.crm.demo.model.Notification;
import com.crm.demo.model.User;
import com.crm.demo.repository.NotificationRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private NotificationService notificationService;

    private User employee;

    @BeforeEach
    void setUp() {
        employee = new User();
        employee.setId(1L);
        employee.setUsername("emp");
        employee.setRole("EMPLOYEE");
    }

    @Test
    void testNotifyLeaveReviewed_Approved() {
        LocalDate from = LocalDate.of(2026, 7, 10);
        LocalDate to = LocalDate.of(2026, 7, 15);

        Notification notif = new Notification();
        notif.setId(10L);
        
        when(notificationRepository.save(any(Notification.class))).thenReturn(notif);

        notificationService.notifyLeaveReviewed(employee, "Approved", "Sick", from, to, "hrUser");

        // The method notifyLeaveReviewed returns void, but internally we can test the notify method
        // by verifying that notificationRepository.save was called.
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void testNotifyLeaveReviewed_Rejected() {
        LocalDate from = LocalDate.of(2026, 7, 10);
        LocalDate to = LocalDate.of(2026, 7, 15);

        Notification notif = new Notification();
        notif.setId(11L);
        
        when(notificationRepository.save(any(Notification.class))).thenReturn(notif);

        notificationService.notifyLeaveReviewed(employee, "Rejected", "Sick", from, to, "hrUser");

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void testNotifyLeaveReviewed_OtherStatus() {
        LocalDate from = LocalDate.of(2026, 7, 10);
        LocalDate to = LocalDate.of(2026, 7, 15);

        notificationService.notifyLeaveReviewed(employee, "Pending", "Sick", from, to, "hrUser");

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
