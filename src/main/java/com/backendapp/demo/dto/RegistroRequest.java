package com.backendapp.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/auth/registro} (publico). No trae rol: el servidor asigna siempre el rol
 * basico, asi nadie puede registrarse como administrador. La contraseña y el telefono se guardan solo
 * en Cognito (el User Pool exige {@code phone_number}).
 */
public record RegistroRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        @Size(max = 160, message = "El email no puede superar 160 caracteres")
        String email,
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$",
                message = "El teléfono debe tener formato internacional, por ejemplo +56912345678")
        String telefono,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 256, message = "La contraseña debe tener entre 8 y 256 caracteres")
        String password) {
}
