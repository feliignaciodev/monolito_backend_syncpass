package com.backendapp.demo.dto;

/** Forma unica de error de la API: {@code {"error": "mensaje legible"}}. */
public record ErrorResponse(String error) {
}
