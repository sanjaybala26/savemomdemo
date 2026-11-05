package com.savemom.appointmentqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemom.appointmentqueue.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for issuing and validating JSON Web Tokens (JWT) using HMAC-SHA256.
 * Implements standard RFC 7519 tokens with zero external JWT libraries for maximum readability.
 * The secret key and token lifetime are read from environment variables.
 */
@Service
public class JwtService {

    private final String secretKey;
    private final long expirationMillis;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtService(
            @Value("${app.jwt.secret:dev-carequeue-secret-key-32-chars-minimum-length-security}") String secretKey,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMillis,
            ObjectMapper objectMapper
    ) {
        this.secretKey = secretKey;
        this.expirationMillis = expirationMillis;
        this.objectMapper = objectMapper;
    }

    /**
     * Issues a signed JWT containing user identity and role.
     */
    public String generateToken(User user) {
        try {
            // 1. JWT Header
            Map<String, Object> headerMap = new HashMap<>();
            headerMap.put("alg", "HS256");
            headerMap.put("typ", "JWT");
            String headerJson = objectMapper.writeValueAsString(headerMap);
            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));

            // 2. JWT Payload (Claims)
            long nowSec = Instant.now().getEpochSecond();
            long expSec = nowSec + (expirationMillis / 1000);

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sub", user.getEmail());
            payloadMap.put("userId", user.getId());
            payloadMap.put("name", user.getName());
            payloadMap.put("role", user.getRole().name());
            payloadMap.put("iat", nowSec);
            payloadMap.put("exp", expSec);
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            // 3. Compute HMAC-SHA256 signature over (header + "." + payload)
            String signingInput = encodedHeader + "." + encodedPayload;
            byte[] signatureBytes = computeHmacSha256(signingInput, secretKey);
            String encodedSignature = base64UrlEncode(signatureBytes);

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the token's signature and expiration, returning the user email (subject).
     * Returns null if the token is invalid, tampered with, or expired.
     */
    public String validateTokenAndGetEmail(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }

        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = computeHmacSha256(signingInput, secretKey);
            byte[] actualSignature = base64UrlDecode(parts[2]);

            // Constant-time signature verification prevents timing attacks
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return null;
            }

            // Parse payload
            byte[] payloadBytes = base64UrlDecode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);

            // Check expiration
            Number expNumber = (Number) payload.get("exp");
            if (expNumber == null) {
                return null;
            }
            long expSec = expNumber.longValue();
            if (Instant.now().getEpochSecond() > expSec) {
                return null; // Token expired
            }

            return (String) payload.get("sub");
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] computeHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String base64) {
        return Base64.getUrlDecoder().decode(base64);
    }
}
