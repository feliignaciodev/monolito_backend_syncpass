package com.backendapp.demo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.backendapp.demo.service.CognitoService;

/**
 * Base comun de las pruebas: un solo contexto Spring (H2 + Flyway + datos semilla ficticios)
 * con Cognito simulado, para no depender de AWS.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PruebaBase {

	@Autowired
	protected MockMvc mockMvc;

	@MockitoBean
	protected CognitoService cognitoService;

	/** Token JWT simulado con el sub y el grupo de Cognito indicados. */
	protected static JwtRequestPostProcessor token(String sub, String rol) {
		return jwt()
				.jwt(j -> j.subject(sub).claim("cognito:groups", java.util.List.of(rol)))
				.authorities(new SimpleGrantedAuthority("ROLE_" + rol));
	}
}
