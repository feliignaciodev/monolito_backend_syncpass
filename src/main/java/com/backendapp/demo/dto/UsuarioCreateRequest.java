package com.backendapp.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioCreateRequest(
        @NotBlank String nombre,
        @NotBlank @Email String email,
        @NotBlank String rol,
        String password) {
}