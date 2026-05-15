package com.crm.demo.controller;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/manager")
public class ManagerController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // ─── DASHBOARD ────────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        populateCommonModel(model);
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageHeading", "Dashboard");
        model.addAttribute("pageSubtitle", "Here's what's happening with your team today.");
        model.addAttribute("pendingTaskList", Collections.emptyList());
        return "manager";
    }

    // ─── ADD MANAGER PAGE (GET) ───────────────────────────────────────────────────
    @GetMapping("/add")
    public String addManagerPage(Model model) {
        List<User> managers = userRepository.findAll()
                .stream()
                .filter(u -> "MANAGER".equalsIgnoreCase(u.getRole()))
                .toList();

        model.addAttribute("managers", managers);
        model.addAttribute("totalManagers", managers.size());
        return "add-manager";
    }

    // ─── ADD MANAGER (POST) ───────────────────────────────────────────────────────
    @PostMapping("/add")
    public String addManager(@RequestParam String email,
                             @RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String confirmPassword,
                             Model model) {

        List<User> managers = userRepository.findAll()
                .stream()
                .filter(u -> "MANAGER".equalsIgnoreCase(u.getRole()))
                .toList();
        model.addAttribute("managers", managers);
        model.addAttribute("totalManagers", managers.size());

        // Validate passwords match
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "add-manager";
        }

        // Check for duplicate username or email
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            model.addAttribute("errorMessage", "Username or email already exists.");
            return "add-manager";
        }

        // Save with BCrypt-hashed password
        User manager = new User();
        manager.setUsername(username);
        manager.setEmail(email);
        manager.setPassword(passwordEncoder.encode(password));   // ← BCrypt hash
        manager.setRole("MANAGER");

        userRepository.save(manager);

        model.addAttribute("successMessage", "Manager '" + username + "' added successfully.");
        return "add-manager";
    }

    // ─── HELPER ───────────────────────────────────────────────────────────────────
    private void populateCommonModel(Model model) {
        model.addAttribute("managerName", "Manager");
        model.addAttribute("notificationCount", 0);
        model.addAttribute("teamCount", 0);
        model.addAttribute("projectCount", 0);
        model.addAttribute("taskCount", 0);
        model.addAttribute("overdueTasks", 0);
        model.addAttribute("teamGrowth", "+0%");
        model.addAttribute("projectGrowth", "+0%");
        model.addAttribute("taskGrowth", "+0%");
        model.addAttribute("overdueChange", "0%");
        model.addAttribute("teamMembers", Collections.emptyList());
    }
}
