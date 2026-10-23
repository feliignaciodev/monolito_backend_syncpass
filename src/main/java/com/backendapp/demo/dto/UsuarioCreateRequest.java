package com.backendapp.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de usuario (solo ADMINISTRADOR). La contraseña se guarda solo en Cognito. */
public record UsuarioCreateRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "El usuario no puede superar 50 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "El usuario solo admite letras, números, punto, guion y guion bajo")
        String username,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,
        @Email(message = "El email no es válido")
        @Size(max = 160, message = "El email no puede superar 160 caracteres")
        String email,
        @NotBlank(message = "El rol es obligatorio") String rol,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password) {
}
