package io.muzoo.ssc.controlmap;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import io.muzoo.ssc.controlmap.seed.SeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ControlMap — GRC findings-to-controls REST API.
 * Application entry point; component scanning is rooted at this package.
 */
@SpringBootApplication
@EnableConfigurationProperties({SeedProperties.class, AiSuggestionProperties.class})
public class ControlMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlMapApplication.class, args);
    }
}
