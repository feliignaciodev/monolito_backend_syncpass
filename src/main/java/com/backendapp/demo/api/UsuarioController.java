package com.backendapp.demo.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.ActivoPatchRequest;
import com.backendapp.demo.dto.UsuarioCreateRequest;
import com.backendapp.demo.dto.UsuarioResponse;
import com.backendapp.demo.service.UsuarioService;

import jakarta.validation.Valid;

/** Gestion de cuentas (Cognito + perfil local). Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping
	public List<UsuarioResponse> listar() {
		return usuarioService.listar();
	}

	@GetMapping("/{id}")
	public UsuarioResponse obtener(@PathVariable("id") Long id) {
		return usuarioService.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse crear(@Valid @RequestBody UsuarioCreateRequest request) {
		return usuarioService.crearUsuario(request);
	}

	@PatchMapping("/{id}/activo")
	public UsuarioResponse cambiarActivo(@PathVariable("id") Long id, @Valid @RequestBody ActivoPatchRequest request) {
		return usuarioService.setActivo(id, request.activo());
	}
}
