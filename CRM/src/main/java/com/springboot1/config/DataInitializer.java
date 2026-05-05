package com.springboot1.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.springboot1.model.Role;
import com.springboot1.model.User;
import com.springboot1.repository.UserRepository;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            upsertUser(userRepository, passwordEncoder, "superadmin", "superadmin@crm.com", Role.SUPER_ADMIN);
            upsertUser(userRepository, passwordEncoder, "admin", "admin@crm.com", Role.ADMIN);
            upsertUser(userRepository, passwordEncoder, "manager", "manager@crm.com", Role.MANAGER);
            upsertUser(userRepository, passwordEncoder, "user", "user@crm.com", Role.USER);
            upsertUser(userRepository, passwordEncoder, "sales", "sales@crm.com", Role.SALES_EXECUTIVE);
        };
    }

    private void upsertUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
            String username, String email, Role role) {
        User appUser = userRepository.findByUsername(username).orElseGet(User::new);
        appUser.setUsername(username);
        appUser.setEmail(email);
        appUser.setRole(role);
        appUser.setPassword(passwordEncoder.encode("password"));
        userRepository.save(appUser);
    }
}
