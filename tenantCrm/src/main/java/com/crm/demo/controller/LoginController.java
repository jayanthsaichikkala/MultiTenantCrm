package com.crm.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.security.JwtUtil;

@Controller
public class LoginController {

	
    @Autowired private UserRepository      userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil             jwtUtil;

    // ─── Serve the login HTML page ────────────────────────────────────────────────
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // ─── REST: authenticate and return JWT ───────────────────────────────────────
    // Called by the login form via fetch() — returns JSON, no redirect
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> authenticate(@RequestBody Map<String, String> body) {

        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required."));
        }

        User user = userRepository.findByUsernameOrEmail(username, username).orElse(null);

        if (user != null && passwordEncoder.matches(password, user.getPassword())) {

            // Block inactive users from logging in
            if (!user.isActive()) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "Your account is inactive. Please contact your administrator."));
            }

            // Block employees/managers/HR if their tenant's admin is inactive
            String role = user.getRole();
            if (role != null && !role.equalsIgnoreCase("SUPER_ADMIN") && !role.equalsIgnoreCase("ADMIN")) {
                String tenantSegment = extractTenantSegment(user.getEmail());
                if (tenantSegment != null && !tenantSegment.isBlank()) {
                    boolean adminInactive = userRepository.findAll().stream()
                            .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                            .filter(u -> tenantSegment.equals(extractTenantSegment(u.getEmail())))
                            .anyMatch(u -> !u.isActive());
                    if (adminInactive) {
                        return ResponseEntity.status(403)
                                .body(Map.of("error", "Access is currently disabled. Please contact your administrator."));
                    }
                }
            }

            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            return ResponseEntity.ok(Map.of(
                    "token",    token,
                    "username", user.getUsername(),
                    "role",     user.getRole(),
                    "redirect", dashboardFor(user.getRole())
            ));
        }

        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid username or password."));
    }

    // ─── Logout: client just deletes the token from localStorage ─────────────────
    // This endpoint is optional — kept for convenience (e.g. server-side audit log)
    @GetMapping("/logout")
    public String logout() {
        // Nothing to invalidate server-side — token lives only in the browser
        return "redirect:/login";
    }

    @GetMapping({"/dashboard", "/tasks", "/meetings", "/leaves", "/leave", "/teams", "/team", "/performance", "/reports", "/attendance", "/calendar"})
    public String handleLegacyRedirects(HttpServletRequest request) {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return "redirect:/login";
        }

        String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        String path = request.getRequestURI().toLowerCase();

        if (path.endsWith("/dashboard")) {
            return "redirect:" + dashboardFor(role);
        } else if (path.endsWith("/tasks")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/tasks";
                case "MANAGER" -> "redirect:/manager/tasks";
                case "HR" -> "redirect:/hr/tasks";
                case "ADMIN" -> "redirect:/admin/tasks";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/meetings")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/meetings";
                case "MANAGER" -> "redirect:/manager/meetings";
                case "HR" -> "redirect:/hr/meetings";
                case "ADMIN" -> "redirect:/admin/schedule-meeting";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/leaves") || path.endsWith("/leave")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/leaves";
                case "MANAGER" -> "redirect:/manager/leaves";
                case "HR" -> "redirect:/hr/leaves";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/teams") || path.endsWith("/team")) {
            return switch (role) {
                case "MANAGER" -> "redirect:/manager/team";
                case "HR" -> "redirect:/hr/teams";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/performance")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/performance";
                case "MANAGER" -> "redirect:/manager/performance";
                case "HR" -> "redirect:/hr/performance";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/reports")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/reports";
                case "MANAGER" -> "redirect:/manager/reports";
                case "HR" -> "redirect:/hr/reports";
                case "ADMIN" -> "redirect:/admin/reports";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/attendance")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/attendance";
                case "MANAGER" -> "redirect:/manager/attendance";
                case "HR" -> "redirect:/hr/attendance";
                default -> "redirect:" + dashboardFor(role);
            };
        } else if (path.endsWith("/calendar")) {
            return switch (role) {
                case "EMPLOYEE" -> "redirect:/employee/calendar";
                case "MANAGER" -> "redirect:/manager/calendar";
                case "HR" -> "redirect:/hr/calendar";
                case "ADMIN" -> "redirect:/admin/calendar";
                default -> "redirect:" + dashboardFor(role);
            };
        }

        return "redirect:/login";
    }

    // ─── Helper ───────────────────────────────────────────────────────────────────
    private String dashboardFor(String role) {
        if (role == null) return "/login";
        return switch (role.toUpperCase()) {
            case "SUPER_ADMIN" -> "/superadmin";
            case "ADMIN"       -> "/admin/dashboard";
            case "MANAGER"     -> "/manager/dashboard";
            case "HR"          -> "/hr/dashboard";
            case "EMPLOYEE"    -> "/employee/dashboard";
            default            -> "/login";
        };
    }

    /** Extract tenant segment from email: "emp.tcs@crm.com" → "tcs" */
    private String extractTenantSegment(String email) {
        if (email == null || !email.contains("@")) return null;
        String local = email.substring(0, email.indexOf('@'));
        int dot = local.lastIndexOf('.');
        return dot >= 0 ? local.substring(dot + 1) : null;
    }
    
 
    }

