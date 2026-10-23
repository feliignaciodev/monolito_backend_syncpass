package com.backendapp.demo.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/sync/asistencias}. */
public record AsistenciaLoteRequest(
        @NotNull(message = "El campo 'registros' es obligatorio")
        @Size(max = 500, message = "El lote supera el máximo de 500 registros")
        List<AsistenciaDto> registros) {
}
