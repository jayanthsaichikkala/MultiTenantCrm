package com.crm.demo.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;

public abstract class BaseController {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected HolidayRepository holidayRepository;

    @Autowired
    protected LeaveRequestRepository leaveRequestRepository;

    protected User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName());
    }

    protected String getTenantSegment(User user) {
        if (user == null || user.getEmail() == null) return "";
        String email = user.getEmail();
        try {
            return email.split("\\.")[1].split("@")[0];
        } catch (Exception e) {
            return "";
        }
    }

    protected boolean hasApprovedLeave(User user, LocalDate date) {
        if (user == null || date == null) return false;
        return leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user).stream()
                .anyMatch(leave -> "Approved".equalsIgnoreCase(leave.getStatus())
                        && !date.isBefore(leave.getFromDate())
                        && !date.isAfter(leave.getToDate()));
    }

    protected Map<LocalDate, String> fetchHolidays(String tenant, LocalDate from, LocalDate to) {
        var map = new java.util.LinkedHashMap<LocalDate, String>();
        if (tenant == null || tenant.isBlank() || from == null || to == null) return map;
        var list = holidayRepository.findByTenantAndDateRange(tenant, from.toString(), to.toString());
        for (var h : list) {
            if (h.getDate() != null) {
                map.put(LocalDate.parse(h.getDate()), h.getName() != null ? h.getName() : "Holiday");
            }
        }
        return map;
    }
}
