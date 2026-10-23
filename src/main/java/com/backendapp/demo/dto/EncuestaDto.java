package com.backendapp.demo.dto;

/** Encuesta enviada por la app. {@code participanteId} nulo = encuesta anonima. */
public record EncuestaDto(String uuid,
        Long eventoId,
        String participanteId,
        Integer satisfaccion,
        Integer utilidad,
        String comentario,
        Long fechaEnvio) {
}
