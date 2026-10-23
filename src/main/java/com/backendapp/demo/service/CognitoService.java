package com.backendapp.demo.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.config.CognitoProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Acceso a Amazon Cognito. Traduce los errores de Cognito a {@link ResponseStatusException}
 * con mensajes en espanol, que el manejador global devuelve como {@code {"error": "..."}}.
 */
@Service
public class CognitoService {

    public static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";

    private static final Logger log = LoggerFactory.getLogger(CognitoService.class);

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;

    public CognitoService(CognitoIdentityProviderClient cognito, CognitoProperties properties) {
        this.cognito = cognito;
        this.properties = properties;
    }

    public record LoginResult(String accessToken, String cognitoSub, String username, List<String> groups) {
    }

    public record Perfil(String nombre, String email) {
    }

    public LoginResult login(String username, String password) {
        InitiateAuthResponse response;
        try {
            response = cognito.initiateAuth(InitiateAuthRequest.builder()
                    .clientId(properties.clientId())
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .authParameters(Map.of(
                            "USERNAME", username,
                            "PASSWORD", password))
                    .build());
        } catch (NotAuthorizedException e) {
            String detalle = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : null;
            if (detalle != null && detalle.toLowerCase(Locale.ROOT).contains("disabled")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario desactivado");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        } catch (UserNotConfirmedException | PasswordResetRequiredException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta tiene una acción pendiente (confirmación o cambio de contraseña); contacte al administrador");
        } catch (SdkException e) {
            throw servicioNoDisponible("iniciar sesion", e);
        }

        // Si Cognito pide un desafio (ej. NEW_PASSWORD_REQUIRED) no entrega tokens: antes esto provocaba un NPE
        if (response.challengeName() != null || response.authenticationResult() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta debe definir una contraseña permanente antes de usar la app; contacte al administrador");
        }

        String accessToken = response.authenticationResult().accessToken();
        try {
            JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();
            List<String> groups = new ArrayList<>();
            if (claims.getClaim("cognito:groups") instanceof List<?> list) {
                list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(groups::add);
            }
            String tokenUsername = claims.getStringClaim("username");
            return new LoginResult(accessToken, claims.getSubject(),
                    tokenUsername != null ? tokenUsername : username, groups);
        } catch (ParseException e) {
            throw new IllegalStateException("No se pudo decodificar el token de Cognito", e);
        }
    }

    /** Nombre y email del usuario en Cognito; si no se pueden leer, usa el username como nombre. */
    public Perfil obtenerPerfil(String username) {
        try {
            var user = cognito.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(username)
                    .build());
            String nombre = atributo(user.userAttributes(), "name");
            return new Perfil(nombre != null ? nombre : username, atributo(user.userAttributes(), "email"));
        } catch (SdkException e) {
            log.warn("No se pudo leer el perfil de Cognito de {}: {}", username, e.getMessage());
            return new Perfil(username, null);
        }
    }

    /**
     * Crea el usuario con contrasena permanente y lo agrega al grupo del rol. Si falla un paso
     * posterior a la creacion, elimina el usuario para no dejarlo a medias. Devuelve el {@code sub}.
     */
    public String createUser(String username, String email, String nombre, String rol, String password) {
        List<AttributeType> atributos = new ArrayList<>();
        atributos.add(AttributeType.builder().name("name").value(nombre).build());
        if (email != null) {
            atributos.add(AttributeType.builder().name("email").value(email).build());
            atributos.add(AttributeType.builder().name("email_verified").value("true").build());
        }

        String sub;
        try {
            var creado = cognito.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(username)
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(atributos)
                    .build());
            sub = atributo(creado.user().attributes(), "sub");
        } catch (UsernameExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya existe en Cognito");
        } catch (SdkException e) {
            throw servicioNoDisponible("crear usuario", e);
        }

        try {
            cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(username)
                    .password(password)
                    .permanent(true)
                    .build());
            cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(username)
                    .groupName(rol)
                    .build());
            if (sub == null) {
                throw new IllegalStateException("Cognito no devolvio sub para " + username);
            }
            return sub;
        } catch (RuntimeException e) {
            deleteUser(username);
            if (e instanceof InvalidPasswordException) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La contraseña no cumple la política del User Pool de Cognito");
            }
            if (e instanceof SdkException sdk) {
                throw servicioNoDisponible("configurar usuario", sdk);
            }
            throw e;
        }
    }

    /** Elimina el usuario de Cognito (compensacion). Los errores solo se registran. */
    public void deleteUser(String username) {
        try {
            cognito.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(username)
                    .build());
        } catch (SdkException e) {
            log.error("No se pudo eliminar el usuario {} de Cognito durante la compensacion; revisar manualmente",
                    username, e);
        }
    }

    public void setActivo(String username, boolean activo) {
        try {
            if (activo) {
                cognito.adminEnableUser(AdminEnableUserRequest.builder()
                        .userPoolId(properties.userPoolId())
                        .username(username)
                        .build());
            } else {
                cognito.adminDisableUser(AdminDisableUserRequest.builder()
                        .userPoolId(properties.userPoolId())
                        .username(username)
                        .build());
            }
        } catch (SdkException e) {
            throw servicioNoDisponible("actualizar estado", e);
        }
    }

    private static String atributo(List<AttributeType> atributos, String nombre) {
        return atributos.stream()
                .filter(a -> nombre.equals(a.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    private static ResponseStatusException servicioNoDisponible(String operacion, SdkException e) {
        log.error("Error de Cognito al {}", operacion, e);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Servicio de autenticación no disponible", e);
    }
}
