package com.backendapp.demo.dto;

/** Respuesta de login exitoso: access token de Cognito + datos del usuario. */
public record LoginResponse(String token, UsuarioDto usuario) {
}
