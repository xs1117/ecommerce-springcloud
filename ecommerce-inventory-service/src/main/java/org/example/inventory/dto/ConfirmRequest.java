package org.example.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(@NotBlank String reservationNo) {
}

