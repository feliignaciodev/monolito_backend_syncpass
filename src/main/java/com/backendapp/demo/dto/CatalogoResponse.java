package com.backendapp.demo.dto;

import java.util.List;

/** Respuesta de {@code GET /api/sync/catalogo} (cache offline de la app). */
public record CatalogoResponse(long serverTime,
        List<ProyectoDto> proyectos,
        List<EventoDto> eventos,
        List<ParticipanteDto> participantes,
        List<InscripcionDto> inscripciones) {
}
