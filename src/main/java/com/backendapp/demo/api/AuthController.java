package com.backendapp.demo.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.LoginRequest;
import com.backendapp.demo.dto.LoginResponse;
import com.backendapp.demo.service.UsuarioService;

import jakarta.validation.Valid;

/** Inicio de sesion (publico). Devuelve el access token de Cognito y el perfil del usuario. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UsuarioService usuarioService;

	public AuthController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return usuarioService.login(request.username(), request.password());
	}
}
