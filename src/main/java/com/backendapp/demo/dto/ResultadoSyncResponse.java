package com.backendapp.demo.dto;

import java.util.List;

/** Respuesta comun de las subidas por lote (asistencias y encuestas). */
public record ResultadoSyncResponse(List<String> aceptados,
        List<String> duplicados,
        List<RechazoDto> rechazados,
        long serverTime) {
}
