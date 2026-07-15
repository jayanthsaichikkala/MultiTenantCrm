package com.crm.demo.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.crm.demo.model.PasswordResetToken;
import com.crm.demo.repository.PasswordResetTokenRepository;
import com.crm.demo.repository.UserRepository;

@Controller
public class PasswordController {

	private static final String PAGE_FORGOT_PASSWORD = "forgot-password";
	private static final String PAGE_RESET_PASSWORD = "reset-password";
	private static final String ATTR_ERROR = "error";
	private static final String ATTR_TOKEN = "token";

	private final JavaMailSender mailSender;
	private final PasswordResetTokenRepository tokenRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public PasswordController(JavaMailSender mailSender,
							  PasswordResetTokenRepository tokenRepository,
							  UserRepository userRepository,
							  PasswordEncoder passwordEncoder) {
		this.mailSender = mailSender;
		this.tokenRepository = tokenRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	// Forgot Password Page
	@GetMapping("/forgot-password")
	public String forgotPasswordPage() {
		return PAGE_FORGOT_PASSWORD;
	}

	// Process Forgot Password
	@PostMapping("/forgot-password")
	public String processForgotPassword(@RequestParam String email, Model model, RedirectAttributes ra) {
		if (email == null || email.trim().isBlank()) {
			model.addAttribute(ATTR_ERROR, "Email is required");
			return PAGE_FORGOT_PASSWORD;
		}
		if (!email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
			model.addAttribute(ATTR_ERROR, "Please provide a valid email address");
			return PAGE_FORGOT_PASSWORD;
		}

		var user = userRepository.findByEmail(email.trim());

		// Check if user exists
		if (user == null) {
			model.addAttribute(ATTR_ERROR, "User not found");
			return PAGE_FORGOT_PASSWORD;
		}

		// Generate token
		var token = UUID.randomUUID().toString();

		// Delete old token if exists
		var existingToken = tokenRepository.findByUser(user);

		existingToken.ifPresent(tokenRepository::delete);

		// Create new token
		var resetToken = new PasswordResetToken();

		resetToken.setToken(token);
		resetToken.setUser(user);
		resetToken.setExpiryTime(LocalDateTime.now().plusMinutes(10));

		tokenRepository.save(resetToken);

		// Reset link
		var resetLink = "http://localhost:8080/reset-password?token=" + token;

		// Send email
		var message = new SimpleMailMessage();

		message.setFrom("CRM ADMIN <vishnumatamala@gmail.com>");
		message.setTo(user.getEmail());
		message.setSubject("Password Reset Request");

		message.setText("Hello " + user.getUsername() + ",\n\n" + "Click the below link to reset your password:\n\n"
				+ resetLink + "\n\nThis link will expire in 10 minutes.");

		mailSender.send(message);

		ra.addFlashAttribute("message", "Password reset link sent to your email");

		return "redirect:/forgot-password";
	}

	// Reset Password Page
	@GetMapping("/reset-password")
	public String resetPasswordPage(@RequestParam String token, Model model) {

		var opt = tokenRepository.findByToken(token);
		if (opt.isEmpty()) {
			model.addAttribute(ATTR_ERROR, "Invalid or expired reset link. Please request a new one.");
			return PAGE_FORGOT_PASSWORD;
		}

		var resetToken = opt.get();

		// Check token expiry
		if (resetToken.getExpiryTime().isBefore(LocalDateTime.now())) {
			tokenRepository.delete(resetToken);
			model.addAttribute(ATTR_ERROR, "Reset link has expired. Please request a new one.");
			return PAGE_FORGOT_PASSWORD;
		}

		model.addAttribute(ATTR_TOKEN, token);

		return PAGE_RESET_PASSWORD;
	}

	// Reset Password Logic
	@PostMapping("/reset-password")
	public String resetPassword(@RequestParam String token, @RequestParam String password,
			@RequestParam String confirmPassword, Model model) {

		if (password == null || password.length() < 4) {
			model.addAttribute(ATTR_ERROR, "Password must be at least 4 characters long.");
			model.addAttribute(ATTR_TOKEN, token);
			return PAGE_RESET_PASSWORD;
		}
		if (!password.matches("^[A-Za-z0-9]+$")) {
			model.addAttribute(ATTR_ERROR, "Password must contain only letters and numbers (no special characters).");
			model.addAttribute(ATTR_TOKEN, token);
			return PAGE_RESET_PASSWORD;
		}

		// Password match validation
		if (!password.equals(confirmPassword)) {

			model.addAttribute(ATTR_ERROR, "Passwords do not match");

			model.addAttribute(ATTR_TOKEN, token);

			return PAGE_RESET_PASSWORD;
		}

		var opt = tokenRepository.findByToken(token);
		if (opt.isEmpty()) {
			model.addAttribute(ATTR_ERROR, "Invalid or expired reset link. Please request a new one.");
			return PAGE_FORGOT_PASSWORD;
		}

		var resetToken = opt.get();

		// Check expiry again
		if (resetToken.getExpiryTime().isBefore(LocalDateTime.now())) {
			tokenRepository.delete(resetToken);
			model.addAttribute(ATTR_ERROR, "Reset link has expired. Please request a new one.");
			return PAGE_FORGOT_PASSWORD;
		}

		var user = resetToken.getUser();

		// Encode password
		var encodedPassword = passwordEncoder.encode(password);

		user.setPassword(encodedPassword);

		userRepository.save(user);

		// Delete token after reset
		tokenRepository.delete(resetToken);

		return "redirect:/login";
	}
}