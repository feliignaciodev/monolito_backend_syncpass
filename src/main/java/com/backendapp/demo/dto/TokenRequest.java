package com.backendapp.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
    @NotBlank String fcmToken
) {
}
