package com.smarthire.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI smarthireOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartHire API")
                        .description("""
                                REST API of the AI-assisted Smart Recruitment Management System.
                                Java Spring Boot is the primary backend; the Python service only
                                performs resume intelligence and is never called by browsers.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("SmartHire").email("api@smarthire.io")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
