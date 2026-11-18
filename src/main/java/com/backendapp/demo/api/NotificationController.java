package com.backendapp.demo.api;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.domain.DispositivoPush;
import com.backendapp.demo.dto.ManualNotificationRequest;
import com.backendapp.demo.dto.NotificationResponse;
import com.backendapp.demo.dto.TokenRequest;
import com.backendapp.demo.service.NotificationService;
import com.backendapp.demo.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * Gestión de notificaciones push.
 * - Token: cualquier usuario autenticado puede registrar su dispositivo.
 * - Manual: solo ADMINISTRADOR puede enviar notificaciones manuales.
 * - Historial: solo ADMINISTRADOR puede ver el historial.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UsuarioService usuarioService;

    public NotificationController(NotificationService notificationService, UsuarioService usuarioService) {
        this.notificationService = notificationService;
        this.usuarioService = usuarioService;
    }

    /**
     * Registra o actualiza el token FCM del dispositivo del usuario autenticado.
     */
    @PostMapping("/token")
    @ResponseStatus(HttpStatus.OK)
    public NotificationResponse registrarToken(
            @Valid @RequestBody TokenRequest request,
            JwtAuthenticationToken authentication) {

        String cognitoSub = authentication.getToken().getSubject();
        Long usuarioId = usuarioService.idLocal(cognitoSub)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        DispositivoPush dispositivo = notificationService.registrarToken(usuarioId, request.fcmToken());

        return new NotificationResponse(
            "Token registrado exitosamente",
            dispositivo.getActualizadoEn() != null ? dispositivo.getActualizadoEn() : LocalDateTime.now()
        );
    }

    /**
     * Envía una notificación manual a todos los usuarios o a roles específicos.
     * Solo ADMINISTRADOR.
     */
    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.OK)
    public NotificationResponse enviarManual(@Valid @RequestBody ManualNotificationRequest request) {
        notificationService.enviarPorRoles(request.titulo(), request.cuerpo(), request.roles());
        return new NotificationResponse(
            "Notificación enviada a " + (request.roles() == null || request.roles().isEmpty() ? "todos los usuarios" : "roles: " + String.join(", ", request.roles())),
            LocalDateTime.now()
        );
    }

    /**
     * Historial de notificaciones enviadas (resumen).
     * Solo ADMINISTRADOR.
     */
    @GetMapping("/historial")
    public List<HistorialItem> historial() {
        // Por ahora devuelve un historial vacío; se puede implementar con tabla de historial en el futuro
        return List.of();
    }

    public record HistorialItem(
        Long id,
        String titulo,
        String cuerpo,
        List<String> roles,
        LocalDateTime enviadoEn,
        int dispositivosEnviados
    ) {
    }
}
