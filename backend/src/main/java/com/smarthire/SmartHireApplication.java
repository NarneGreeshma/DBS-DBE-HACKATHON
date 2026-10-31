package com.smarthire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartHire - AI-assisted Smart Recruitment Management System.
 *
 * <p>This Spring Boot application is the <b>primary backend</b>: it owns authentication,
 * role-based access, every domain module and all PostgreSQL persistence. The Python/FastAPI
 * service is only ever consulted for resume intelligence through {@code com.smarthire.ai.AiClient}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class SmartHireApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartHireApplication.class, args);
    }
}
