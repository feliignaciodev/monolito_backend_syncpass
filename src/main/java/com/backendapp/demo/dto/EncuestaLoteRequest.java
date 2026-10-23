package com.backendapp.demo.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/sync/encuestas}. */
public record EncuestaLoteRequest(
        @NotNull(message = "El campo 'encuestas' es obligatorio")
        @Size(max = 500, message = "El lote supera el máximo de 500 encuestas")
        List<EncuestaDto> encuestas) {
}
