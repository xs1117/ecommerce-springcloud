package org.example.user.security;

import org.example.user.domain.UserRole;

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

