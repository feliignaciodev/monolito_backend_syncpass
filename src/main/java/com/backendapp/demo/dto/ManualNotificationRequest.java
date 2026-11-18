package com.backendapp.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ManualNotificationRequest(
    @NotBlank String titulo,
    @NotBlank String cuerpo,
    List<String> roles
) {
}
