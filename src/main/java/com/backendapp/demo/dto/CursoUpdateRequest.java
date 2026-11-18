package com.backendapp.demo.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** Solicitud de edición de un curso (Proyecto). Solo ADMINISTRADOR. Todos los campos son opcionales. */
public record CursoUpdateRequest(
        @Size(max = 150, message = "El título no puede superar 150 caracteres")
        String titulo,

        @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
        String descripcion,

        @Size(max = 60, message = "El área temática no puede superar 60 caracteres")
        String areaTematica,

        @Size(max = 100, message = "La unidad vecinal no puede superar 100 caracteres")
        String unidadVecinal,

        LocalDate fechaInicio,

        LocalDate fechaTermino,

        String estado) {
}
