package com.savemom.appointmentqueue.config;

import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration for CORS and Route Security Interceptors.
 * - Allows React frontend to communicate with backend endpoints.
 * - Protects authenticated routes (/api/auth/me) via JWT verification.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Allowed origins loaded from application.properties / environment variable
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Autowired
    public CorsConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Protect authenticated endpoints with JWT verification
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/api/auth/me", "/api/provider/**", "/api/appointments/**", "/api/queue/**", "/api/admin/**");
    }
}
