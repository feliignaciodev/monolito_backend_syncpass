package com.backendapp.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio") String username,
        @NotBlank(message = "La contraseña es obligatoria") String password) {
}
