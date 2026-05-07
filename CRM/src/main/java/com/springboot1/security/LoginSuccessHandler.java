package com.springboot1.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_MANAGER"));
        boolean isSalesExecutive = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_SALES_EXECUTIVE"));

        if (isSuperAdmin) {
            response.sendRedirect("/dashboard/super-admin");
            return;
        }
        if (isAdmin) {
            response.sendRedirect("/dashboard/admin");
            return;
        }
        if (isManager) {
            response.sendRedirect("/dashboard/manager");
            return;
        }
        if (isSalesExecutive) {
            response.sendRedirect("/sales/dashboard");
            return;
        }
        response.sendRedirect("/user/home");
    }
}
