package com.backendapp.demo.config;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
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
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
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
}
