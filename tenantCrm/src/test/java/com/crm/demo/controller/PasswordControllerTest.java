package com.crm.demo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.crm.demo.model.PasswordResetToken;
import com.crm.demo.model.User;
import com.crm.demo.repository.PasswordResetTokenRepository;
import com.crm.demo.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    private MockMvc mockMvc;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordController passwordController;

    private User testUser;

    @BeforeEach
    void setUp() {
        org.springframework.web.servlet.view.InternalResourceViewResolver viewResolver = 
                new org.springframework.web.servlet.view.InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(passwordController)
                .setViewResolvers(viewResolver)
                .build();

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@crm.com");
        testUser.setPassword("oldPasswordHash");
    }

    @Test
    @DisplayName("Should return forgot password page view")
    void shouldReturnForgotPasswordPageView() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"));
    }

    @Test
    @DisplayName("Should return error when email is null or empty in forgot password processing")
    void shouldReturnErrorWhenEmailIsNullOrEmpty() throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "Email is required"));
    }

    @Test
    @DisplayName("Should return error when email has invalid format in forgot password processing")
    void shouldReturnErrorWhenEmailHasInvalidFormat() throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", "invalidEmail"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "Please provide a valid email address"));
    }

    @Test
    @DisplayName("Should return error when user is not found in forgot password processing")
    void shouldReturnErrorWhenUserNotFound() throws Exception {
        when(userRepository.findByEmail("nonexistent@crm.com")).thenReturn(null);

        mockMvc.perform(post("/forgot-password").param("email", "nonexistent@crm.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "User not found"));

        verify(userRepository).findByEmail("nonexistent@crm.com");
    }

    @Test
    @DisplayName("Should process forgot password successfully and delete old token if exists")
    void shouldProcessForgotPasswordSuccessfullyWithExistingToken() throws Exception {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);

        PasswordResetToken oldToken = new PasswordResetToken();
        oldToken.setId(99L);
        oldToken.setToken("old-token-uuid");
        oldToken.setUser(testUser);

        when(tokenRepository.findByUser(testUser)).thenReturn(Optional.of(oldToken));

        mockMvc.perform(post("/forgot-password").param("email", testUser.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attribute("message", "Password reset link sent to your email"));

        verify(userRepository).findByEmail(testUser.getEmail());
        verify(tokenRepository).findByUser(testUser);
        verify(tokenRepository).delete(oldToken);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should process forgot password successfully when no prior token exists")
    void shouldProcessForgotPasswordSuccessfullyWithoutExistingToken() throws Exception {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);
        when(tokenRepository.findByUser(testUser)).thenReturn(Optional.empty());

        mockMvc.perform(post("/forgot-password").param("email", testUser.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attribute("message", "Password reset link sent to your email"));

        verify(userRepository).findByEmail(testUser.getEmail());
        verify(tokenRepository).findByUser(testUser);
        verify(tokenRepository, never()).delete(any(PasswordResetToken.class));
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should return error when token is invalid or missing in reset password page")
    void shouldReturnErrorWhenResetTokenNotFound() throws Exception {
        String token = "invalid-token";
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "Invalid or expired reset link. Please request a new one."));

        verify(tokenRepository).findByToken(token);
    }

    @Test
    @DisplayName("Should delete token and return error when reset token is expired in reset password page")
    void shouldDeleteTokenAndReturnErrorWhenTokenExpiredOnGet() throws Exception {
        String token = "expired-token";
        PasswordResetToken expiredToken = new PasswordResetToken();
        expiredToken.setToken(token);
        expiredToken.setUser(testUser);
        expiredToken.setExpiryTime(LocalDateTime.now().minusMinutes(1)); // Expired

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(expiredToken));

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "Reset link has expired. Please request a new one."));

        verify(tokenRepository).findByToken(token);
        verify(tokenRepository).delete(expiredToken);
    }

    @Test
    @DisplayName("Should display reset password page when token is valid and not expired")
    void shouldDisplayResetPasswordPageWhenTokenIsValid() throws Exception {
        String token = "valid-token";
        PasswordResetToken validToken = new PasswordResetToken();
        validToken.setToken(token);
        validToken.setUser(testUser);
        validToken.setExpiryTime(LocalDateTime.now().plusMinutes(10)); // Active

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(validToken));

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attribute("token", token));

        verify(tokenRepository).findByToken(token);
        verify(tokenRepository, never()).delete(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Should return error when password is null or less than 4 characters in reset password processing")
    void shouldReturnErrorWhenPasswordIsTooShort() throws Exception {
        mockMvc.perform(post("/reset-password")
                .param("token", "token-uuid")
                .param("password", "abc")
                .param("confirmPassword", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attribute("error", "Password must be at least 4 characters long."))
                .andExpect(model().attribute("token", "token-uuid"));
    }

    @Test
    @DisplayName("Should return error when password has special characters in reset password processing")
    void shouldReturnErrorWhenPasswordContainsSpecialCharacters() throws Exception {
        mockMvc.perform(post("/reset-password")
                .param("token", "token-uuid")
                .param("password", "pass123!")
                .param("confirmPassword", "pass123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attribute("error", "Password must contain only letters and numbers (no special characters)."))
                .andExpect(model().attribute("token", "token-uuid"));
    }

    @Test
    @DisplayName("Should return error when passwords do not match in reset password processing")
    void shouldReturnErrorWhenPasswordsDoNotMatch() throws Exception {
        mockMvc.perform(post("/reset-password")
                .param("token", "token-uuid")
                .param("password", "pass123")
                .param("confirmPassword", "diff123"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attribute("error", "Passwords do not match"))
                .andExpect(model().attribute("token", "token-uuid"));
    }

    @Test
    @DisplayName("Should return error when token is invalid or not found in reset password processing")
    void shouldReturnErrorWhenTokenNotFoundOnPost() throws Exception {
        String token = "invalid-token";
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        mockMvc.perform(post("/reset-password")
                .param("token", token)
                .param("password", "newPassword123")
                .param("confirmPassword", "newPassword123"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "Invalid or expired reset link. Please request a new one."));

        verify(tokenRepository).findByToken(token);
    }

    @Test
    @DisplayName("Should delete token and return error when token is expired in reset password processing")
    void shouldDeleteTokenAndReturnErrorWhenTokenExpiredOnPost() throws Exception {
        String token = "expired-token";
        PasswordResetToken expiredToken = new PasswordResetToken();
        expiredToken.setToken(token);
        expiredToken.setUser(testUser);
        expiredToken.setExpiryTime(LocalDateTime.now().minusMinutes(1)); // Expired

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(expiredToken));

        mockMvc.perform(post("/reset-password")
                .param("token", token)
                .param("password", "newPassword123")
                .param("confirmPassword", "newPassword123"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attribute("error", "Reset link has expired. Please request a new one."));

        verify(tokenRepository).findByToken(token);
        verify(tokenRepository).delete(expiredToken);
    }

    @Test
    @DisplayName("Should reset password, save user, delete token, and redirect to login when request is valid")
    void shouldResetPasswordSuccessfullyWhenRequestIsValid() throws Exception {
        String token = "valid-token";
        PasswordResetToken validToken = new PasswordResetToken();
        validToken.setToken(token);
        validToken.setUser(testUser);
        validToken.setExpiryTime(LocalDateTime.now().plusMinutes(10)); // Active

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(validToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newPasswordHash");

        mockMvc.perform(post("/reset-password")
                .param("token", token)
                .param("password", "newPassword123")
                .param("confirmPassword", "newPassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(tokenRepository).findByToken(token);
        verify(passwordEncoder).encode("newPassword123");
        verify(userRepository).save(testUser);
        verify(tokenRepository).delete(validToken);

        assertEquals("newPasswordHash", testUser.getPassword());
    }
}
