package com.backendapp.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/auth/registro/reenviar-codigo}. */
public record ReenviarCodigoRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        String email) {
}
