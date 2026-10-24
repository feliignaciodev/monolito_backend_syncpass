package com.backendapp.demo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;

import com.backendapp.demo.domain.Rol;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health", "/api/health").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro",
								"/api/auth/registro/confirmar", "/api/auth/registro/reenviar-codigo").permitAll()
						.requestMatchers("/api/usuarios/**").hasRole(Rol.ADMINISTRADOR)
						.requestMatchers("/api/eventos/*/metricas", "/api/reportes/**")
								.hasAnyRole(Rol.COORDINADOR, Rol.ADMINISTRADOR)
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.authenticationEntryPoint(entradaNoAutenticada())
						.accessDeniedHandler(accesoDenegado())
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(entradaNoAutenticada())
						.accessDeniedHandler(accesoDenegado()))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.build();
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Object groupsClaim = jwt.getClaims().get("cognito:groups");
			if (!(groupsClaim instanceof Collection<?> groups)) {
				return List.of();
			}

			return groups.stream()
					.filter(String.class::isInstance)
					.map(String.class::cast)
					.<GrantedAuthority>map(group -> new SimpleGrantedAuthority("ROLE_" + group))
					.toList();
		});
		return converter;
	}

	@Bean
	JwtDecoder jwtDecoder(CognitoProperties cognitoProperties,
			org.springframework.core.env.Environment environment) {
		String issuer = environment.getRequiredProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
		// Sin esto la API arranca igual, pero rechaza todo token como "expirado" y el login responde 502
		if (!StringUtils.hasText(cognitoProperties.clientId()) || !StringUtils.hasText(cognitoProperties.userPoolId())) {
			throw new IllegalStateException("Faltan las variables COGNITO_CLIENT_ID y/o COGNITO_USER_POOL_ID");
		}
		if (!issuer.endsWith("/" + cognitoProperties.userPoolId())) {
			throw new IllegalStateException("COGNITO_ISSUER (" + issuer + ") no corresponde a COGNITO_USER_POOL_ID ("
					+ cognitoProperties.userPoolId() + ")");
		}
		NimbusJwtDecoder decoder = NimbusJwtDecoder
				.withJwkSetUri(issuer + "/.well-known/jwks.json")
				.build();

		OAuth2TokenValidator<Jwt> issuerAndTimestampValidator = JwtValidators.createDefaultWithIssuer(issuer);
		OAuth2TokenValidator<Jwt> accessTokenValidator = new JwtClaimValidator<>("token_use", "access"::equals);
		OAuth2TokenValidator<Jwt> clientIdValidator = new JwtClaimValidator<>("client_id",
				cognitoProperties.clientId()::equals);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				issuerAndTimestampValidator, accessTokenValidator, clientIdValidator));
		return decoder;
	}

	/** 401 con el formato de error del contrato; la app cierra la sesion al recibirlo. */
	private static AuthenticationEntryPoint entradaNoAutenticada() {
		return (request, response, exception) -> {
			String mensaje = request.getHeader(HttpHeaders.AUTHORIZATION) == null
					? "Falta el token de autenticación (Authorization: Bearer <token>)"
					: "Token inválido o expirado, inicie sesión nuevamente";
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			escribirError(response, HttpStatus.UNAUTHORIZED, mensaje);
		};
	}

	private static AccessDeniedHandler accesoDenegado() {
		return (request, response, exception) -> {
			String rol = request.getRequestURI().startsWith("/api/usuarios") ? Rol.ADMINISTRADOR : Rol.COORDINADOR;
			escribirError(response, HttpStatus.FORBIDDEN, "Acceso restringido: se requiere rol " + rol);
		};
	}

	private static void escribirError(HttpServletResponse response, HttpStatus estado, String mensaje)
			throws IOException {
		response.setStatus(estado.value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String json = "{\"error\":\"" + mensaje.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
		response.getWriter().write(json);
	}
}
