package com.smarthire.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed binding for the {@code smarthire.*} configuration keys. */
@Getter
@Setter
@ConfigurationProperties(prefix = "smarthire")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Ai ai = new Ai();
    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenMinutes = 30;
        private long refreshTokenDays = 7;
    }

    @Getter
    @Setter
    public static class Cors {
        /**
         * Exact origins (comma separated via {@code CORS_ORIGINS}). When set, they are used
         * verbatim and the patterns below are ignored.
         */
        private List<String> allowedOrigins = List.of();

        /**
         * Used when no explicit origins are configured. Covers the dev server on any port
         * and either loopback spelling (localhost / 127.0.0.1 / [::1]) - Vite moves to a
         * new port automatically when 5173 is busy, and browsers keep the Origin header
         * when the request is proxied, so a fixed port list silently 403s.
         */
        private List<String> allowedOriginPatterns = List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*");
    }

    @Getter
    @Setter
    public static class Ai {
        private String baseUrl = "http://localhost:8000";
        private boolean enabled = true;
        private long timeoutMs = 20000;
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "./data/uploads";
    }
}
