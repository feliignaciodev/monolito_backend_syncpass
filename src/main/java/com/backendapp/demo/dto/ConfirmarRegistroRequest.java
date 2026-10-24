package com.backendapp.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de {@code POST /api/auth/registro/confirmar}: el codigo que Cognito envio al email. */
public record ConfirmarRegistroRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        String email,
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos")
        String codigo) {
}
