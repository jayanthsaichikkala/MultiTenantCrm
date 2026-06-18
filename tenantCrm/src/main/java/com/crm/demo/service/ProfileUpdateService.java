package com.crm.demo.service;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.security.JwtUtil;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class ProfileUpdateService {

    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    public boolean updateProfile(User user,
                                 String username,
                                 String email,
                                 String password,
                                 String confirmPassword,
                                 RedirectAttributes ra,
                                 HttpServletResponse response) {
        if (user == null) {
            return false;
        }

        boolean changed = false;

        // Validate username
        if (username == null || username.trim().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Username is required.");
            return false;
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            ra.addFlashAttribute("errorMessage", "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.");
            return false;
        }

        // Validate email
        if (email == null || email.trim().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Email is required.");
            return false;
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            ra.addFlashAttribute("errorMessage", "Please provide a valid email address.");
            return false;
        }

        // Enforce tenant segment preservation for non-admin/non-superadmin users
        String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
            String currentTenant = getTenantSegment(user);
            if (!currentTenant.isEmpty() && !email.trim().contains("." + currentTenant + "@")) {
                ra.addFlashAttribute("errorMessage", "Email must belong to your tenant domain (must contain '." + currentTenant + "@').");
                return false;
            }
        } else if ("ADMIN".equals(role)) {
            // Admin email must contain a dot in local part to establish a tenant segment
            String local = email.substring(0, email.indexOf('@'));
            if (local.lastIndexOf('.') < 0) {
                ra.addFlashAttribute("errorMessage", "Admin email must follow the pattern name.tenant@domain.com (e.g. admin.wipro@crm.com).");
                return false;
            }
        }

        // Check username and email uniqueness
        User existingUserByUname = userRepository.findByUsername(username.trim());
        if (isAnotherUser(existingUserByUname, user)) {
            ra.addFlashAttribute("errorMessage", "Username is already taken.");
            return false;
        }
        User existingUserByEmail = userRepository.findByEmail(email.trim());
        if (isAnotherUser(existingUserByEmail, user)) {
            ra.addFlashAttribute("errorMessage", "Email is already taken.");
            return false;
        }

        // Update username & email
        if (!Objects.equals(username.trim(), user.getUsername())) {
            user.setUsername(username.trim());
            changed = true;
        }
        if (!Objects.equals(email.trim(), user.getEmail())) {
            user.setEmail(email.trim());
            changed = true;
        }

        // Validate and update password
        if (password != null && !password.isBlank()) {
            if (password.length() < 4) {
                ra.addFlashAttribute("errorMessage", "Password must be at least 4 characters long.");
                return false;
            }
            if (!password.matches("^[A-Za-z0-9]+$")) {
                ra.addFlashAttribute("errorMessage", "Password must contain only letters and numbers (no special characters).");
                return false;
            }
            if (!Objects.equals(password, confirmPassword)) {
                ra.addFlashAttribute("errorMessage", "Passwords do not match.");
                return false;
            }

            if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(password));
                changed = true;
            }
        }

        if (changed) {
            userRepository.save(user);
            ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        } else {
            ra.addFlashAttribute("successMessage", "No changes to update.");
        }

        refreshBrowserAuth(user, ra, response);
        return true;
    }

    private void refreshBrowserAuth(User user, RedirectAttributes ra, HttpServletResponse response) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        ra.addFlashAttribute("newJwtToken", token);
        ra.addFlashAttribute("newJwtUsername", user.getUsername());
        ra.addFlashAttribute("newJwtRole", user.getRole());

        ResponseCookie cookie = ResponseCookie.from("jwt_token", token)
                .path("/")
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isAnotherUser(User existing, User current) {
        return existing != null && !existing.getId().equals(current.getId());
    }

    private String getTenantSegment(User user) {
        if (user == null || user.getEmail() == null) return "";
        String email = user.getEmail();
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        int lastDot = localPart.lastIndexOf('.');
        return lastDot >= 0 ? localPart.substring(lastDot + 1) : localPart;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
