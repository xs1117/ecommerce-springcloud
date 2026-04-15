package org.example.service;

import java.time.Instant;

public record PendingAction(
        String token,
        Long userId,
        String orderNo,
        String actionType,
        String remark,
        Instant expireAt
) {

    public boolean expired(Instant now) {
        return expireAt.isBefore(now);
    }
}

