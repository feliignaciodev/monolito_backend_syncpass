package com.backendapp.demo.scheduler;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backendapp.demo.domain.Evento;
import com.backendapp.demo.domain.Inscripcion;
import com.backendapp.demo.domain.EstadoInscripcion;
import com.backendapp.demo.repository.EventoRepository;
import com.backendapp.demo.repository.InscripcionRepository;
import com.backendapp.demo.service.NotificationService;

/**
 * Envía recordatorios push antes de los eventos.
 * Se ejecuta cada 15 minutos y busca eventos que comienzan en 1 hora y 1 día.
 * Evita duplicados: cada recordatorio (1h o 1d) se envía una sola vez por evento.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final EventoRepository eventoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final NotificationService notificationService;

    // Umbrales de recordatorio: 1 hora y 1 día antes del evento
    private static final long RECORDATORIO_1HORA_MS = 60 * 60 * 1000L;
    private static final long RECORDATORIO_1DIA_MS = 24 * 60 * 60 * 1000L;

    public NotificationScheduler(EventoRepository eventoRepository,
            InscripcionRepository inscripcionRepository,
            NotificationService notificationService) {
        this.eventoRepository = eventoRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000L) // cada 15 minutos
    public void enviarRecordatorios() {
        log.debug("Ejecutando scheduler de recordatorios de notificaciones");

        long ahora = Instant.now().toEpochMilli();

        // Recordatorio a 1 hora: solo eventos que comienzan en menos de 1 hora
        enviarRecordatorioParaVentana(ahora, 0, RECORDATORIO_1HORA_MS, "1 hora",
            "El evento comienza en 1 hora. ¡Prepárate para asistir!",
            "recordatorio_1h");

        // Recordatorio a 1 día: eventos entre 1 hora y 1 día
        enviarRecordatorioParaVentana(ahora, RECORDATORIO_1HORA_MS, RECORDATORIO_1DIA_MS, "1 día",
            "El evento comienza mañana. ¡No olvides asistir!",
            "recordatorio_1d");
    }

    private void enviarRecordatorioParaVentana(long ahora, long desdeMs, long hastaMs, String tiempo,
            String mensajeCuerpo, String tipo) {
        long desde = ahora + desdeMs;
        long hasta = ahora + hastaMs;

        List<Evento> eventos = eventoRepository.findBetween(desde, hasta);

        for (Evento evento : eventos) {
            // Deduplicación: no enviar si ya se envió este tipo de recordatorio para este evento
            if (notificationService.recordatorioYaEnviado(evento.getId(), tipo)) {
                log.debug("Recordatorio {} ya enviado para evento {}, omitiendo", tipo, evento.getNombre());
                continue;
            }

            log.info("Enviando recordatorio de {} para evento: {}", tiempo, evento.getNombre());

            List<Inscripcion> inscripciones = inscripcionRepository.findAllConRelaciones().stream()
                .filter(i -> i.getEvento().getId().equals(evento.getId()))
                .filter(i -> i.getEstado() == EstadoInscripcion.INSCRITO)
                .toList();

            String titulo = "Recordatorio: " + evento.getNombre();
            String cuerpo = mensajeCuerpo + " Recinto: " + evento.getRecinto();

            notificationService.enviarATodosConDatos(
                titulo, cuerpo, tipo, evento.getId().toString()
            );
            log.info("Recordatorio enviado para evento {} ({} inscritos)", evento.getNombre(), inscripciones.size());
        }
    }
}
