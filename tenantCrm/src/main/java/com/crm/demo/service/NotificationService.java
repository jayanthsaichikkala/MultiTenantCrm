package com.crm.demo.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.Meeting;
import com.crm.demo.model.Notification;
import com.crm.demo.model.Task;
import com.crm.demo.model.Team;
import com.crm.demo.model.User;
import com.crm.demo.repository.NotificationRepository;
import com.crm.demo.repository.TeamRepository;
import com.crm.demo.repository.UserRepository;
import java.util.HashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_HR = "HR";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private static final String TYPE_REPORT = "REPORT";
    private static final String TYPE_HOLIDAY = "HOLIDAY";
    private static final String TYPE_LEAVE = "LEAVE";
    private static final String TYPE_MEETING = "MEETING";
    private static final String TYPE_TASK = "TASK";
    private static final String TYPE_TEAM = "TEAM";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               TeamRepository teamRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification notify(User user, String title, String message, String type) {
        if (user == null || user.getId() == null) return null;

        // Enforce role-based notification policies
        var role = roleOf(user);
        var normType = type != null ? type.toUpperCase() : "";

        if (!isNotificationAllowed(role, normType)) {
            return null;
        }

        try {
            var n = new Notification();
            n.setUserId(user.getId());
            n.setTitle(title);
            n.setMessage(message);
            n.setType(type);
            n.setLink(resolveLink(user, type));
            var saved = notificationRepository.save(n);

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendWebSocketNotification(user, saved);
                    }
                });
            } else {
                sendWebSocketNotification(user, saved);
            }

            return saved;
        } catch (Exception e) {
            log.error("Failed to save notification for user {}: {}", user.getUsername(), e.getMessage());
            return null;
        }
    }

    private boolean isNotificationAllowed(String role, String normType) {
        return switch (role) {
            case ROLE_ADMIN -> TYPE_REPORT.equals(normType) || TYPE_HOLIDAY.equals(normType);
            case ROLE_HR -> TYPE_LEAVE.equals(normType) || TYPE_MEETING.equals(normType) 
                    || TYPE_HOLIDAY.equals(normType) || TYPE_REPORT.equals(normType);
            case ROLE_MANAGER -> TYPE_TEAM.equals(normType) || TYPE_TASK.equals(normType) 
                    || TYPE_MEETING.equals(normType) || TYPE_HOLIDAY.equals(normType);
            case ROLE_EMPLOYEE -> TYPE_TASK.equals(normType) || TYPE_LEAVE.equals(normType) 
                    || TYPE_MEETING.equals(normType) || TYPE_HOLIDAY.equals(normType) 
                    || TYPE_REPORT.equals(normType);
            default -> false;
        };
    }

    private void sendWebSocketNotification(User user, Notification notification) {
        try {
            messagingTemplate.convertAndSend("/topic/notifications/" + user.getId(), toDto(notification));
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", user.getUsername(), e.getMessage());
        }
    }

    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("type", n.getType());
        m.put("link", n.getLink());
        m.put("read", n.isRead());
        m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        return m;
    }

    public void notifyByUsername(String username, String title, String message, String type) {
        if (username == null || username.isBlank()) return;
        var user = userRepository.findByUsername(username.trim());
        if (user != null) {
            notify(user, title, message, type);
        }
    }

    public void notifyUsersInTenantByRole(String tenant, String role, String title, String message, String type) {
        if (tenant == null || tenant.isBlank() || role == null) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> role.equalsIgnoreCase(u.getRole()))
                .forEach(u -> notify(u, title, message, type));
    }

    public void notifyAllInTenant(String tenant, String title, String message, String type) {
        if (tenant == null || tenant.isBlank()) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> u.getRole() != null && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
                .forEach(u -> notify(u, title, message, type));
    }

    public List<Notification> getRecentForUser(Long userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadStatusFalse(userId);
    }

    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .map(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllReadForUser(userId);
    }

    @Transactional
    public boolean deleteNotification(Long notificationId, Long userId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .map(n -> {
                    notificationRepository.delete(n);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }

    public void notifyMeetingParticipants(Meeting meeting) {
        if (meeting == null) return;
        String participants = meeting.getParticipants();
        if (participants == null || participants.isBlank()) return;

        String scheduledBy = meeting.getScheduledBy() != null ? meeting.getScheduledBy() : "";
        String dateStr = meeting.getMeetingDate() != null ? meeting.getMeetingDate().toString() : "TBD";

        Arrays.stream(participants.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty() && !name.equalsIgnoreCase(scheduledBy))
                .forEach(username -> {
                    var user = userRepository.findByUsername(username);
                    if (user == null) return;
                    notify(user,
                            "New Meeting Scheduled",
                            "You have been invited to \"" + meeting.getTitle() + "\" on " + dateStr + ".",
                            TYPE_MEETING);
                });
    }

    public void notifyTaskAssigned(User employee, String assignerName, String taskTitle) {
        if (employee == null) return;
        notify(employee,
                "New Task Assigned",
                assignerName + " assigned you: \"" + taskTitle + "\"",
                TYPE_TASK);
    }

    public void notifyTaskSubmittedForReview(User employee, Task task) {
        notifyTaskStatusUpdated(employee, task, "done");
    }

    public void notifyTaskStatusUpdated(User employee, Task task, String status) {
        if (employee == null || task == null) return;
        String tenant = getTenantSegment(employee);
        Map<Long, User> managers = new LinkedHashMap<>();

        if (task.getCreatedBy() != null && !task.getCreatedBy().isBlank()) {
            var creator = userRepository.findByUsername(task.getCreatedBy().trim());
            addTaskStatusRecipient(managers, creator, employee, task);
        }

        List<Team> teams = teamRepository.findByMemberAndTenant(employee, tenant);
        for (Team team : teams) {
            User manager = team.getManager();
            addTaskStatusRecipient(managers, manager, employee, task);
        }

        String displayStatus = displayTaskStatus(status != null ? status : task.getStatus());
        boolean reviewReady = "done".equalsIgnoreCase(status != null ? status : task.getStatus());
        String title = reviewReady ? "Task Ready for Review" : "Task Status Updated";
        String message = reviewReady
                ? employee.getUsername() + " updated \"" + task.getTitle() + "\" to Done and submitted it for your review."
                : employee.getUsername() + " updated \"" + task.getTitle() + "\" to " + displayStatus + ".";

        for (User manager : managers.values()) {
            notify(manager,
                    title,
                    message,
                    TYPE_TASK);
        }
    }

    private void addTaskStatusRecipient(Map<Long, User> recipients, User manager, User employee, Task task) {
        if (manager == null || manager.getId() == null) return;
        if (employee != null && manager.getId().equals(employee.getId())) return;
        if (task != null && task.getTenantSegment() != null && !task.getTenantSegment().isBlank()
                && !task.getTenantSegment().equals(getTenantSegment(manager))) {
            return;
        }
        recipients.putIfAbsent(manager.getId(), manager);
    }

    private String displayTaskStatus(String status) {
        if ("in-progress".equalsIgnoreCase(status)) return "In Progress";
        if ("done".equalsIgnoreCase(status)) return "Done";
        return "Pending";
    }

    public void notifyTaskVerified(User employee, String managerName, String taskTitle,
                                   String action, String reason) {
        if (employee == null) return;
        if ("approve".equalsIgnoreCase(action)) {
            notify(employee,
                    "Task Approved",
                    managerName + " approved your completed task \"" + taskTitle + "\".",
                    TYPE_TASK);
        } else if ("reject".equalsIgnoreCase(action)) {
            var msg = managerName + " returned \"" + taskTitle + "\" for rework.";
            if (reason != null && !reason.isBlank()) {
                msg += " Feedback: " + reason.trim();
            }
            notify(employee, "Task Returned", msg, TYPE_TASK);
        } else if ("reopen".equalsIgnoreCase(action)) {
            notify(employee,
                    "Task Reopened",
                    managerName + " reopened \"" + taskTitle + "\". Please continue working on it.",
                    TYPE_TASK);
        }
    }

    public void notifyLeaveSubmitted(LeaveRequest leave) {
        if (leave == null || leave.getTenantSegment() == null) return;
        var employeeName = leave.getEmployeeName() != null ? leave.getEmployeeName() : "An employee";
        notifyUsersInTenantByRole(
                leave.getTenantSegment(),
                ROLE_HR,
                "New Leave Request",
                employeeName + " applied for leave.",
                TYPE_LEAVE);
    }

    public void notifyLeaveReviewed(User employee, String status, String leaveType,
                                    LocalDate from, LocalDate to, String reviewer) {
        if (employee == null) return;
        var period = from + " to " + to;
        if ("Approved".equalsIgnoreCase(status)) {
            notify(employee,
                    "Leave Request Approved",
                    "Your " + leaveType + " leave (" + period + ") was approved by " + reviewer + ".",
                    TYPE_LEAVE);
        } else if ("Rejected".equalsIgnoreCase(status)) {
            notify(employee,
                    "Leave Request Rejected",
                    "Your " + leaveType + " leave (" + period + ") was rejected by " + reviewer + ".",
                    TYPE_LEAVE);
        }
    }

    public void notifyTeamAdded(User employee, String teamName) {
        if (employee == null) return;
        notify(employee,
                "Added to Team",
                "You have been added to team \"" + teamName + "\".",
                TYPE_TEAM);
    }

    public void notifyManagerAssigned(User manager, String teamName) {
        if (manager == null) return;
        notify(manager,
                "Team Assignment",
                "You have been assigned as manager of team \"" + teamName + "\".",
                TYPE_TEAM);
    }

    public void notifyPerformanceReview(User employee, String reviewer, String reviewMonth, int rating) {
        if (employee == null) return;
        notify(employee,
                "Performance Review Updated",
                reviewer + " submitted your performance review for " + reviewMonth
                        + " (Rating: " + rating + "/5).",
                "PERFORMANCE");
    }

    public void notifyReportReceived(User recipient, String senderName, String reportTitle) {
        if (recipient == null) return;
        notify(recipient,
                "New Report Received",
                senderName + " sent you a report: \"" + reportTitle + "\".",
                TYPE_REPORT);
    }

    public void notifyHolidayAdded(String tenant, String holidayName, String date) {
        notifyAllInTenant(
                tenant,
                "Holiday Announced",
                holidayName + " on " + date + " has been added to the company calendar.",
                TYPE_HOLIDAY);
    }

    public void sendLiveUpdate(User recipient, String type, String title, String message) {
        if (recipient == null || recipient.getId() == null) return;
        try {
            notify(recipient, title, message, type);
        } catch (Exception e) {
            log.error("Failed to save and send live update to user {}: {}", recipient.getUsername(), e.getMessage());
        }
    }

    public void sendLiveUpdateToTenant(String tenant, String type, String title, String message) {
        if (tenant == null || tenant.isBlank()) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> u.getRole() != null && !ROLE_SUPER_ADMIN.equalsIgnoreCase(u.getRole()))
                .forEach(u -> sendLiveUpdate(u, type, title, message));
    }

    public void sendLiveUpdateToTenantRole(String tenant, String role, String type, String title, String message) {
        if (tenant == null || tenant.isBlank() || role == null) return;
        userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> role.equalsIgnoreCase(u.getRole()))
                .forEach(u -> sendLiveUpdate(u, type, title, message));
    }

    public void notifyAttendanceUpdated(User employee, String action) {
        // Disabled "Attendance Updated" notifications entirely to reduce spam
    }

    public void notifyAttendanceModified(User employee, String reviewerName) {
        if (employee == null) return;
        sendLiveUpdate(employee, "ATTENDANCE", 
            "Attendance Record Updated", 
            reviewerName + " updated your attendance record.");
    }

    public void notifyEmployeeManagementChanged(String tenant, String action, String employeeName) {
        // Disabled "Employee List Updated" notifications entirely for all roles and dashboards
    }



    private String dashboardLink(User user) {
        var role = roleOf(user);
        return switch (role) {
            case ROLE_EMPLOYEE -> "/employee/dashboard";
            case ROLE_MANAGER -> "/manager/dashboard";
            case ROLE_HR -> "/hr/dashboard";
            case ROLE_ADMIN -> "/admin/dashboard";
            case ROLE_SUPER_ADMIN -> "/superadmin/dashboard";
            default -> "/login";
        };
    }

    private String tasksLink(User user) {
        return switch (roleOf(user)) {
            case ROLE_EMPLOYEE -> "/employee/tasks";
            case ROLE_MANAGER -> "/manager/tasks";
            case ROLE_HR -> "/hr/tasks";
            case ROLE_ADMIN -> "/admin/tasks";
            default -> dashboardLink(user);
        };
    }

    private String leavesLink(User user) {
        return switch (roleOf(user)) {
            case ROLE_EMPLOYEE -> "/employee/leaves";
            case ROLE_MANAGER -> "/manager/leave";
            case ROLE_HR -> "/hr/leaves";
            default -> dashboardLink(user);
        };
    }

    private String meetingsLink(User user) {
        return switch (roleOf(user)) {
            case ROLE_EMPLOYEE -> "/employee/meetings";
            case ROLE_MANAGER -> "/manager/meetings";
            case ROLE_HR -> "/hr/meetings";
            case ROLE_ADMIN -> "/admin/schedule-meeting";
            default -> dashboardLink(user);
        };
    }

    private String reportsLink(User user) {
        return switch (roleOf(user)) {
            case ROLE_EMPLOYEE -> "/employee/reports";
            case ROLE_MANAGER -> "/manager/reports";
            case ROLE_HR -> "/hr/reports";
            case ROLE_ADMIN -> "/admin/reports";
            default -> dashboardLink(user);
        };
    }

    private String calendarLink(User user) {
        return switch (roleOf(user)) {
            case ROLE_EMPLOYEE -> "/employee/calendar";
            case ROLE_MANAGER -> "/manager/calendar";
            case ROLE_HR -> "/hr/calendar";
            case ROLE_ADMIN -> "/admin/calendar";
            default -> dashboardLink(user);
        };
    }



    private String resolveLink(User user, String type) {
        var normType = type != null ? type.toUpperCase() : "";
        return switch (normType) {
            case TYPE_TASK -> tasksLink(user);
            case TYPE_LEAVE -> leavesLink(user);
            case TYPE_MEETING -> meetingsLink(user);
            case TYPE_REPORT -> reportsLink(user);
            case TYPE_HOLIDAY -> calendarLink(user);
            case TYPE_TEAM -> {
                var role = roleOf(user);
                yield switch (role) {
                    case ROLE_MANAGER -> "/manager/team";
                    case ROLE_HR -> "/hr/teams";
                    default -> dashboardLink(user);
                };
            }
            default -> dashboardLink(user);
        };
    }

    private String roleOf(User user) {
        return user != null && user.getRole() != null ? user.getRole().toUpperCase() : "";
    }

    private String getTenantSegment(User user) {
        if (user == null || user.getEmail() == null) return "";
        String email = user.getEmail();
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        int lastDot = localPart.lastIndexOf('.');
        return lastDot >= 0 ? localPart.substring(lastDot + 1) : localPart;
    }
}
