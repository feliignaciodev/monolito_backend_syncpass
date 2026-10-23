package com.backendapp.demo.dto;

/** Datos publicos del usuario autenticado. */
public record UsuarioDto(Long id, String username, String nombre, String rol) {
}
