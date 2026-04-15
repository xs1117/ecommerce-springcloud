package org.example.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.user.domain.UserAccount;
import org.example.user.domain.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TokenService {

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long expireMinutes;

    public TokenService(ObjectMapper objectMapper,
                        @Value("${security.token-secret}") String secret,
                        @Value("${security.token-expire-minutes}") long expireMinutes) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.expireMinutes = expireMinutes;
    }

    public String issueToken(UserAccount userAccount) {
        try {
            long now = Instant.now().toEpochMilli();
            long expiresAt = now + expireMinutes * 60_000L;
            Map<String, Object> payload = Map.of(
                    "userId", userAccount.getId(),
                    "username", userAccount.getUsername(),
                    "nickname", userAccount.getNickname(),
                    "role", userAccount.getRole().name(),
                    "permissions", permissionsOf(userAccount.getRole()),
                    "points", userAccount.getPoints(),
                    "memberLevel", userAccount.getMemberLevel(),
                    "iat", now,
                    "exp", expiresAt
            );
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadPart = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signaturePart = base64Url(sign(payloadPart));
            return payloadPart + "." + signaturePart;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue token", ex);
        }
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return Optional.empty();
            }
            byte[] expected = sign(parts[0]);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(expected).equals(parts[1])) {
                return Optional.empty();
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
            long exp = Long.parseLong(payload.get("exp").toString());
            if (Instant.now().toEpochMilli() > exp) {
                return Optional.empty();
            }
            Long userId = Long.valueOf(payload.get("userId").toString());
            String username = String.valueOf(payload.get("username"));
            String nickname = String.valueOf(payload.get("nickname"));
            UserRole role = UserRole.valueOf(String.valueOf(payload.get("role")));
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) payload.get("permissions");
            Integer points = Integer.valueOf(payload.get("points").toString());
            String memberLevel = String.valueOf(payload.get("memberLevel"));
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

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<String> permissionsOf(UserRole role) {
        return role == UserRole.ADMIN
                ? List.of("user:read", "user:write", "user:status", "member:manage", "admin:dashboard")
                : List.of("user:read", "member:read", "member:points");
    }
}

