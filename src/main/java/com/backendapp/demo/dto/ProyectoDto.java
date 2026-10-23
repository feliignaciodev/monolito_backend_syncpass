package com.backendapp.demo.dto;

/** Proyecto en el catalogo. Las fechas van como texto {@code yyyy-MM-dd}. */
public record ProyectoDto(Long id, String titulo, String descripcion, String areaTematica, String unidadVecinal,
        String fechaInicio, String fechaTermino, String estado) {
}
