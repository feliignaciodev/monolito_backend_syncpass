package com.backendapp.demo.api;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.AsistenciaLoteRequest;
import com.backendapp.demo.dto.CatalogoResponse;
import com.backendapp.demo.dto.EncuestaLoteRequest;
import com.backendapp.demo.dto.ResultadoSyncResponse;
import com.backendapp.demo.service.CatalogoService;
import com.backendapp.demo.service.SyncService;
import com.backendapp.demo.service.UsuarioService;

import jakarta.validation.Valid;

/** Sincronizacion: bajada del catalogo y subida por lotes de asistencias y encuestas. */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

	private final CatalogoService catalogoService;
	private final SyncService syncService;
	private final UsuarioService usuarioService;

	public SyncController(CatalogoService catalogoService, SyncService syncService, UsuarioService usuarioService) {
		this.catalogoService = catalogoService;
		this.syncService = syncService;
		this.usuarioService = usuarioService;
	}

	@GetMapping("/catalogo")
	public CatalogoResponse catalogo() {
		return catalogoService.obtenerCatalogo();
	}

	@PostMapping("/asistencias")
	public ResultadoSyncResponse asistencias(@Valid @RequestBody AsistenciaLoteRequest request,
			JwtAuthenticationToken authentication) {
		Long usuarioId = usuarioService.idLocal(authentication.getToken().getSubject()).orElse(null);
		return syncService.sincronizarAsistencias(request.registros(), usuarioId);
	}

	@PostMapping("/encuestas")
	public ResultadoSyncResponse encuestas(@Valid @RequestBody EncuestaLoteRequest request) {
		return syncService.sincronizarEncuestas(request.encuestas());
	}
}
