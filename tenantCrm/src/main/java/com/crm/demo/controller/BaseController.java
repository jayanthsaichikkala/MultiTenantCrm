package com.crm.demo.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.repository.HolidayRepository;
import com.crm.demo.repository.LeaveRequestRepository;

public abstract class BaseController {

    protected UserRepository userRepository;
    protected HolidayRepository holidayRepository;
    protected LeaveRequestRepository leaveRequestRepository;

    protected BaseController() {
        this.userRepository = null;
        this.holidayRepository = null;
        this.leaveRequestRepository = null;
    }

    protected BaseController(UserRepository userRepository,
                             HolidayRepository holidayRepository,
                             LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

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

    // =========================================================
    // SHARED ATTENDANCE DAY LIST BUILDER
    // =========================================================

    /**
     * Builds a merged day-by-day attendance list for the given date range, newest-first.
     * Priority: holiday > weekend > real record > approved-leave > absent (past weekday).
     *
     * @param records   attendance records already fetched from DB for [from, to]
     * @param from      range start (inclusive)
     * @param to        range end   (inclusive)
     * @param holidays  map of date → holiday name (from fetchHolidays)
     * @param user      user whose approved leaves are factored in (may be null → no leave check)
     */
    protected java.util.List<com.crm.demo.model.AttendanceDay> buildDayList(
            java.util.List<com.crm.demo.model.Attendance> records,
            LocalDate from, LocalDate to,
            Map<LocalDate, String> holidays,
            com.crm.demo.model.User user) {

        var byDate = new java.util.LinkedHashMap<LocalDate, com.crm.demo.model.Attendance>();
        if (records != null) {
            for (var a : records) {
                if (a != null && a.getDate() != null) byDate.put(a.getDate(), a);
            }
        }

        // Collect approved leave dates for this user in [from, to]
        var approvedLeaveDates = new java.util.LinkedHashSet<LocalDate>();
        if (user != null) {
            for (var leave : leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user)) {
                if (!"Approved".equalsIgnoreCase(leave.getStatus())
                        || leave.getFromDate() == null || leave.getToDate() == null) continue;
                var cursor = leave.getFromDate();
                while (!cursor.isAfter(leave.getToDate())) {
                    if (from != null && to != null && !cursor.isBefore(from) && !cursor.isAfter(to)) {
                        approvedLeaveDates.add(cursor);
                    }
                    cursor = cursor.plusDays(1);
                }
            }
        }

        var days = new java.util.ArrayList<com.crm.demo.model.AttendanceDay>();
        if (from == null || to == null) return days;

        var today  = LocalDate.now();
        var cursor = to;
        while (!cursor.isBefore(from)) {
            if (holidays != null && holidays.containsKey(cursor)) {
                days.add(new com.crm.demo.model.AttendanceDay(cursor, holidays.get(cursor), true));
            } else {
                var dow = cursor.getDayOfWeek();
                if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                    days.add(new com.crm.demo.model.AttendanceDay(cursor, "weekend"));
                } else if (byDate.containsKey(cursor)) {
                    days.add(new com.crm.demo.model.AttendanceDay(byDate.get(cursor)));
                } else if (approvedLeaveDates.contains(cursor)) {
                    days.add(new com.crm.demo.model.AttendanceDay(cursor, "leave"));
                } else if (!cursor.isAfter(today)) {
                    days.add(new com.crm.demo.model.AttendanceDay(cursor, "absent"));
                }
            }
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    /**
     * Overload without leave-date support (used by Admin modal where leave data is not needed).
     */
    protected java.util.List<com.crm.demo.model.AttendanceDay> buildDayList(
            java.util.List<com.crm.demo.model.Attendance> records,
            LocalDate from, LocalDate to,
            Map<LocalDate, String> holidays) {
        return buildDayList(records, from, to, holidays, null);
    }

    // =========================================================
    // SHARED VALIDATION HELPERS
    // =========================================================

    /**
     * Validates a username: non-blank, 3-50 chars, letters/numbers/dots/hyphens/underscores.
     * Returns an error message string, or null if valid.
     */
    protected String validateUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            return "Username is required.";
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            return "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.";
        }
        return null;
    }

    /**
     * Validates an email address and optionally checks that it belongs to the given tenant domain.
     * Returns an error message string, or null if valid.
     */
    protected String validateEmail(String email, String tenant, String domainSuffix) {
        if (email == null || email.trim().isBlank()) {
            return "Email is required.";
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            return "Please provide a valid email address.";
        }
        if (tenant != null && !tenant.isBlank() && !email.trim().contains("." + tenant + "@")) {
            return "Email must belong to your tenant domain (expected format: name." + tenant + domainSuffix;
        }
        return null;
    }

    /**
     * Validates a new-user password: non-null, min 4 chars, alphanumeric only, matches confirm.
     * Returns an error message string, or null if valid.
     */
    protected String validatePassword(String password, String confirmPassword) {
        if (password == null || password.length() < 4) {
            return "Password must be at least 4 characters long.";
        }
        if (!password.matches("^[A-Za-z0-9]+$")) {
            return "Password must contain only letters and numbers (no special characters).";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }

    // =========================================================
    // SHARED ATTENDANCE ROW BUILDER
    // =========================================================

    /**
     * Converts a list of AttendanceDay objects into a list of string maps
     * suitable for JSON attendance modals used in Admin, HR, and Manager controllers.
     */
    protected java.util.List<java.util.Map<String, String>> buildAttendanceRows(
            java.util.List<com.crm.demo.model.AttendanceDay> days) {
        var rows = new java.util.ArrayList<java.util.Map<String, String>>();
        for (var d : days) {
            var row = new java.util.LinkedHashMap<String, String>();
            row.put("date",      d.getDate().toString());
            row.put("checkIn",   d.getCheckInDisplay());
            row.put("checkOut",  d.getCheckOutDisplay());
            row.put("worked",    d.getWorkedHours());
            row.put("breakTime", d.getBreakDuration());
            row.put("dayType",   d.isReal() && d.getRecord().getCheckOut() != null ? d.getRecord().getDayType() : "—");
            row.put("status",    d.getStatus());
            rows.add(row);
        }
        return rows;
    }

    // =========================================================
    // SHARED ATTACHMENT RESPONSE BUILDER
    // =========================================================

    /**
     * Builds a ResponseEntity for serving a file attachment.
     *
     * @param disposition "inline" or "attachment"
     * @param filename    original filename
     * @param data        file bytes
     * @param contentType MIME type (null falls back to application/octet-stream)
     */
    protected org.springframework.http.ResponseEntity<byte[]> buildFileResponse(
            String disposition, String filename, byte[] data, String contentType) {
        var ct = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, ct)
                .body(data);
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
