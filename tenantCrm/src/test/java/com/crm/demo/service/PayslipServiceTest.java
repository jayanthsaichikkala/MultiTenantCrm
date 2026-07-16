package com.crm.demo.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.User;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.PayrollTemplateRepository;
import com.crm.demo.repository.PayslipRepository;
import com.crm.demo.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PayslipServiceTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private PayrollTemplateRepository payrollTemplateRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private PayslipService payslipService;

    private User employee;

    @BeforeEach
    void setUp() {
        employee = new User();
        employee.setId(1L);
        employee.setUsername("emp");
    }

    @Test
    void testCountApprovedLeaveDays_NoLeaves() {
        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(employee)).thenReturn(Collections.emptyList());
        int count = payslipService.countApprovedLeaveDays(employee, "Sick", 7, 2026);
        assertEquals(0, count);
    }

    @Test
    void testCountApprovedLeaveDays_WithApprovedLeaves() {
        LeaveRequest leave = new LeaveRequest();
        leave.setStatus("Approved");
        leave.setType("Sick");
        leave.setFromDate(LocalDate.of(2026, 7, 10));
        leave.setToDate(LocalDate.of(2026, 7, 15)); // 6 days

        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(employee)).thenReturn(List.of(leave));

        int count = payslipService.countApprovedLeaveDays(employee, "Sick", 7, 2026);
        assertEquals(6, count);
    }

    @Test
    void testCountApprovedLeaveDays_NonMatchingStatusOrType() {
        LeaveRequest leave1 = new LeaveRequest();
        leave1.setStatus("Pending");
        leave1.setType("Sick");
        leave1.setFromDate(LocalDate.of(2026, 7, 10));
        leave1.setToDate(LocalDate.of(2026, 7, 15));

        LeaveRequest leave2 = new LeaveRequest();
        leave2.setStatus("Approved");
        leave2.setType("Casual");
        leave2.setFromDate(LocalDate.of(2026, 7, 10));
        leave2.setToDate(LocalDate.of(2026, 7, 15));

        when(leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(employee)).thenReturn(List.of(leave1, leave2));

        int count = payslipService.countApprovedLeaveDays(employee, "Sick", 7, 2026);
        assertEquals(0, count);
    }
}
