package com.springboot1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

	// Serves GET /login → templates/login.html
	@GetMapping("/login")
	public String login() {
		return "login";
	}

	// Redirect root "/" to admin dashboard
	@GetMapping("/")
	public String root() {
		return "redirect:/admin/dashboard";
	}
}