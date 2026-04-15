package org.example.merchant.security;

import org.example.merchant.domain.UserRole;

import java.util.List;

public record AuthenticatedUser(
        Long userId,
        String username,
        String nickname,
        UserRole role,
        List<String> permissions,
        Integer points,
        String memberLevel
) {
}

