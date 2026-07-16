package com.crm.demo.service;

import java.util.Objects;

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

    private static final String ERROR_MESSAGE_ATTR = "errorMessage";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public ProfileUpdateService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

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

        if (!validateUsername(username, ra) 
                || !validateEmail(email, user, ra) 
                || !checkUniqueness(username, email, user, ra) 
                || !validatePassword(password, confirmPassword, ra)) {
            return false;
        }

        var changed = false;

        // Update username & email
        if (!Objects.equals(username.trim(), user.getUsername())) {
            user.setUsername(username.trim());
            changed = true;
        }
        if (!Objects.equals(email.trim(), user.getEmail())) {
            user.setEmail(email.trim());
            changed = true;
        }

        // Update password
        if (password != null && !password.isBlank() && (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword()))) {
            user.setPassword(passwordEncoder.encode(password));
            changed = true;
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

    private boolean validateUsername(String username, RedirectAttributes ra) {
        if (username == null || username.trim().isBlank()) {
            ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Username is required.");
            return false;
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.");
            return false;
        }
        return true;
    }

    private boolean validateEmail(String email, User user, RedirectAttributes ra) {
        if (email == null || email.trim().isBlank()) {
            ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Email is required.");
            return false;
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Please provide a valid email address.");
            return false;
        }

        var role = user.getRole() != null ? user.getRole().toUpperCase() : "";
        if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
            var currentTenant = getTenantSegment(user);
            if (!currentTenant.isEmpty() && !email.trim().contains("." + currentTenant + "@")) {
                ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Email must belong to your tenant domain (must contain '." + currentTenant + "@').");
                return false;
            }
        } else if ("ADMIN".equals(role)) {
            // Admin email must contain a dot in local part to establish a tenant segment
            var local = email.substring(0, email.indexOf('@'));
            if (local.lastIndexOf('.') < 0) {
                ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Admin email must follow the pattern name.tenant@domain.com (e.g. admin.wipro@crm.com).");
                return false;
            }
        }
        return true;
    }

    private boolean checkUniqueness(String username, String email, User user, RedirectAttributes ra) {
        var existingUserByUname = userRepository.findByUsername(username.trim());
        if (isAnotherUser(existingUserByUname, user)) {
            ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Username is already taken.");
            return false;
        }
        var existingUserByEmail = userRepository.findByEmail(email.trim());
        if (isAnotherUser(existingUserByEmail, user)) {
            ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Email is already taken.");
            return false;
        }
        return true;
    }

    private boolean validatePassword(String password, String confirmPassword, RedirectAttributes ra) {
        if (password != null && !password.isBlank()) {
            if (password.length() < 4) {
                ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Password must be at least 4 characters long.");
                return false;
            }
            if (!password.matches("^[A-Za-z0-9]+$")) {
                ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Password must contain only letters and numbers (no special characters).");
                return false;
            }
            if (!Objects.equals(password, confirmPassword)) {
                ra.addFlashAttribute(ERROR_MESSAGE_ATTR, "Passwords do not match.");
                return false;
            }
        }
        return true;
    }

    private void refreshBrowserAuth(User user, RedirectAttributes ra, HttpServletResponse response) {
        var token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        ra.addFlashAttribute("newJwtToken", token);
        ra.addFlashAttribute("newJwtUsername", user.getUsername());
        ra.addFlashAttribute("newJwtRole", user.getRole());

        var cookie = ResponseCookie.from("jwt_token", token)
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
        var email = user.getEmail();
        var localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        var lastDot = localPart.lastIndexOf('.');
        return lastDot >= 0 ? localPart.substring(lastDot + 1) : localPart;
    }
}
