package com.backendapp.demo;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.service.CognitoService;

class RegistroTest extends PruebaBase {

	@Test
	void registroCreaLaCuentaEnCognitoYElPerfilLocalComoBeneficiario() throws Exception {
		when(cognitoService.registrar("ana@syncpass.test", "Ana Muñoz", "BENEFICIARIO", "Clave#2026"))
				.thenReturn("sub-ana");

		// Sin token: el registro es publico. El "rol" del cuerpo se ignora
		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":" Ana Muñoz ","email":"Ana@SyncPass.test","password":"Clave#2026","rol":"ADMINISTRADOR"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("ana@syncpass.test"))
				.andExpect(jsonPath("$.nombre").value("Ana Muñoz"))
				.andExpect(jsonPath("$.rol").value("BENEFICIARIO"))
				.andExpect(jsonPath("$.activo").value(true));

		mockMvc.perform(get("/api/usuarios").with(token("sub-admin", "ADMINISTRADOR")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.email == 'ana@syncpass.test')].rol").value("BENEFICIARIO"));
	}

	@Test
	void emailYaRegistradoResponde409SinTocarCognito() throws Exception {
		when(cognitoService.registrar("repetida@syncpass.test", "Repetida", "BENEFICIARIO", "Clave#2026"))
				.thenReturn("sub-repetida");
		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(registro("repetida@syncpass.test", "Repetida")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(registro("REPETIDA@syncpass.test", "Otra")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value(CognitoService.CUENTA_EXISTENTE));
		verify(cognitoService, never()).registrar("repetida@syncpass.test", "Otra", "BENEFICIARIO", "Clave#2026");
	}

	@Test
	void siLaBaseFallaSeEliminaLaCuentaDeCognito() throws Exception {
		// Cognito devuelve un sub que ya existe en la base: el INSERT falla y se compensa en Cognito
		when(cognitoService.registrar("primera@syncpass.test", "Primera", "BENEFICIARIO", "Clave#2026"))
				.thenReturn("sub-duplicado-registro");
		when(cognitoService.registrar("segunda@syncpass.test", "Segunda", "BENEFICIARIO", "Clave#2026"))
				.thenReturn("sub-duplicado-registro");
		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(registro("primera@syncpass.test", "Primera")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(registro("segunda@syncpass.test", "Segunda")))
				.andExpect(status().isConflict());
		verify(cognitoService).deleteUser("segunda@syncpass.test");
	}

	@Test
	void datosInvalidosResponden400SinTocarCognito() throws Exception {
		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Corta\",\"email\":\"corta@syncpass.test\",\"password\":\"123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("La contraseña debe tener entre 8 y 256 caracteres"));

		mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(registro("no-es-un-email", "Sin Email")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El email no es válido"));

		verify(cognitoService, never()).registrar(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void confirmarEnviaElCodigoACognito() throws Exception {
		mockMvc.perform(post("/api/auth/registro/confirmar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"Ana@SyncPass.test\",\"codigo\":\"123456\"}"))
				.andExpect(status().isNoContent());
		verify(cognitoService).confirmarRegistro("ana@syncpass.test", "123456");
	}

	@Test
	void codigoConFormatoInvalidoResponde400() throws Exception {
		mockMvc.perform(post("/api/auth/registro/confirmar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@syncpass.test\",\"codigo\":\"12ab\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El código tiene 6 dígitos"));
		verify(cognitoService, never()).confirmarRegistro(anyString(), anyString());
	}

	@Test
	void codigoIncorrectoDevuelveElMensajeDeCognito() throws Exception {
		doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código no es correcto"))
				.when(cognitoService).confirmarRegistro("ana@syncpass.test", "000000");

		mockMvc.perform(post("/api/auth/registro/confirmar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@syncpass.test\",\"codigo\":\"000000\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El código no es correcto"));
	}

	@Test
	void reenviarCodigoEsPublico() throws Exception {
		mockMvc.perform(post("/api/auth/registro/reenviar-codigo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@syncpass.test\"}"))
				.andExpect(status().isNoContent());
		verify(cognitoService).reenviarCodigo("ana@syncpass.test");
	}

	private static String registro(String email, String nombre) {
		return "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"password\":\"Clave#2026\"}";
	}
}
