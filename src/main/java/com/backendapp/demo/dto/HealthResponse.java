package com.backendapp.demo.dto;

/** Respuesta de {@code GET /api/health}. */
public record HealthResponse(String status, long serverTime) {
}
