package com.crm.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // Page meta
        model.addAttribute("pageTitle", "CRM — Admin Dashboard");
        model.addAttribute("pageHeading", "Dashboard");
        model.addAttribute("pageSubtitle", "Welcome back, Admin!");
        model.addAttribute("activePage", "dashboard");

        // Topbar
        model.addAttribute("adminName", "Admin User");
        model.addAttribute("adminRole", "Admin");
        model.addAttribute("messageCount", 3);
        model.addAttribute("notificationCount", 7);

        // Stat cards
        model.addAttribute("totalEmployees", 0);
        model.addAttribute("employeeGrowth", "+0%");
        model.addAttribute("activeProjects", 0);
        model.addAttribute("projectGrowth", "+0%");
        model.addAttribute("tasksDone", 0);
        model.addAttribute("taskGrowth", "+0%");
        model.addAttribute("overdueTasks", 0);
        model.addAttribute("overdueChange", "0%");

        // Sidebar badges
        model.addAttribute("employeeCount", 0);
        model.addAttribute("projectCount", 0);
        model.addAttribute("pendingTasks", 0);

        // Donut chart
        model.addAttribute("completedPct", 0);
        model.addAttribute("inProgressPct", 0);
        model.addAttribute("onHoldPct", 0);

        // Empty lists — extend these when you wire up real data
        model.addAttribute("recentActivities", java.util.Collections.emptyList());
        model.addAttribute("topEmployees", java.util.Collections.emptyList());
        model.addAttribute("monthlyData", java.util.Collections.emptyList());
        model.addAttribute("pendingTaskList", java.util.Collections.emptyList());

        return "admin";
    }
}
