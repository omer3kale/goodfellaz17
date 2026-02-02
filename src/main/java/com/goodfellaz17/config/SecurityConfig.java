package com.goodfellaz17.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security Configuration - Password Encryption
 *
 * Provides PasswordEncoder bean for application-wide use.
 * BCrypt with strength 12 = ~100ms per hash (secure default for 2026).
 *
 * Usage: Inject PasswordEncoder in services for password hashing.
 */
@Slf4j
@Configuration
public class SecurityConfig {

    /**
     * Creates BCrypt password encoder bean with strength 12
     *
     * Strength 12 = ~100ms per encoding operation
     * - Secure against GPU attacks (2026+)
     * - Acceptable user experience (~100ms wait per signup)
     * - Automatic salt generation per hash
     *
     * @return BCryptPasswordEncoder configured for production use
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("Initializing BCryptPasswordEncoder with strength 12");
        return new BCryptPasswordEncoder(12);
    }
}
