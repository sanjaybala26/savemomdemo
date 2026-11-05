package com.savemom.appointmentqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemom.appointmentqueue.dto.ErrorResponse;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Interceptor that guards protected endpoints.
 * Validates the Authorization: Bearer <token> header and attaches the authenticated User.
 */
@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtAuthenticationInterceptor(JwtService jwtService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight OPTIONS requests through without authentication
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or malformed Authorization header. Expected: Bearer <token>");
            return false;
        }

        String token = authHeader.substring(7).trim();
        String email = jwtService.validateTokenAndGetEmail(token);
        if (email == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token");
            return false;
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "User account associated with token no longer exists");
            return false;
        }

        // Attach authenticated user to request attributes for downstream controllers
        request.setAttribute(CURRENT_USER_ATTRIBUTE, userOpt.get());
        return true;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse errorResponse = new ErrorResponse(status, "Unauthorized", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
