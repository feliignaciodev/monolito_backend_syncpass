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
import com.backendapp.demo.domain.NotificacionHistorial;
import com.backendapp.demo.domain.Usuario;
import com.backendapp.demo.repository.DispositivoPushRepository;
import com.backendapp.demo.repository.NotificacionHistorialRepository;
import com.backendapp.demo.repository.UsuarioRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DispositivoPushRepository dispositivoPushRepository;
    private final UsuarioRepository usuarioRepository;
    private final FcmClient fcmClient;
    private final NotificacionHistorialRepository historialRepository;

    public NotificationService(DispositivoPushRepository dispositivoPushRepository,
            UsuarioRepository usuarioRepository, FcmClient fcmClient,
            NotificacionHistorialRepository historialRepository) {
        this.dispositivoPushRepository = dispositivoPushRepository;
        this.usuarioRepository = usuarioRepository;
        this.fcmClient = fcmClient;
        this.historialRepository = historialRepository;
    }

    /**
     * Registra o actualiza el token FCM de un dispositivo para un usuario.
     */
    @Transactional
    public DispositivoPush registrarToken(Long usuarioId, String fcmToken) {
        return dispositivoPushRepository.findByfcmToken(fcmToken)
            .map(dispositivo -> {
                dispositivo.setUsuario(usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
                dispositivo.setActivo(Boolean.TRUE);
                dispositivo.setActualizadoEn(LocalDateTime.now());
                return dispositivoPushRepository.save(dispositivo);
            })
            .orElseGet(() -> {
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
     * Persiste el envío en el historial para trazabilidad y deduplicación.
     */
    @Transactional
    public void enviarATodosConDatos(String titulo, String cuerpo, String tipo, String eventoId) {
        List<DispositivoPush> dispositivos = dispositivoPushRepository.findByActivoTrue();
        int enviados = 0;
        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotificationWithData(
                dispositivo.getFcmToken(), titulo, cuerpo, tipo, eventoId);
            if (enviado) {
                enviados++;
            } else {
                marcarInactivo(dispositivo);
            }
        }

        NotificacionHistorial historial = new NotificacionHistorial();
        historial.setTitulo(titulo);
        historial.setCuerpo(cuerpo);
        historial.setRoles(null);
        historial.setTipo(tipo);
        historial.setEventoId(eventoId != null ? Long.parseLong(eventoId) : null);
        historial.setEnviadoEn(LocalDateTime.now());
        historial.setDispositivosEnviados(enviados);
        historialRepository.save(historial);
    }

    /**
     * Envía una notificación a todos los usuarios con los roles especificados.
     * Si roles es null o vacío, envía a todos los usuarios activos.
     * Persiste el envío en el historial.
     */
    @Transactional
    public void enviarPorRoles(String titulo, String cuerpo, List<String> roles) {
        List<DispositivoPush> dispositivos;
        if (roles == null || roles.isEmpty()) {
            dispositivos = dispositivoPushRepository.findByActivoTrue();
        } else {
            dispositivos = dispositivoPushRepository.findByActivoTrue().stream()
                .filter(d -> roles.contains(d.getUsuario().getRol().getNombre()))
                .toList();
        }

        int enviados = 0;
        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotification(dispositivo.getFcmToken(), titulo, cuerpo);
            if (enviado) {
                enviados++;
            } else {
                marcarInactivo(dispositivo);
            }
        }

        NotificacionHistorial historial = new NotificacionHistorial();
        historial.setTitulo(titulo);
        historial.setCuerpo(cuerpo);
        historial.setRoles(roles == null || roles.isEmpty() ? null : String.join(",", roles));
        historial.setTipo("manual");
        historial.setEventoId(null);
        historial.setEnviadoEn(LocalDateTime.now());
        historial.setDispositivosEnviados(enviados);
        historialRepository.save(historial);
    }

    /**
     * Envía una notificación con datos a todos los usuarios con los roles especificados.
     * Persiste el envío en el historial.
     */
    @Transactional
    public void enviarPorRolesConDatos(String titulo, String cuerpo, List<String> roles, String tipo, String eventoId) {
        List<DispositivoPush> dispositivos;
        if (roles == null || roles.isEmpty()) {
            dispositivos = dispositivoPushRepository.findByActivoTrue();
        } else {
            dispositivos = dispositivoPushRepository.findByActivoTrue().stream()
                .filter(d -> roles.contains(d.getUsuario().getRol().getNombre()))
                .toList();
        }

        int enviados = 0;
        for (DispositivoPush dispositivo : dispositivos) {
            boolean enviado = fcmClient.sendNotificationWithData(
                dispositivo.getFcmToken(), titulo, cuerpo, tipo, eventoId);
            if (enviado) {
                enviados++;
            } else {
                marcarInactivo(dispositivo);
            }
        }

        NotificacionHistorial historial = new NotificacionHistorial();
        historial.setTitulo(titulo);
        historial.setCuerpo(cuerpo);
        historial.setRoles(roles == null || roles.isEmpty() ? null : String.join(",", roles));
        historial.setTipo(tipo);
        historial.setEventoId(eventoId != null ? Long.parseLong(eventoId) : null);
        historial.setEnviadoEn(LocalDateTime.now());
        historial.setDispositivosEnviados(enviados);
        historialRepository.save(historial);
    }

    /** Verifica si ya se envió un recordatorio de un tipo para un evento. */
    @Transactional(readOnly = true)
    public boolean recordatorioYaEnviado(Long eventoId, String tipo) {
        return historialRepository.existsByEventoIdAndTipo(eventoId, tipo);
    }

    /**
     * Devuelve el historial de notificaciones enviadas, del más reciente al más antiguo.
     */
    @Transactional(readOnly = true)
    public List<NotificacionHistorial> obtenerHistorial() {
        return historialRepository.findAllByOrderByEnviadoEnDesc();
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
