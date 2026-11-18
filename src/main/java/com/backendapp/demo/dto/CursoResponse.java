package com.backendapp.demo.dto;

/** Respuesta de lectura de un curso (Proyecto). Las fechas van como texto {@code yyyy-MM-dd}. */
public record CursoResponse(
        Long id,
        String titulo,
        String descripcion,
        String areaTematica,
        String unidadVecinal,
        String fechaInicio,
        String fechaTermino,
        String estado) {
}
