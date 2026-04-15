package org.example.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.chat.domain.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TokenService {

    private final ObjectMapper objectMapper;
    private final String secret;

    public TokenService(ObjectMapper objectMapper, @Value("${security.token-secret}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return Optional.empty();
            }
            byte[] expected = sign(parts[0]);
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(expected);
            if (!expectedSignature.equals(parts[1])) {
                return Optional.empty();
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));
            if (Instant.now().toEpochMilli() > exp) {
                return Optional.empty();
            }
            Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
            String username = String.valueOf(payload.get("username"));
            String nickname = String.valueOf(payload.get("nickname"));
            UserRole role = UserRole.valueOf(String.valueOf(payload.get("role")));
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) payload.getOrDefault("permissions", Collections.emptyList());
            Integer points = Integer.valueOf(String.valueOf(payload.getOrDefault("points", 0)));
            String memberLevel = String.valueOf(payload.getOrDefault("memberLevel", "BRONZE"));
            return Optional.of(new AuthenticatedUser(userId, username, nickname, role, permissions, points, memberLevel));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payloadPart) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
    }
}


