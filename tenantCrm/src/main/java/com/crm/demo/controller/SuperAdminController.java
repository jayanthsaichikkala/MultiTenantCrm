package com.crm.demo.controller;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired private UserRepository        userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @GetMapping
    public String dashboard(HttpServletRequest request, Model model) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();

        model.addAttribute("admins",       admins);
        model.addAttribute("totalAdmins",  admins.size());
        model.addAttribute("activeAdmins", admins.size());
        model.addAttribute("todayAdmins",  0);
        return "superadmin";
    }

    @PostMapping("/add-admin")
    public String addAdmin(@RequestParam String email,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           Model model) {

        List<User> admins = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();
        model.addAttribute("admins",       admins);
        model.addAttribute("totalAdmins",  admins.size());
        model.addAttribute("activeAdmins", admins.size());
        model.addAttribute("todayAdmins",  0);

        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "superadmin";
        }
        if (userRepository.findByUsernameOrEmail(username, email) != null) {
            model.addAttribute("errorMessage", "Username or email already exists.");
            return "superadmin";
        }

        User newAdmin = new User();
        newAdmin.setUsername(username);
        newAdmin.setEmail(email);
        newAdmin.setPassword(passwordEncoder.encode(password));
        newAdmin.setRole("ADMIN");
        userRepository.save(newAdmin);

        model.addAttribute("successMessage", "Admin '" + username + "' added successfully.");
        return "superadmin";
    }
}
