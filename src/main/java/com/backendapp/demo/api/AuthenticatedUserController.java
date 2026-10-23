package com.backendapp.demo.api;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthenticatedUserController {

	@GetMapping("/me")
	public AuthenticatedUserResponse me(JwtAuthenticationToken authentication) {
		List<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(authority -> authority.startsWith("ROLE_"))
				.map(authority -> authority.substring("ROLE_".length()))
				.toList();

		return new AuthenticatedUserResponse(authentication.getToken().getSubject(), roles);
	}

	public record AuthenticatedUserResponse(String cognitoSub, List<String> roles) {
	}
}
