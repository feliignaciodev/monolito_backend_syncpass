package com.backendapp.demo.event;

import org.springframework.context.ApplicationEvent;

import com.backendapp.demo.domain.EstadoInscripcion;

/**
 * Evento disparado cuando cambia el estado de una inscripción.
 * Permite enviar notificaciones al participante afectado.
 */
public class InscripcionStateChangedEvent extends ApplicationEvent {

    private final Long inscripcionId;
    private final Long eventoId;
    private final String eventoNombre;
    private final String participanteId;
    private final EstadoInscripcion nuevoEstado;

    public InscripcionStateChangedEvent(Object source, Long inscripcionId, Long eventoId,
            String eventoNombre, String participanteId, EstadoInscripcion nuevoEstado) {
        super(source);
        this.inscripcionId = inscripcionId;
        this.eventoId = eventoId;
        this.eventoNombre = eventoNombre;
        this.participanteId = participanteId;
        this.nuevoEstado = nuevoEstado;
    }

    public Long getInscripcionId() {
        return inscripcionId;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public String getEventoNombre() {
        return eventoNombre;
    }

    public String getParticipanteId() {
        return participanteId;
    }

    public EstadoInscripcion getNuevoEstado() {
        return nuevoEstado;
    }
}
