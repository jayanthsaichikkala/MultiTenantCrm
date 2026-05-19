package com.crm.demo.controller;

import com.crm.demo.model.Project;
import com.crm.demo.model.Task;
import com.crm.demo.model.User;
import com.crm.demo.repository.ProjectRepository;
import com.crm.demo.repository.TaskRepository;
import com.crm.demo.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	// =========================
	// COMMON STATS METHOD
	// =========================
	private void injectStats(HttpServletRequest request, Model model) {

		// Logged-in manager username
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

		User manager = userRepository.findByUsername(currentUsername);

		// Safety check
		if (manager == null) {
			return;
		}

		// Example:
		// manager.tcs@crm.com

		String email = manager.getEmail();

		// Extract company/tenant
		String tenantSegment = email.split("\\.")[1].split("@")[0];

		// Fetch only same company employees
		List<User> team = userRepository.findEmployeesByTenant(tenantSegment);

		List<Project> projects = projectRepository.findAll();

		List<Task> tasks = taskRepository.findAll();

		long active = team.stream().filter(User::isActive).count();

		long inactive = team.size() - active;

		long done = tasks.stream().filter(t -> "done".equalsIgnoreCase(t.getStatus())).count();

		long pending = tasks.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();

		long activeP = projects.stream().filter(p -> "active".equalsIgnoreCase(p.getStatus())).count();

		long completedP = projects.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();

		// =========================
		// MODEL ATTRIBUTES
		// =========================

		model.addAttribute("teamMembers", team);
		model.addAttribute("teamCount", team.size());
		model.addAttribute("activeTeam", active);
		model.addAttribute("inactiveTeam", inactive);

		model.addAttribute("projects", projects);
		model.addAttribute("totalProjects", projects.size());
		model.addAttribute("activeProjects", activeP);
		model.addAttribute("completedProjects", completedP);
		model.addAttribute("projectCount", projects.size());

		model.addAttribute("tasks", tasks);
		model.addAttribute("totalTasks", tasks.size());
		model.addAttribute("doneTasks", done);
		model.addAttribute("pendingTaskCount", pending);
		model.addAttribute("taskCount", tasks.size());

		model.addAttribute("overdueTasks", pending);

		model.addAttribute("notificationCount", 0);

		model.addAttribute("pendingTaskList", Collections.emptyList());
	}

	// =========================
	// DASHBOARD PAGE
	// =========================
	@GetMapping("/dashboard")
	public String dashboard(HttpServletRequest request, Model model) {

		injectStats(request, model);

		return "manager-dashboard";
	}

	// =========================
	// TEAM PAGE
	// =========================
	@GetMapping("/team")
	public String teamPage(HttpServletRequest request, Model model) {

		injectStats(request, model);

		return "manager-team";
	}
}