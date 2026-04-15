package org.example.service;

import org.example.config.AiProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfirmationService {

    private final AiProperties aiProperties;
    private final Map<String, PendingAction> byToken = new ConcurrentHashMap<>();
    private final Map<Long, String> latestTokenByUser = new ConcurrentHashMap<>();

    public ConfirmationService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public PendingAction create(Long userId, String orderNo, String actionType, String remark) {
        Instant now = Instant.now();
        String token = UUID.randomUUID().toString();
        PendingAction pendingAction = new PendingAction(
                token,
                userId,
                orderNo,
                actionType,
                remark,
                now.plusSeconds(Math.max(60, aiProperties.getConfirmationTtlSeconds()))
        );
        byToken.put(token, pendingAction);
        latestTokenByUser.put(userId, token);
        return pendingAction;
    }

    public Optional<PendingAction> findValidByToken(Long userId, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        PendingAction action = byToken.get(token);
        if (action == null) {
            return Optional.empty();
        }
        if (!userId.equals(action.userId())) {
            return Optional.empty();
        }
        if (action.expired(Instant.now())) {
            invalidate(action);
            return Optional.empty();
        }
        return Optional.of(action);
    }

    public Optional<PendingAction> findLatestValidByUser(Long userId) {
        String token = latestTokenByUser.get(userId);
        return findValidByToken(userId, token);
    }

    public void invalidate(PendingAction action) {
        if (action == null) {
            return;
        }
        byToken.remove(action.token());
        latestTokenByUser.compute(action.userId(), (id, token) -> action.token().equals(token) ? null : token);
    }
}

