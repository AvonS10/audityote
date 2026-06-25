package io.muzoo.ssc.controlmap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing. A single {@link PasswordEncoder} (BCrypt) bean is shared by the seeder (#6) and,
 * from chunk #7, Spring Security's authentication. Plaintext passwords are never stored or logged.
 */
@Configuration
public class CryptoConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
