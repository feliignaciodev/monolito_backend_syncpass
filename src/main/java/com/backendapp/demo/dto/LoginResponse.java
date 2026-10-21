package com.backendapp.demo.dto;

public record LoginResponse(
        String token,
        String rol,
        long expiraEn,
        UsuarioResumen usuario) {

    public record UsuarioResumen(Long id, String nombre) {
    }
}