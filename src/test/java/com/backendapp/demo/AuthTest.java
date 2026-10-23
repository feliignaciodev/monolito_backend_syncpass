package com.backendapp.demo;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.service.CognitoService;

class AuthTest extends PruebaBase {

	private static final String CUERPO_LOGIN = """
			{"username":"coordinador","password":"coord123"}""";

	@Test
	void loginExitosoDevuelveTokenYUsuarioConElFormatoDeLaApp() throws Exception {
		when(cognitoService.login("coordinador", "coord123")).thenReturn(new CognitoService.LoginResult(
				"access-token-coordinador", "sub-coordinador", "coordinador", List.of("COORDINADOR")));
		when(cognitoService.obtenerPerfil("coordinador"))
				.thenReturn(new CognitoService.Perfil("Coordinadora Demo", "coordinador@syncpass.test"));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO_LOGIN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("access-token-coordinador"))
				.andExpect(jsonPath("$.usuario.id").isNumber())
				.andExpect(jsonPath("$.usuario.username").value("coordinador"))
				.andExpect(jsonPath("$.usuario.nombre").value("Coordinadora Demo"))
				.andExpect(jsonPath("$.usuario.rol").value("COORDINADOR"));

		// Segundo login: reutiliza el perfil local (mismo id) sin volver a consultar el perfil en Cognito
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO_LOGIN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.username").value("coordinador"));
		verify(cognitoService, org.mockito.Mockito.times(1)).obtenerPerfil("coordinador");
	}

	@Test
	void elRolSeTomaDelGrupoDeMayorPrivilegio() throws Exception {
		when(cognitoService.login(anyString(), anyString())).thenReturn(new CognitoService.LoginResult(
				"t", "sub-multi", "multi", List.of("VOLUNTARIO", "COORDINADOR")));
		when(cognitoService.obtenerPerfil("multi")).thenReturn(new CognitoService.Perfil("Multi Demo", null));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"multi\",\"password\":\"x\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.rol").value("COORDINADOR"));
	}

	@Test
	void usuarioRecreadoEnCognitoConservaSuPerfilLocal() throws Exception {
		String cuerpo = "{\"username\":\"recreado\",\"password\":\"x\"}";
		when(cognitoService.login(anyString(), anyString())).thenReturn(new CognitoService.LoginResult(
				"t1", "sub-original", "recreado", List.of("VOLUNTARIO")));
		when(cognitoService.obtenerPerfil("recreado")).thenReturn(new CognitoService.Perfil("Recreado Demo", null));
		String primero = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number id = com.jayway.jsonpath.JsonPath.read(primero, "$.usuario.id");

		// El User Pool se volvio a crear: mismo username, otro sub. Antes respondia 409 para siempre
		when(cognitoService.login(anyString(), anyString())).thenReturn(new CognitoService.LoginResult(
				"t2", "sub-nuevo", "recreado", List.of("VOLUNTARIO")));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.id").value(id.longValue()));
	}

	@Test
	void credencialesIncorrectasResponden401() throws Exception {
		when(cognitoService.login(anyString(), anyString()))
				.thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, CognitoService.CREDENCIALES_INVALIDAS));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO_LOGIN))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
	}

	@Test
	void usuarioSinGrupoResponde403() throws Exception {
		when(cognitoService.login(anyString(), anyString()))
				.thenReturn(new CognitoService.LoginResult("t", "sub-sin-grupo", "singrupo", List.of()));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"singrupo\",\"password\":\"x\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("El usuario no tiene un rol asignado; contacte al administrador"));
	}

	@Test
	void faltaElUsuarioResponde400() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El usuario es obligatorio"));
	}

	@Test
	void jsonMalFormadoResponde400() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("JSON mal formado o con tipos de datos inválidos"));
	}
}
