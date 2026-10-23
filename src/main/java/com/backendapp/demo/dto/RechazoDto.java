package com.backendapp.demo.dto;

/** Registro rechazado en una sincronizacion, con el motivo en espanol. */
public record RechazoDto(String uuid, String motivo) {
}
