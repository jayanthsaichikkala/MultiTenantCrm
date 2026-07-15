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

    protected LocalDate[] resolveDateRange(String fromStr, String toStr) {
        LocalDate today = LocalDate.now();
        LocalDate filterFrom = (fromStr != null && !fromStr.isBlank()) ? LocalDate.parse(fromStr) : today.minusDays(29);
        LocalDate filterTo   = (toStr   != null && !toStr.isBlank())   ? LocalDate.parse(toStr)   : today;
        if (filterFrom == null) {
            filterFrom = today.minusDays(29);
        }
        if (filterTo == null) {
            filterTo = today;
        }
        if (filterTo != null && filterTo.isAfter(today)) {
            filterTo = today;
        }
        if (filterFrom != null && filterTo != null && filterFrom.isAfter(filterTo)) {
            filterFrom = filterTo;
        }
        return new LocalDate[]{filterFrom, filterTo};
    }

    protected Map<String, Object> buildAnalyticsMap(java.util.List<com.crm.demo.model.Task> tasks, java.util.List<com.crm.demo.model.User> people, boolean countByCreator, boolean excludeHrManager) {
        var data = new java.util.LinkedHashMap<String, Object>();
        var scopedTasks = tasks != null ? tasks : java.util.Collections.<com.crm.demo.model.Task>emptyList();
        var scopedPeople = people != null ? people : java.util.Collections.<com.crm.demo.model.User>emptyList();

        var statusDone = scopedTasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();
        var statusInProgress = scopedTasks.stream().filter(t -> "in-progress".equalsIgnoreCase(t.getStatus())).count();
        var statusPending = scopedTasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
        var statusReview = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getStatus())).count();
        var priorityHigh = scopedTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority())).count();
        var priorityMedium = scopedTasks.stream().filter(t -> "Medium".equalsIgnoreCase(t.getPriority())).count();
        var priorityLow = scopedTasks.stream().filter(t -> "Low".equalsIgnoreCase(t.getPriority())).count();

        var memberLabels = new java.util.ArrayList<String>();
        var memberTaskCounts = new java.util.ArrayList<Long>();

        if (countByCreator) {
            var creatorCounts = new java.util.LinkedHashMap<String, Long>();
            for (var task : scopedTasks) {
                var creator = task.getCreatedBy() == null || task.getCreatedBy().isBlank() ? "Unassigned" : task.getCreatedBy();
                creatorCounts.put(creator, creatorCounts.getOrDefault(creator, 0L) + 1);
            }
            memberLabels.addAll(creatorCounts.keySet());
            for (var val : creatorCounts.values()) {
                memberTaskCounts.add(val);
            }
        } else {
            for (var person : scopedPeople) {
                if (excludeHrManager && ("HR".equalsIgnoreCase(person.getRole()) || "MANAGER".equalsIgnoreCase(person.getRole()))) {
                    continue;
                }
                var count = scopedTasks.stream()
                        .filter(t -> person.getUsername() != null && person.getUsername().equalsIgnoreCase(t.getAssignedTo()))
                        .count();
                memberLabels.add(person.getUsername());
                memberTaskCounts.add(count);
            }
        }

        var activeCount = scopedPeople.stream().filter(com.crm.demo.model.User::isActive).count();
        var inactiveCount = scopedPeople.size() - activeCount;
        var verified = scopedTasks.stream().filter(t -> "approved".equalsIgnoreCase(t.getVerificationStatus())).count();
        var rejected = scopedTasks.stream().filter(t -> "rejected".equalsIgnoreCase(t.getVerificationStatus())).count();
        var waiting = scopedTasks.stream().filter(t -> "waiting-for-review".equalsIgnoreCase(t.getVerificationStatus())).count();
        var unverified = scopedTasks.size() - verified - rejected - waiting;

        data.put("statusDone", statusDone);
        data.put("statusInProgress", statusInProgress);
        data.put("statusPending", statusPending);
        data.put("statusReview", statusReview);
        data.put("priorityHigh", priorityHigh);
        data.put("priorityMedium", priorityMedium);
        data.put("priorityLow", priorityLow);
        data.put("memberLabels", memberLabels);
        data.put("memberTaskCounts", memberTaskCounts);
        data.put("activeTeam", activeCount);
        data.put("inactiveTeam", inactiveCount);
        data.put("verified", verified);
        data.put("rejected", rejected);
        data.put("waiting", waiting);
        data.put("unverified", java.lang.Math.max(unverified, 0));
        data.put("totalMyTasks", scopedTasks.size());
        return data;
    }

    protected void populateAnalyticsAttributes(org.springframework.ui.Model model, Map<String, Object> data) {
        model.addAttribute("chartStatusDone", data.get("statusDone"));
        model.addAttribute("chartStatusInProgress", data.get("statusInProgress"));
        model.addAttribute("chartStatusPending", data.get("statusPending"));
        model.addAttribute("chartStatusReview", data.get("statusReview"));
        model.addAttribute("chartPriorityHigh", data.get("priorityHigh"));
        model.addAttribute("chartPriorityMedium", data.get("priorityMedium"));
        model.addAttribute("chartPriorityLow", data.get("priorityLow"));
        model.addAttribute("chartMemberLabels", data.get("memberLabels"));
        model.addAttribute("chartMemberTaskCounts", data.get("memberTaskCounts"));
        model.addAttribute("chartActiveTeam", data.get("activeTeam"));
        model.addAttribute("chartInactiveTeam", data.get("inactiveTeam"));
        model.addAttribute("chartVerified", data.get("verified"));
        model.addAttribute("chartRejected", data.get("rejected"));
        model.addAttribute("chartWaiting", data.get("waiting"));
        model.addAttribute("chartUnverified", data.get("unverified"));
        model.addAttribute("chartTotalMyTasks", data.get("totalMyTasks"));
    }
}
