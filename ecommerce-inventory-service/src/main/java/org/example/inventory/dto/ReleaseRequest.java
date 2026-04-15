package org.example.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record ReleaseRequest(@NotBlank String reservationNo) {
}

