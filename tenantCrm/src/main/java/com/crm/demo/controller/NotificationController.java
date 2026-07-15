package com.crm.demo.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crm.demo.model.Notification;
import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String KEY_ERROR = "error";
    private static final String VAL_UNAUTHORIZED = "Unauthorized";
    private static final String KEY_UNREAD_COUNT = "unreadCount";

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;

    public static class UnauthorizedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized() {
        return ResponseEntity.status(401).body(Map.of(KEY_ERROR, VAL_UNAUTHORIZED));
    }

    private User getCurrentUser() {
        var username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    private User getCurrentUserOrThrow() {
        var user = getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException();
        }
        return user;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe() {
        var user = getCurrentUserOrThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        var user = getCurrentUserOrThrow();

        var items = notificationService.getRecentForUser(user.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "notifications", items,
                KEY_UNREAD_COUNT, notificationService.getUnreadCount(user.getId())
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount() {
        var user = getCurrentUserOrThrow();
        return ResponseEntity.ok(Map.of(KEY_UNREAD_COUNT, notificationService.getUnreadCount(user.getId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id) {
        var user = getCurrentUserOrThrow();
        var ok = notificationService.markAsRead(id, user.getId());
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(KEY_UNREAD_COUNT, notificationService.getUnreadCount(user.getId())));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        var user = getCurrentUserOrThrow();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of(KEY_UNREAD_COUNT, 0L));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteOne(@PathVariable Long id) {
        var user = getCurrentUserOrThrow();
        var ok = notificationService.deleteNotification(id, user.getId());
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                KEY_UNREAD_COUNT, notificationService.getUnreadCount(user.getId()),
                "deleted", true
        ));
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> deleteAll() {
        var user = getCurrentUserOrThrow();
        notificationService.deleteAllForUser(user.getId());
        return ResponseEntity.ok(Map.of(KEY_UNREAD_COUNT, 0L, "deleted", true));
    }

    private Map<String, Object> toDto(Notification n) {
        var m = new HashMap<String, Object>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("type", n.getType());
        m.put("link", n.getLink());
        m.put("read", n.isRead());
        m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        return m;
    }
}
