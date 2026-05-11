package org.example.service;

import java.time.Instant;

public record PendingAction(
        String token,
        Long userId,
        String orderNo,
        String actionType,
        String remark,
        String stage,
        String reasonSummary,
        Instant expireAt
) {

    public static final String STAGE_CONFIRMATION = "CONFIRMATION";
    public static final String STAGE_COLLECT_RETURN_REASON = "COLLECT_RETURN_REASON";

    public boolean expired(Instant now) {
        return expireAt.isBefore(now);
    }

    public boolean waitingReturnReason() {
        return STAGE_COLLECT_RETURN_REASON.equalsIgnoreCase(stage);
    }
}

