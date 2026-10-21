package com.backendapp.demo.service;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.backendapp.demo.config.CognitoProperties;
import com.nimbusds.jwt.SignedJWT;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;

@Service
public class CognitoService {

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;

    public CognitoService(CognitoIdentityProviderClient cognito, CognitoProperties properties) {
        this.cognito = cognito;
        this.properties = properties;
    }

    public record LoginResult(String accessToken, String cognitoSub, List<String> groups, int expiresIn) {
    }

    public LoginResult login(String email, String password) {
        var response = cognito.initiateAuth(InitiateAuthRequest.builder()
                .clientId(properties.clientId())
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .authParameters(Map.of(
                        "USERNAME", email,
                        "PASSWORD", password))
                .build());

        var authResult = response.authenticationResult();
        String accessToken = authResult.accessToken();
        int expiresIn = authResult.expiresIn();

        String sub;
        List<String> groups;
        try {
            SignedJWT jwt = SignedJWT.parse(accessToken);
            sub = jwt.getJWTClaimsSet().getSubject();
            Object claim = jwt.getJWTClaimsSet().getClaim("cognito:groups");
            groups = (claim instanceof List<?> list)
                    ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                    : List.of();
        } catch (ParseException e) {
            throw new IllegalStateException("No se pudo decodificar el token de Cognito", e);
        }

        return new LoginResult(accessToken, sub, groups, expiresIn);
    }

    public String createUser(String email, String nombre, String rol, String password) {
        String tempPassword = (password == null || password.isBlank())
                ? "Tmp" + UUID.randomUUID() + "aA1!"
                : password;

        cognito.adminCreateUser(AdminCreateUserRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(email)
                .temporaryPassword(tempPassword)
                .messageAction(MessageActionType.SUPPRESS)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(nombre).build())
                .build());

        if (password != null && !password.isBlank()) {
            cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build());
        }

        cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(email)
                .groupName(rol)
                .build());

        var user = cognito.adminGetUser(AdminGetUserRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(email)
                .build());

        return user.userAttributes().stream()
                .filter(a -> "sub".equals(a.name()))
                .map(software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType::value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cognito no devolvio sub para " + email));
    }

    public void setActivo(String username, boolean activo) {
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
    }
}