package com.backendapp.demo.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.backendapp.demo.domain.EstadoInscripcion;
import com.backendapp.demo.service.NotificationService;

/**
 * Escucha eventos de cambio de estado de inscripción y envía notificaciones push.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onInscripcionStateChanged(InscripcionStateChangedEvent event) {
        String titulo;
        String cuerpo;

        switch (event.getNuevoEstado()) {
            case INSCRITO:
                titulo = "Inscripción confirmada";
                cuerpo = "Tu inscripción al evento '" + event.getEventoNombre() + "' ha sido confirmada.";
                break;
            case LISTA_ESPERA:
                titulo = "Lista de espera";
                cuerpo = "Tu inscripción al evento '" + event.getEventoNombre() + "' está en lista de espera.";
                break;
            case CANCELADO:
                titulo = "Inscripción cancelada";
                cuerpo = "Tu inscripción al evento '" + event.getEventoNombre() + "' ha sido cancelada.";
                break;
            default:
                log.warn("Estado de inscripción no manejado: {}", event.getNuevoEstado());
                return;
        }

        // Enviar notificación a todos los dispositivos (filtrado por participante en el futuro)
        notificationService.enviarATodosConDatos(
            titulo, cuerpo, "inscripcion_cambiada", event.getEventoId().toString()
        );
        log.info("Notificación enviada para cambio de estado: {} -> {} (evento: {})",
            event.getParticipanteId(), event.getNuevoEstado(), event.getEventoNombre());
    }
}
