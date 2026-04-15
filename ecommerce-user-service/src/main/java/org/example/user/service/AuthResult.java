package org.example.user.service;

import org.example.user.domain.UserRole;

import java.util.List;

public record AuthResult(
        String token,
        Long userId,
        String username,
        String nickname,
        UserRole role,
        List<String> permissions,
        Integer points,
        String memberLevel
) {
}

