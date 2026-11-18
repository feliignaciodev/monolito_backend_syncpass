package com.backendapp.demo.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Solicitud de creación de un curso (Proyecto). Solo ADMINISTRADOR. */
public record CursoCreateRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede superar 150 caracteres")
        String titulo,

        @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
        String descripcion,

        @NotBlank(message = "El área temática es obligatoria")
        @Size(max = 60, message = "El área temática no puede superar 60 caracteres")
        String areaTematica,

        @NotBlank(message = "La unidad vecinal es obligatoria")
        @Size(max = 100, message = "La unidad vecinal no puede superar 100 caracteres")
        String unidadVecinal,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de término es obligatoria")
        LocalDate fechaTermino,

        @NotBlank(message = "El estado es obligatorio")
        String estado) {
}
