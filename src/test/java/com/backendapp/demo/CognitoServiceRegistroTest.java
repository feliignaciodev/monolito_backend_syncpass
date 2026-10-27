package com.backendapp.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.config.CognitoProperties;
import com.backendapp.demo.service.CognitoService;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/** Llamadas a Cognito del auto-registro, con el cliente de AWS simulado. */
class CognitoServiceRegistroTest {

	private final CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
	private final CognitoService servicio = new CognitoService(cognito,
			new CognitoProperties("client-web", "us-east-1", "us-east-1_pool"));

	@Test
	void registrarHaceSignUpConElClientDelFrontYAgregaAlGrupo() {
		when(cognito.signUp(any(SignUpRequest.class))).thenReturn(SignUpResponse.builder().userSub("sub-1").build());

		assertEquals("sub-1", servicio.registrar("ana@syncpass.test", "Ana", "+56912345678", "BENEFICIARIO", "Clave#2026"));

		ArgumentCaptor<SignUpRequest> signUp = ArgumentCaptor.forClass(SignUpRequest.class);
		verify(cognito).signUp(signUp.capture());
		assertEquals("client-web", signUp.getValue().clientId());
		assertEquals("ana@syncpass.test", signUp.getValue().username());
		assertEquals("+56912345678", signUp.getValue().userAttributes().stream()
				.filter(a -> a.name().equals("phone_number")).findFirst().orElseThrow().value());

		ArgumentCaptor<AdminAddUserToGroupRequest> grupo = ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
		verify(cognito).adminAddUserToGroup(grupo.capture());
		assertEquals("BENEFICIARIO", grupo.getValue().groupName());
		assertEquals("us-east-1_pool", grupo.getValue().userPoolId());
	}

	@Test
	void siNoSePuedeAsignarElGrupoSeEliminaLaCuenta() {
		when(cognito.signUp(any(SignUpRequest.class))).thenReturn(SignUpResponse.builder().userSub("sub-2").build());
		when(cognito.adminAddUserToGroup(any(AdminAddUserToGroupRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().message("Group not found").build());

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> servicio.registrar("sin-grupo@syncpass.test", "Sin Grupo", "+56912345678", "BENEFICIARIO", "Clave#2026"));

		assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
		ArgumentCaptor<AdminDeleteUserRequest> borrado = ArgumentCaptor.forClass(AdminDeleteUserRequest.class);
		verify(cognito).adminDeleteUser(borrado.capture());
		assertEquals("sin-grupo@syncpass.test", borrado.getValue().username());
	}

	@Test
	void emailExistenteEnCognitoResponde409SinAsignarGrupo() {
		when(cognito.signUp(any(SignUpRequest.class))).thenThrow(UsernameExistsException.builder().build());

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> servicio.registrar("ana@syncpass.test", "Ana", "+56912345678", "BENEFICIARIO", "Clave#2026"));

		assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
		assertEquals(CognitoService.CUENTA_EXISTENTE, error.getReason());
		verify(cognito, never()).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
	}

	@Test
	void poolSinAutoRegistroResponde503() {
		when(cognito.signUp(any(SignUpRequest.class))).thenThrow(NotAuthorizedException.builder()
				.message("SignUp is not permitted for this user pool").build());

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> servicio.registrar("ana@syncpass.test", "Ana", "+56912345678", "BENEFICIARIO", "Clave#2026"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
	}

	@Test
	void codigoIncorrectoYEmailInexistenteDanElMismoMensaje() {
		when(cognito.confirmSignUp(any(ConfirmSignUpRequest.class)))
				.thenThrow(CodeMismatchException.builder().build())
				.thenThrow(UserNotFoundException.builder().build());

		ResponseStatusException codigo = assertThrows(ResponseStatusException.class,
				() -> servicio.confirmarRegistro("ana@syncpass.test", "000000"));
		ResponseStatusException inexistente = assertThrows(ResponseStatusException.class,
				() -> servicio.confirmarRegistro("nadie@syncpass.test", "000000"));

		assertEquals(HttpStatus.BAD_REQUEST, codigo.getStatusCode());
		assertEquals(codigo.getReason(), inexistente.getReason());
	}

	@Test
	void reenviarAUnEmailSinCuentaNoRevelaNada() {
		when(cognito.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
				.thenThrow(UserNotFoundException.builder().build());

		servicio.reenviarCodigo("nadie@syncpass.test");
	}
}
