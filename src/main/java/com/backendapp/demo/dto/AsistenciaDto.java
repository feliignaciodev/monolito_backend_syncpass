package com.backendapp.demo.dto;

/**
 * Marca de asistencia enviada por la app. Los enumerados se reciben como texto para poder
 * rechazar el registro individual (en vez de todo el lote) si traen un valor invalido.
 */
public record AsistenciaDto(String uuid,
        Long eventoId,
        String participanteId,
        String tipoParticipante,
        Long timestamp,
        Double latitud,
        Double longitud,
        Double distanciaMetros,
        String estadoValidacion,
        Boolean inscritoEnSitio,
        Long registradoPor,
        String dispositivoId) {
}
