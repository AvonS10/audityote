package io.muzoo.ssc.controlmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ControlMap — GRC findings-to-controls REST API.
 * Application entry point; component scanning is rooted at this package.
 */
@SpringBootApplication
public class ControlMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlMapApplication.class, args);
    }
}
