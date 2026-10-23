package com.backendapp.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import com.jayway.jsonpath.JsonPath;

class UsuarioTest extends PruebaBase {

	private static final String ADMIN = "ADMINISTRADOR";

	@Test
	void administradorCreaUsuarioEnCognitoYEnLaBase() throws Exception {
		when(cognitoService.createUser("voluntario2", "voluntario2@syncpass.test", "Voluntario Dos", "VOLUNTARIO",
				"volun12345")).thenReturn("sub-voluntario2");

		mockMvc.perform(post("/api/usuarios").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content(alta("voluntario2", "Voluntario2@syncpass.test", "VOLUNTARIO")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.username").value("voluntario2"))
				.andExpect(jsonPath("$.email").value("voluntario2@syncpass.test"))
				.andExpect(jsonPath("$.rol").value("VOLUNTARIO"))
				.andExpect(jsonPath("$.activo").value(true));

		mockMvc.perform(get("/api/usuarios").with(token("sub-admin", ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.username == 'voluntario2')].rol").value("VOLUNTARIO"));
	}

	@Test
	void rolInvalidoResponde400SinTocarCognito() throws Exception {
		mockMvc.perform(post("/api/usuarios").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content(alta("jefe1", null, "JEFE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(
						"Rol inválido: JEFE (valores permitidos: ADMINISTRADOR, COORDINADOR, VOLUNTARIO, BENEFICIARIO)"));
		verify(cognitoService, never()).createUser(anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void usernameRepetidoResponde409() throws Exception {
		when(cognitoService.createUser(eq("repetido"), any(), anyString(), anyString(), anyString()))
				.thenReturn("sub-repetido");
		crear("repetido", "COORDINADOR");

		mockMvc.perform(post("/api/usuarios").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content(alta("repetido", null, "COORDINADOR")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya existe un usuario con ese nombre de usuario"));
	}

	@Test
	void siLaBaseFallaSeEliminaElUsuarioDeCognito() throws Exception {
		// Cognito devuelve un sub que ya existe en la base: el INSERT falla y se compensa en Cognito
		when(cognitoService.createUser(anyString(), any(), anyString(), anyString(), anyString()))
				.thenReturn("sub-duplicado");
		crear("original", "VOLUNTARIO");

		mockMvc.perform(post("/api/usuarios").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content(alta("copia", null, "VOLUNTARIO")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("El usuario ya existe"));
		verify(cognitoService).deleteUser("copia");
	}

	@Test
	void desactivarActualizaCognitoYLaBase() throws Exception {
		when(cognitoService.createUser(eq("desactivable"), any(), anyString(), anyString(), anyString()))
				.thenReturn("sub-desactivable");
		long id = crear("desactivable", "VOLUNTARIO");

		mockMvc.perform(patch("/api/usuarios/" + id + "/activo").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
		verify(cognitoService).setActivo("desactivable", false);
	}

	@Test
	void siCognitoFallaAlDesactivarNoCambiaLaBase() throws Exception {
		when(cognitoService.createUser(eq("fallido"), any(), anyString(), anyString(), anyString()))
				.thenReturn("sub-fallido");
		long id = crear("fallido", "VOLUNTARIO");
		doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Servicio de autenticación no disponible"))
				.when(cognitoService).setActivo(eq("fallido"), anyBoolean());

		mockMvc.perform(patch("/api/usuarios/" + id + "/activo").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("Servicio de autenticación no disponible"));

		mockMvc.perform(get("/api/usuarios/" + id).with(token("sub-admin", ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void usuarioInexistenteResponde404() throws Exception {
		mockMvc.perform(get("/api/usuarios/99999").with(token("sub-admin", ADMIN)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Usuario no encontrado"));
	}

	private long crear(String username, String rol) throws Exception {
		String respuesta = mockMvc.perform(post("/api/usuarios").with(token("sub-admin", ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content(alta(username, null, rol)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private static String alta(String username, String email, String rol) {
		String campoEmail = email == null ? "" : ",\"email\":\"" + email + "\"";
		String nombre = "voluntario2".equals(username) ? "Voluntario Dos" : "Usuario " + username;
		return "{\"username\":\"" + username + "\",\"nombre\":\"" + nombre + "\"" + campoEmail
				+ ",\"rol\":\"" + rol + "\",\"password\":\"volun12345\"}";
	}
}
