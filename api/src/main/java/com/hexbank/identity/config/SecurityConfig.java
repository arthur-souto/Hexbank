package com.hexbank.identity.config;

import com.hexbank.identity.adapter.in.http.DevHeaderAuthenticationFilter;
import com.hexbank.shared.adapter.in.http.ProblemSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Environment env) throws Exception {
        var problemHandlers = new ProblemSecurityHandlers();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v1/dev/**").hasRole("TEST_ADMIN")
                        .anyRequest().authenticated());

        if (env.acceptsProfiles(Profiles.of("dev", "test"))) {
            http.addFilterBefore(new DevHeaderAuthenticationFilter(), AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * Sem isto o Boot cria um usuário "user" com senha aleatória no log.
     * A API não tem login por senha: autenticação vem do filtro dev (hoje) ou de JWT (futuro).
     */
    @Bean
    UserDetailsService noPasswordUsers() {
        return new InMemoryUserDetailsManager();
    }
}