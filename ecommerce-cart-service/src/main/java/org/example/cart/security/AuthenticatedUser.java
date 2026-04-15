package org.example.cart.security;

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

