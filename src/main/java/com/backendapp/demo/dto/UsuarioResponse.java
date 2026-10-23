package com.backendapp.demo.dto;

public record UsuarioResponse(
        Long id,
        String username,
        String nombre,
        String email,
        String rol,
        Boolean activo) {
}
