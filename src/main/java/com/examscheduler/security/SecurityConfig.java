package com.examscheduler.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler smartExamSuccessHandler() {
        return (request, response, authentication) -> {
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_ADMIN".equals(role)) {
                response.sendRedirect("/admin/dashboard");
            } else if ("ROLE_EXAM_COORDINATOR".equals(role)) {
                response.sendRedirect("/coordinator/dashboard");
            } else if ("ROLE_FACULTY".equals(role)) {
                response.sendRedirect("/faculty/dashboard");
            } else {
                response.sendRedirect("/");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/", "/login", "/access-denied").permitAll()

                // Admin-only Faculty Import feature
                .requestMatchers("/admin/faculty/import", "/admin/faculty/import/**", "/admin/faculty/template").hasRole("ADMIN")

                // Shared operational and Core Entities modules for Admin and Exam Coordinator
                .requestMatchers(
                    "/admin/examinations/**",
                    "/admin/rooms/**",
                    "/admin/schedule/**",
                    "/admin/invigilation/**",
                    "/admin/seating/**",
                    "/admin/conflicts/**",
                    "/admin/reports/**",
                    "/admin/departments/**",
                    "/admin/faculty/**",
                    "/admin/students/**",
                    "/admin/subjects/**"
                ).hasAnyRole("ADMIN", "EXAM_COORDINATOR")

                // Strict Admin-only modules (users, profile, audit logs, analytics)
                .requestMatchers(
                    "/admin/dashboard",
                    "/admin/profile",
                    "/admin/users/**",
                    "/admin/audit/**",
                    "/admin/analytics/**",
                    "/admin/**"
                ).hasRole("ADMIN")

                // Coordinator dedicated portal
                .requestMatchers("/coordinator/**").hasAnyRole("ADMIN", "EXAM_COORDINATOR")

                // Faculty dedicated portal
                .requestMatchers("/faculty/**").hasAnyRole("ADMIN", "FACULTY")

                // General authenticated routes (notifications, profile, etc.)
                .requestMatchers("/notifications/**", "/api/**").authenticated()

                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(smartExamSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .headers(headers -> headers
                .cacheControl(cache -> {})
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            )
            .sessionManagement(session -> session
                .maximumSessions(2)
                .expiredUrl("/login?expired=true")
            );

        return http.build();
    }
}
