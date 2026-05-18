package com.crm.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;

@Controller
@RequestMapping("/hr")
public class HrController {

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {

        String username = (String) request.getAttribute("loggedInUser");

        model.addAttribute("adminName",   username != null ? username : "HR User");
        model.addAttribute("adminRole",   "HR");
        model.addAttribute("adminAvatar",
                "https://ui-avatars.com/api/?background=0D6EFD&color=fff&name="
                + (username != null ? username.replace(" ", "+") : "HR"));
        model.addAttribute("notificationCount", 0);
        model.addAttribute("notifications",     Collections.emptyList());

        model.addAttribute("totalEmployees",  0);
        model.addAttribute("employeesTrend", "+0%");
        model.addAttribute("newHires",        0);
        model.addAttribute("hiresTrend",     "+0%");
        model.addAttribute("onLeaveToday",    0);
        model.addAttribute("leaveTrend",     "0 pending");
        model.addAttribute("openPositions",   0);
        model.addAttribute("positionsTrend", "+0");
        model.addAttribute("hiresGrowth",    "0%");

        model.addAttribute("recentApplicants", Collections.emptyList());
        model.addAttribute("upcomingEvents",   Collections.emptyList());
        model.addAttribute("leaveRequests",    Collections.emptyList());
        model.addAttribute("employees",        Collections.emptyList());

        model.addAttribute("attendanceMonth", "May 2026");
        model.addAttribute("presentPercent",  "0%");
        model.addAttribute("absentPercent",   "0%");
        model.addAttribute("wfhPercent",      "0%");

        return "hr";
    }
}
