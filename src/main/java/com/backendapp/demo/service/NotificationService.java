package com.backendapp.demo.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.domain.DispositivoPush;
import com.backendapp.demo.domain.Usuario;
import com.backendapp.demo.repository.DispositivoPushRepository;
import com.backendapp.demo.repository.UsuarioRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DispositivoPushRepository dispositivoPushRepository;
    private final UsuarioRepository usuarioRepository;
    private final FcmClient fcmClient;

    public NotificationService(DispositivoPushRepository dispositivoPushRepository,
            UsuarioRepository usuarioRepository, FcmClient fcmClient) {
        this.dispositivoPushRepository = dispositivoPushRepository;
        this.usuarioRepository = usuarioRepository;
        this.fcmClient = fcmClient;
    }

    /**
     * Registra o actualiza el token FCM de un dispositivo para un usuario.
     */
    @Transactional
    public DispositivoPush registrarToken(Long usuarioId, String fcmToken) {
        // Buscar si ya existe un dispositivo con este token
        return dispositivoPushRepository.findByfcmToken(fcmToken)
            .map(dispositivo -> {
                // Token existe, actualizar si es del mismo usuario o reasignar
                dispositivo.setUsuario(usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
                dispositivo.setActivo(Boolean.TRUE);
                dispositivo.setActualizadoEn(LocalDateTime.now());
                return dispositivoPushRepository.save(dispositivo);
            })
            .orElseGet(() -> {
                // Crear nuevo registro
                DispositivoPush dispositivo = new DispositivoPush();
                dispositivo.setFcmToken(fcmToken);
                dispositivo.setUsuario(usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
                dispositivo.setActivo(Boolean.TRUE);
                dispositivo.setCreadoEn(LocalDateTime.now());
                dispositivo.setActualizadoEn(LocalDateTime.now());
                return dispositivoPushRepository.save(dispositivo);
            });
    }

    /**
     * Envía una notificación a todos los dispositivos activos de un usuario.
     */
    public void enviarAUsuario(Long usuarioId, String titulo, String cuerpo) {
        List<DispositivoPush> dispositivos = dispositivoPushRepository.findByUsuario_IdAndActivoTrue(usuarioId);
        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotification(dispositivo.getFcmToken(), titulo, cuerpo);
            if (!enviado) {
                // Marcar token como inactivo si falla
                marcarInactivo(dispositivo);
            }
        }
    }

    /**
     * Envía una notificación con datos adicionales a todos los dispositivos activos de un usuario.
     */
    public void enviarAUsuarioConDatos(Long usuarioId, String titulo, String cuerpo, String tipo, String eventoId) {
        List<DispositivoPush> dispositivos = dispositivoPushRepository.findByUsuario_IdAndActivoTrue(usuarioId);
        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotificationWithData(
                dispositivo.getFcmToken(), titulo, cuerpo, tipo, eventoId);
            if (!enviado) {
                marcarInactivo(dispositivo);
            }
        }
    }

    /**
     * Envía una notificación a todos los dispositivos activos (por token).
     * Útil cuando no hay relación directa usuario-participante.
     */
    public void enviarATodosConDatos(String titulo, String cuerpo, String tipo, String eventoId) {
        List<DispositivoPush> dispositivos = dispositivoPushRepository.findByActivoTrue();
        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotificationWithData(
                dispositivo.getFcmToken(), titulo, cuerpo, tipo, eventoId);
            if (!enviado) {
                marcarInactivo(dispositivo);
            }
        }
    }

    /**
     * Envía una notificación a todos los usuarios con los roles especificados.
     * Si roles es null o vacío, envía a todos los usuarios activos.
     */
    @Transactional(readOnly = true)
    public void enviarPorRoles(String titulo, String cuerpo, List<String> roles) {
        List<DispositivoPush> dispositivos;
        if (roles == null || roles.isEmpty()) {
            dispositivos = dispositivoPushRepository.findByActivoTrue();
        } else {
            // Obtener usuarios por roles y luego sus dispositivos
            dispositivos = dispositivoPushRepository.findByActivoTrue().stream()
                .filter(d -> roles.contains(d.getUsuario().getRol().getNombre()))
                .toList();
        }

        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotification(dispositivo.getFcmToken(), titulo, cuerpo);
            if (!enviado) {
                marcarInactivo(dispositivo);
            }
        }
    }

    /**
     * Envía una notificación con datos a todos los usuarios con los roles especificados.
     */
    @Transactional(readOnly = true)
    public void enviarPorRolesConDatos(String titulo, String cuerpo, List<String> roles, String tipo, String eventoId) {
        List<DispositivoPush> dispositivos;
        if (roles == null || roles.isEmpty()) {
            dispositivos = dispositivoPushRepository.findByActivoTrue();
        } else {
            dispositivos = dispositivoPushRepository.findByActivoTrue().stream()
                .filter(d -> roles.contains(d.getUsuario().getRol().getNombre()))
                .toList();
        }

        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotificationWithData(
                dispositivo.getFcmToken(), titulo, cuerpo, tipo, eventoId);
            if (!enviado) {
                marcarInactivo(dispositivo);
            }
        }
    }

    /**
     * Marca un dispositivo como inactivo cuando su token es inválido.
     */
    @Transactional
    protected void marcarInactivo(DispositivoPush dispositivo) {
        dispositivo.setActivo(Boolean.FALSE);
        dispositivo.setActualizadoEn(LocalDateTime.now());
        dispositivoPushRepository.save(dispositivo);
        log.warn("Token FCM marcado como inactivo para usuario {}", dispositivo.getUsuario().getId());
    }
}
