package com.crm.demo.controller;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {

        // Read user info injected by JwtAuthFilter
        String username = (String) request.getAttribute("loggedInUser");
        String role     = (String) request.getAttribute("loggedInRole");

        model.addAttribute("pageTitle",    "CRM — Admin Dashboard");
        model.addAttribute("pageHeading",  "Dashboard");
        model.addAttribute("pageSubtitle", "Welcome back, " + (username != null ? username : "Admin") + "!");
        model.addAttribute("activePage",   "dashboard");

        model.addAttribute("adminName",  username != null ? username : "Admin");
        model.addAttribute("adminRole",  role     != null ? role     : "Admin");
        model.addAttribute("messageCount",      3);
        model.addAttribute("notificationCount", 7);

        model.addAttribute("totalEmployees", 0);
        model.addAttribute("employeeGrowth", "+0%");
        model.addAttribute("activeProjects",  0);
        model.addAttribute("projectGrowth",  "+0%");
        model.addAttribute("tasksDone",       0);
        model.addAttribute("taskGrowth",     "+0%");
        model.addAttribute("overdueTasks",    0);
        model.addAttribute("overdueChange",  "0%");

        model.addAttribute("employeeCount", 0);
        model.addAttribute("projectCount",  0);
        model.addAttribute("pendingTasks",  0);

        model.addAttribute("completedPct",  0);
        model.addAttribute("inProgressPct", 0);
        model.addAttribute("onHoldPct",     0);

        model.addAttribute("recentActivities", Collections.emptyList());
        model.addAttribute("topEmployees",     Collections.emptyList());
        model.addAttribute("monthlyData",      Collections.emptyList());
        model.addAttribute("pendingTaskList",  Collections.emptyList());

        return "admin";
    }

    // ── Add User page (GET) ───────────────────────────────────────────────────
    @GetMapping("/add-user")
    public String addUserPage(Model model) {
        List<User> users = userRepository.findAll();
        model.addAttribute("managers",      users);
        model.addAttribute("totalManagers", users.size());
        return "add-users";
    }

    // ── Add User form submit (POST) ───────────────────────────────────────────
    @PostMapping("/add-user")
    public String addUser(@RequestParam String email,
                          @RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          @RequestParam String role,
                          Model model) {

        List<User> users = userRepository.findAll();
        model.addAttribute("managers",      users);
        model.addAttribute("totalManagers", users.size());

        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "add-users";
        }
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            model.addAttribute("errorMessage", "Username or email already exists.");
            return "add-users";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);

        // Refresh list after save
        model.addAttribute("managers",      userRepository.findAll());
        model.addAttribute("totalManagers", userRepository.findAll().size());
        model.addAttribute("successMessage", "User '" + username + "' added successfully.");
        return "add-users";
    }
}
