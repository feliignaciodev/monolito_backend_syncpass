package com.backendapp.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void cognitoGroupsBecomeRoleAuthorities() {
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
