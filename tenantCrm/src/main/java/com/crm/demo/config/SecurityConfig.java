package com.crm.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;

import com.crm.demo.security.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.holidays-api-path:/api/holidays/**}")
    private String holidaysApiPath;

    @Value("${app.security.notifications-api-path:/api/notifications/**}")
    private String notificationsApiPath;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_HR = "HR";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth
                        // ── Public assets & auth pages ──────────────────────────────
                        .requestMatchers(
                                "/login",
                                "/forgot-password",
                                "/reset-password",
                                "/api/auth/**",
                                "/error",
                                "/error/**",
                                "/*.css",
                                "/*.js",
                                "/*.png",
                                "/*.jpg",
                                "/*.ico",
                                "/*.woff",
                                "/*.woff2",
                                "/*.svg",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/static/**",
                                "/ws",
                                "/ws/**")
                        .permitAll()
                        // GET /api/holidays — any authenticated user (all roles see their tenant's
                        // holidays)
                        // POST /api/holidays — ADMIN or HR only
                        // PUT /api/holidays/* — ADMIN or HR only
                        // DELETE /api/holidays/* — ADMIN or HR only
                        .requestMatchers(HttpMethod.GET, "/api/holidays").authenticated()
                        .requestMatchers(HttpMethod.GET, holidaysApiPath).authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/holidays").hasAnyRole(ROLE_ADMIN, ROLE_HR)
                        .requestMatchers(HttpMethod.PUT, holidaysApiPath).hasAnyRole(ROLE_ADMIN, ROLE_HR)
                        .requestMatchers(HttpMethod.DELETE, holidaysApiPath).hasAnyRole(ROLE_ADMIN, ROLE_HR)
                        .requestMatchers(HttpMethod.GET, "/api/notifications").authenticated()
                        .requestMatchers(HttpMethod.GET, notificationsApiPath).authenticated()
                        .requestMatchers(HttpMethod.POST, notificationsApiPath).authenticated()
                        .requestMatchers(HttpMethod.DELETE, notificationsApiPath).authenticated()
                        // ── Role-scoped pages ────────────────────────────────────────
                        .requestMatchers("/superadmin/**").hasRole(ROLE_SUPER_ADMIN)
                        .requestMatchers("/admin/**").hasRole(ROLE_ADMIN)
                        .requestMatchers("/manager/**").hasRole(ROLE_MANAGER)
                        .requestMatchers("/hr/**").hasRole(ROLE_HR)
                        .requestMatchers("/employee/**").hasRole(ROLE_EMPLOYEE)
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                if (Boolean.TRUE.equals(request.getAttribute("session_superseded"))) {
                                    response.getWriter().write("{\"error\":\"superseded\"}");
                                } else {
                                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                                }
                            } else {
                                if (Boolean.TRUE.equals(request.getAttribute("session_superseded"))) {
                                    response.sendRedirect("/login?error=superseded");
                                } else {
                                    response.sendRedirect("/login");
                                }
                            }
                        }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public HttpFirewall httpFirewall() {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(false);
        return firewall;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
