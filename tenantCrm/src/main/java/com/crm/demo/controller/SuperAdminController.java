package com.crm.demo.controller;

import java.util.List;
import java.util.Map;
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

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private ProfileUpdateService  profileUpdateService;

    // ── helper: load admins and stats into model ─────────────────────────────
    private List<User> loadAdmins(Model model) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .sorted(java.util.Comparator.comparing(User::getId).reversed())
                .toList();
        long activeCount = admins.stream().filter(User::isActive).count();
        model.addAttribute("admins",       admins);
        model.addAttribute("totalAdmins",  admins.size());
        model.addAttribute("activeAdmins", activeCount);
        model.addAttribute("todayAdmins",  0);
        return admins;
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
        List<User> allUsers = userRepository.findAll();
        List<User> admins   = allUsers.stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        long activeAdmins   = admins.stream().filter(User::isActive).count();
        long inactiveAdmins = admins.size() - activeAdmins;

        // Role distribution across ALL users
        Map<String, Long> roleDistribution = allUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRole() == null ? "UNKNOWN" : u.getRole().toUpperCase(),
                        Collectors.counting()));

        // Admin growth simulation: bucket admins into 6 groups by ID range
        // (approximates "added over time" without a createdAt column)
        int total = admins.size();
        int[] growthData = new int[6];
        if (total > 0) {
            long minId = admins.stream().mapToLong(User::getId).min().orElse(1);
            long maxId = admins.stream().mapToLong(User::getId).max().orElse(1);
            long range = Math.max(maxId - minId, 1);
            for (User a : admins) {
                int bucket = (int) Math.min(5, (a.getId() - minId) * 6 / range);
                growthData[bucket]++;
            }
        }

        // Status breakdown per "simulated month" (last 6 months labels)
        java.time.LocalDate now = java.time.LocalDate.now();
        String[] monthLabels = new String[6];
        for (int i = 5; i >= 0; i--) {
            monthLabels[5 - i] = now.minusMonths(i)
                    .getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                            java.util.Locale.ENGLISH);
        }

        // Active vs Inactive per bucket (for stacked bar)
        int[] activePerMonth   = new int[6];
        int[] inactivePerMonth = new int[6];
        if (total > 0) {
            long minId = admins.stream().mapToLong(User::getId).min().orElse(1);
            long maxId = admins.stream().mapToLong(User::getId).max().orElse(1);
            long range = Math.max(maxId - minId, 1);
            for (User a : admins) {
                int bucket = (int) Math.min(5, (a.getId() - minId) * 6 / range);
                if (a.isActive()) activePerMonth[bucket]++;
                else              inactivePerMonth[bucket]++;
            }
        }

        model.addAttribute("activeAdminsCount",   activeAdmins);
        model.addAttribute("inactiveAdminsCount",  inactiveAdmins);
        model.addAttribute("roleDistribution",     roleDistribution);
        model.addAttribute("growthData",           growthData);
        model.addAttribute("monthLabels",          monthLabels);
        model.addAttribute("activePerMonth",       activePerMonth);
        model.addAttribute("inactivePerMonth",     inactivePerMonth);
        model.addAttribute("totalUsers",           allUsers.size());

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));

        return "superadmin-dashboard";
    }

    // ── Admins list page ──────────────────────────────────────────────────────
    @GetMapping("/admins")
    public String adminsPage(Model model) {
        loadAdmins(model);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));
        return "superadmin-admins";
    }

    // ── Add Admin page (GET) ──────────────────────────────────────────────────
    @GetMapping("/add-admin")
    public String addAdminPage(Model model) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));
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

        if (username == null || username.trim().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Username is required.");
            return "redirect:/superadmin/add-admin";
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            ra.addFlashAttribute("errorMessage", "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.");
            return "redirect:/superadmin/add-admin";
        }
        if (email == null || email.trim().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Email is required.");
            return "redirect:/superadmin/add-admin";
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            ra.addFlashAttribute("errorMessage", "Please provide a valid email address.");
            return "redirect:/superadmin/add-admin";
        }
        // Admin email must contain a dot in local part to establish a tenant segment
        String local = email.trim().substring(0, email.trim().indexOf('@'));
        if (local.lastIndexOf('.') < 0) {
            ra.addFlashAttribute("errorMessage", "Admin email must follow the pattern name.tenant@domain.com (e.g. admin.wipro@crm.com) to establish a tenant segment.");
            return "redirect:/superadmin/add-admin";
        }
        if (password == null || password.length() < 4) {
            ra.addFlashAttribute("errorMessage", "Password must be at least 4 characters long.");
            return "redirect:/superadmin/add-admin";
        }
        if (!password.matches("^[A-Za-z0-9]+$")) {
            ra.addFlashAttribute("errorMessage", "Password must contain only letters and numbers (no special characters).");
            return "redirect:/superadmin/add-admin";
        }
        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "Passwords do not match.");
            return "redirect:/superadmin/add-admin";
        }
        if (employeeLimit == null || employeeLimit < 1) {
            ra.addFlashAttribute("errorMessage", "Employee limit must be a positive integer.");
            return "redirect:/superadmin/add-admin";
        }

        if (userRepository.existsByUsernameOrEmail(username.trim(), email.trim())) {
            ra.addFlashAttribute("errorMessage", "Username or email already exists.");
            return "redirect:/superadmin/add-admin";
        }

        User newAdmin = new User();
        newAdmin.setUsername(username.trim());
        newAdmin.setEmail(email.trim());
        newAdmin.setPassword(passwordEncoder.encode(password));
        newAdmin.setRole("ADMIN");
        newAdmin.setStatus(status);
        newAdmin.setEmployeeLimit(employeeLimit);
        userRepository.save(newAdmin);

        ra.addFlashAttribute("successMessage", "Admin '" + username.trim() + "' added successfully.");
        return "redirect:/superadmin/admins";
    }

    private String getTenantSegment(String email) {
        if (email == null) return "";
        try {
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            int dot = local.lastIndexOf('.');
            return dot >= 0 ? local.substring(dot + 1) : local;
        } catch (Exception e) {
            return "";
        }
    }

    // ── Edit Admin (GET) ──────────────────────────────────────────────────────
    @GetMapping("/edit-admin/{id}")
    public String editAdminPage(@PathVariable Long id, Model model) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            return "redirect:/superadmin/admins";
        }
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        model.addAttribute("superAdminUser", userRepository.findByUsername(currentUsername));
        model.addAttribute("editAdmin", admin);

        // Fetch employees under this admin's tenant segment
        String tenant = getTenantSegment(admin.getEmail());
        List<User> employees = userRepository.findByTenantSegment(tenant).stream()
                .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole()) && !"SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
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

        User admin = userRepository.findById(id).orElse(null);
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            return "redirect:/superadmin/admins";
        }

        if (username == null || username.trim().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Username is required.");
            return "redirect:/superadmin/edit-admin/" + id;
        }
        if (!username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            ra.addFlashAttribute("errorMessage", "Username must be 3-50 characters and contain only letters, numbers, dots, hyphens, or underscores.");
            return "redirect:/superadmin/edit-admin/" + id;
        }
        if (email == null || email.trim().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Email is required.");
            return "redirect:/superadmin/edit-admin/" + id;
        }
        if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
            ra.addFlashAttribute("errorMessage", "Please provide a valid email address.");
            return "redirect:/superadmin/edit-admin/" + id;
        }
        // Admin email must contain a dot in local part to establish a tenant segment
        String local = email.trim().substring(0, email.trim().indexOf('@'));
        if (local.lastIndexOf('.') < 0) {
            ra.addFlashAttribute("errorMessage", "Admin email must follow the pattern name.tenant@domain.com (e.g. admin.wipro@crm.com) to establish a tenant segment.");
            return "redirect:/superadmin/edit-admin/" + id;
        }
        if (employeeLimit == null || employeeLimit < 1) {
            ra.addFlashAttribute("errorMessage", "Employee limit must be a positive integer.");
            return "redirect:/superadmin/edit-admin/" + id;
        }

        // Check uniqueness
        User existingUserByUname = userRepository.findByUsername(username.trim());
        if (existingUserByUname != null && !existingUserByUname.getId().equals(admin.getId())) {
            ra.addFlashAttribute("errorMessage", "Username is already taken.");
            return "redirect:/superadmin/edit-admin/" + id;
        }
        User existingUserByEmail = userRepository.findByEmail(email.trim());
        if (existingUserByEmail != null && !existingUserByEmail.getId().equals(admin.getId())) {
            ra.addFlashAttribute("errorMessage", "Email is already taken.");
            return "redirect:/superadmin/edit-admin/" + id;
        }

        admin.setUsername(username.trim());
        admin.setEmail(email.trim());
        admin.setStatus(status);
        admin.setEmployeeLimit(employeeLimit);
        userRepository.save(admin);

        ra.addFlashAttribute("successMessage", "Admin '" + admin.getUsername() + "' updated successfully.");
        return "redirect:/superadmin/admins";
    }

    // ── Toggle Admin Status ───────────────────────────────────────────────────
    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin != null && "ADMIN".equalsIgnoreCase(admin.getRole())) {
            String newStatus = "active".equalsIgnoreCase(admin.getStatus()) ? "inactive" : "active";
            admin.setStatus(newStatus);
            userRepository.save(admin);
            ra.addFlashAttribute("successMessage",
                "Admin '" + admin.getUsername() + "' is now " + newStatus + ".");
        }
        return "redirect:/superadmin/admins";
    }

    // ── Delete Admin ──────────────────────────────────────────────────────────
    @PostMapping("/delete-admin/{id}")
    public String deleteAdmin(@PathVariable Long id, RedirectAttributes ra) {
        User admin = userRepository.findById(id).orElse(null);
        if (admin != null && "ADMIN".equalsIgnoreCase(admin.getRole())) {
            String name = admin.getUsername();
            userRepository.delete(admin);
            ra.addFlashAttribute("successMessage", "Admin '" + name + "' deleted successfully.");
        }
        return "redirect:/superadmin/admins";
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
        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User superAdmin = userRepository.findByUsername(currentUsername);
        model.addAttribute("superAdminUser", superAdmin);
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

        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User superAdmin = userRepository.findByUsername(currentUsername);
        if (superAdmin == null) {
            return "redirect:/superadmin/profile";
        }

        profileUpdateService.updateProfile(superAdmin, username, email, password, confirmPassword, ra, response);
        return "redirect:/superadmin/profile";
    }
}
