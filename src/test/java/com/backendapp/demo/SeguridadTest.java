package com.backendapp.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SeguridadTest extends PruebaBase {

	@Autowired
	private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

	@Test
	void healthDeActuatorYDeLaApiSonPublicos() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.serverTime").isNumber());
	}

	@Test
	void sinTokenResponde401ConFormatoDelContrato() throws Exception {
		mockMvc.perform(get("/api/sync/catalogo"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.error").value("Falta el token de autenticación (Authorization: Bearer <token>)"));
	}

	@Test
	void tokenInvalidoResponde401() throws Exception {
		mockMvc.perform(get("/api/me").header("Authorization", "Bearer no-es-un-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Token inválido o expirado, inicie sesión nuevamente"));
	}

	@Test
	void meDevuelveSubYRoles() throws Exception {
		mockMvc.perform(get("/api/me").with(token("sub-me", "VOLUNTARIO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cognitoSub").value("sub-me"))
				.andExpect(jsonPath("$.roles[0]").value("VOLUNTARIO"));
	}

	@Test
	void voluntarioNoAccedeAEndpointsDeCoordinadorNiDeAdministrador() throws Exception {
		mockMvc.perform(get("/api/reportes/resumen").with(token("sub-v", "VOLUNTARIO")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Acceso restringido: se requiere rol COORDINADOR"));
		mockMvc.perform(get("/api/usuarios").with(token("sub-c", "COORDINADOR")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Acceso restringido: se requiere rol ADMINISTRADOR"));
	}

	@Test
	void rutaInexistenteResponde404ConFormatoDelContrato() throws Exception {
		mockMvc.perform(get("/api/no-existe").with(token("sub-x", "VOLUNTARIO")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Recurso no encontrado"));
	}

	@Test
	void gruposDeCognitoSeConviertenEnRoles() {
		Jwt jwt = Jwt.withTokenValue("test-token")
				.header("alg", "RS256")
				.subject("user-123")
				.claim("cognito:groups", List.of("ADMINISTRADOR", "VOLUNTARIO"))
				.build();

		AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

		assertThat(authentication.getAuthorities())
				.contains(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"),
						new SimpleGrantedAuthority("ROLE_VOLUNTARIO"));
	}
}
