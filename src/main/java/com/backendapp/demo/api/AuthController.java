package com.backendapp.demo.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.ConfirmarRegistroRequest;
import com.backendapp.demo.dto.LoginRequest;
import com.backendapp.demo.dto.LoginResponse;
import com.backendapp.demo.dto.ReenviarCodigoRequest;
import com.backendapp.demo.dto.RegistroRequest;
import com.backendapp.demo.dto.UsuarioResponse;
import com.backendapp.demo.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * Inicio de sesion y auto-registro (publicos). El login devuelve el access token de Cognito y el perfil
 * del usuario; el registro crea la cuenta en Cognito y en la base, y queda pendiente de confirmar el email.
 */
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

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest request) {
		return usuarioService.registrar(request);
	}

	@PostMapping("/registro/confirmar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmar(@Valid @RequestBody ConfirmarRegistroRequest request) {
		usuarioService.confirmarRegistro(request.email(), request.codigo());
	}

	@PostMapping("/registro/reenviar-codigo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequest request) {
		usuarioService.reenviarCodigo(request.email());
	}
}
