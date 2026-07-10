package com.crm.demo.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.ProfileUpdateService;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ATTR_SUPER_ADMIN_USER = "superAdminUser";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_SUPERADMIN_ADD_ADMIN = "redirect:/superadmin/add-admin";
    private static final String REDIRECT_SUPERADMIN_ADMINS = "redirect:/superadmin/admins";
    private static final String REDIRECT_SUPERADMIN_EDIT_ADMIN_PREFIX = "redirect:/superadmin/edit-admin/";
    private static final String MSG_ADMIN_PREFIX = "Admin '";

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private ProfileUpdateService  profileUpdateService;

    // ── helper: load admins and stats into model ─────────────────────────────
    private List<User> loadAdmins(Model model) {
        var admins = userRepository.findAll().stream()
                .filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
                .sorted(java.util.Comparator.comparing(User::getId).reversed())
                .collect(Collectors.toList());
        var activeCount = admins.stream().filter(User::isActive).count();
        model.addAttribute("admins",       admins);
        model.addAttribute("totalAdmins",  admins.size());
        model.addAttribute("activeAdmins", activeCount);
        model.addAttribute("todayAdmins",  0);
        return admins;
    }

    private String validateAdminParams(String username, String email, Integer employeeLimit) {
        if (username == null || username.trim().isBlank()) {
            return "Username is required.";
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            return "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.";
        }
        if (email == null || email.trim().isBlank()) {
            return "Email is required.";
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            return "Please provide a valid email address.";
        }
        // Admin email must contain a dot in local part to establish a tenant segment
        var local = email.trim().substring(0, email.trim().indexOf('@'));
        if (local.lastIndexOf('.') < 0) {
            return "Admin email must follow the pattern name.tenant@domain.com (e.g. admin.wipro@crm.com) to establish a tenant segment.";
        }
        if (employeeLimit == null || employeeLimit < 1) {
            return "Employee limit must be a positive integer.";
        }
        return null;
    }

    // ── Dashboard (default page) ──────────────────────────────────────────────
    @GetMapping
    public String dashboardRoot(Model model) {
        return "redirect:/superadmin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        loadAdmins(model);

        // ── Analytics data ────────────────────────────────────────────────────
        var allUsers = userRepository.findAll();
        var admins   = allUsers.stream()
                .filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        var activeAdmins   = admins.stream().filter(User::isActive).count();
        var inactiveAdmins = admins.size() - activeAdmins;

        // Role distribution across ALL users
        var roleDistribution = allUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRole() == null ? "UNKNOWN" : u.getRole().toUpperCase(),
                        Collectors.counting()));

        // Growth & Active/Inactive breakdown per bucket (simulated months)
        var total = admins.size();
        var growthData = new int[6];
        var activePerMonth   = new int[6];
        var inactivePerMonth = new int[6];

        if (total > 0) {
            var minId = admins.stream().mapToLong(User::getId).min().orElse(1);
            var maxId = admins.stream().mapToLong(User::getId).max().orElse(1);
            var range = Math.max(maxId - minId, 1);
            for (var a : admins) {
                var bucket = (int) Math.min(5, (a.getId() - minId) * 6 / range);
                growthData[bucket]++;
                if (a.isActive()) {
                    activePerMonth[bucket]++;
                } else {
                    inactivePerMonth[bucket]++;
                }
            }
        }

        // Status breakdown per "simulated month" (last 6 months labels)
        var now = java.time.LocalDate.now();
        var monthLabels = new String[6];
        for (var i = 5; i >= 0; i--) {
            monthLabels[5 - i] = now.minusMonths(i)
                    .getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                            java.util.Locale.ENGLISH);
        }

        model.addAttribute("activeAdminsCount",   activeAdmins);
        model.addAttribute("inactiveAdminsCount",  inactiveAdmins);
        model.addAttribute("roleDistribution",     roleDistribution);
        model.addAttribute("growthData",           growthData);
        model.addAttribute("monthLabels",          monthLabels);
        model.addAttribute("activePerMonth",       activePerMonth);
        model.addAttribute("inactivePerMonth",     inactivePerMonth);
        model.addAttribute("totalUsers",           allUsers.size());

        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute(ATTR_SUPER_ADMIN_USER, userRepository.findByUsername(currentUsername));

        return "superadmin-dashboard";
    }

    // ── Admins list page ──────────────────────────────────────────────────────
    @GetMapping("/admins")
    public String adminsPage(Model model) {
        loadAdmins(model);
        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute(ATTR_SUPER_ADMIN_USER, userRepository.findByUsername(currentUsername));
        return "superadmin-admins";
    }

    // ── Add Admin page (GET) ──────────────────────────────────────────────────
    @GetMapping("/add-admin")
    public String addAdminPage(Model model) {
        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute(ATTR_SUPER_ADMIN_USER, userRepository.findByUsername(currentUsername));
        return "superadmin-add-admin";
    }

    // ── Add Admin (POST) ──────────────────────────────────────────────────────
    @PostMapping("/add-admin")
    public String addAdmin(@RequestParam String email,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam(defaultValue = "active") String status,
                           @RequestParam(defaultValue = "10") Integer employeeLimit,
                           RedirectAttributes ra) {

        var validationError = validateAdminParams(username, email, employeeLimit);
        if (validationError != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
            return REDIRECT_SUPERADMIN_ADD_ADMIN;
        }

        if (password == null || password.length() < 4) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Password must be at least 4 characters long.");
            return REDIRECT_SUPERADMIN_ADD_ADMIN;
        }
        if (!password.matches("^[A-Za-z0-9]+$")) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Password must contain only letters and numbers (no special characters).");
            return REDIRECT_SUPERADMIN_ADD_ADMIN;
        }
        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Passwords do not match.");
            return REDIRECT_SUPERADMIN_ADD_ADMIN;
        }

        if (userRepository.existsByUsernameOrEmail(username.trim(), email.trim())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Username or email already exists.");
            return REDIRECT_SUPERADMIN_ADD_ADMIN;
        }

        var newAdmin = new User();
        newAdmin.setUsername(username.trim());
        newAdmin.setEmail(email.trim());
        newAdmin.setPassword(passwordEncoder.encode(password));
        newAdmin.setRole(ROLE_ADMIN);
        newAdmin.setStatus(status);
        newAdmin.setEmployeeLimit(employeeLimit);
        userRepository.save(newAdmin);

        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, MSG_ADMIN_PREFIX + username.trim() + "' added successfully.");
        return REDIRECT_SUPERADMIN_ADMINS;
    }

    private String getTenantSegment(String email) {
        if (email == null) return "";
        try {
            var local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            var dot = local.lastIndexOf('.');
            return dot >= 0 ? local.substring(dot + 1) : local;
        } catch (Exception e) {
            return "";
        }
    }

    // ── Edit Admin (GET) ──────────────────────────────────────────────────────
    @GetMapping("/edit-admin/{id}")
    public String editAdminPage(@PathVariable Long id, Model model) {
        var admin = userRepository.findById(id).orElse(null);
        if (admin == null || !ROLE_ADMIN.equalsIgnoreCase(admin.getRole())) {
            return REDIRECT_SUPERADMIN_ADMINS;
        }
        var currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute(ATTR_SUPER_ADMIN_USER, userRepository.findByUsername(currentUsername));
        model.addAttribute("editAdmin", admin);

        // Fetch employees under this admin's tenant segment
        var tenant = getTenantSegment(admin.getEmail());
        var employees = userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> !ROLE_ADMIN.equalsIgnoreCase(u.getRole()) && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("employees", employees);

        return "superadmin-edit-admin";
    }

    // ── Edit Admin (POST) ──────────────────────────────────────────────────────
    @PostMapping("/edit-admin/{id}")
    public String editAdmin(@PathVariable Long id,
                            @RequestParam String email,
                            @RequestParam String username,
                            @RequestParam(defaultValue = "active") String status,
                            @RequestParam(defaultValue = "10") Integer employeeLimit,
                            RedirectAttributes ra) {

        var admin = userRepository.findById(id).orElse(null);
        if (admin == null || !ROLE_ADMIN.equalsIgnoreCase(admin.getRole())) {
            return REDIRECT_SUPERADMIN_ADMINS;
        }

        var validationError = validateAdminParams(username, email, employeeLimit);
        if (validationError != null) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, validationError);
            return REDIRECT_SUPERADMIN_EDIT_ADMIN_PREFIX + id;
        }

        // Check uniqueness
        var existingUserByUname = userRepository.findByUsername(username.trim());
        if (existingUserByUname != null && !existingUserByUname.getId().equals(admin.getId())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Username is already taken.");
            return REDIRECT_SUPERADMIN_EDIT_ADMIN_PREFIX + id;
        }
        var existingUserByEmail = userRepository.findByEmail(email.trim());
        if (existingUserByEmail != null && !existingUserByEmail.getId().equals(admin.getId())) {
            ra.addFlashAttribute(ATTR_ERROR_MESSAGE, "Email is already taken.");
            return REDIRECT_SUPERADMIN_EDIT_ADMIN_PREFIX + id;
        }

        admin.setUsername(username.trim());
        admin.setEmail(email.trim());
        admin.setStatus(status);
        admin.setEmployeeLimit(employeeLimit);
        userRepository.save(admin);

        ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, MSG_ADMIN_PREFIX + admin.getUsername() + "' updated successfully.");
        return REDIRECT_SUPERADMIN_ADMINS;
    }

    // ── Toggle Admin Status ───────────────────────────────────────────────────
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        var admin = userRepository.findById(id).orElse(null);
        if (admin != null && ROLE_ADMIN.equalsIgnoreCase(admin.getRole())) {
            var newStatus = "active".equalsIgnoreCase(admin.getStatus()) ? "inactive" : "active";
            admin.setStatus(newStatus);
            userRepository.save(admin);
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                MSG_ADMIN_PREFIX + admin.getUsername() + "' is now " + newStatus + ".");
        }
        return REDIRECT_SUPERADMIN_ADMINS;
    }

    // ── Delete Admin ──────────────────────────────────────────────────────────
    @PostMapping("/delete-admin/{id}")
    public String deleteAdmin(@PathVariable Long id, RedirectAttributes ra) {
        var admin = userRepository.findById(id).orElse(null);
        if (admin != null && ROLE_ADMIN.equalsIgnoreCase(admin.getRole())) {
            var name = admin.getUsername();
            userRepository.delete(admin);
            ra.addFlashAttribute(ATTR_SUCCESS_MESSAGE, MSG_ADMIN_PREFIX + name + "' deleted successfully.");
        }
        return REDIRECT_SUPERADMIN_ADMINS;
    }

    // ── Views page — removed, redirect to dashboard ──────────────────────────
    @GetMapping("/views")
    public String viewsPage() {
        return "redirect:/superadmin/dashboard";
    }

    // ── Profile page ──────────────────────────────────────────────────────────
    @GetMapping("/profile")
    public String profilePage(Model model) {
        loadAdmins(model);
        var currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        var superAdmin = userRepository.findByUsername(currentUsername);
        model.addAttribute(ATTR_SUPER_ADMIN_USER, superAdmin);
        return "superadmin-profile";
    }

    // ── Update Profile (POST) ─────────────────────────────────────────────────
    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam(required = false) String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                HttpServletResponse response,
                                RedirectAttributes ra) {

        var currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        var superAdmin = userRepository.findByUsername(currentUsername);
        if (superAdmin == null) {
            return "redirect:/superadmin/profile";
        }

        profileUpdateService.updateProfile(superAdmin, username, email, password, confirmPassword, ra, response);
        return "redirect:/superadmin/profile";
    }
}
