package org.example.service;

import org.example.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationServiceTest {

    @Test
    void shouldCreateAndFindPendingActionByToken() {
        AiProperties properties = new AiProperties();
        properties.setConfirmationTtlSeconds(300);
        ConfirmationService confirmationService = new ConfirmationService(properties);

        PendingAction pendingAction = confirmationService.create(1001L, "ORDER-1001", "APPLY_RETURN", "remark");

        assertTrue(confirmationService.findValidByToken(1001L, pendingAction.token()).isPresent());
        assertFalse(confirmationService.findValidByToken(1002L, pendingAction.token()).isPresent());

        confirmationService.invalidate(pendingAction);
        assertFalse(confirmationService.findValidByToken(1001L, pendingAction.token()).isPresent());
    }
}

