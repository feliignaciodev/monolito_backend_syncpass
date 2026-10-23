package com.backendapp.demo.dto;

/** Evento en el catalogo. {@code fechaHora} en milisegundos epoch UTC. */
public record EventoDto(Long id, Long proyectoId, String nombre, long fechaHora, int cuposMaximos, int quorumMinimo,
        String recinto, String direccion, double latitud, double longitud, int radioMetros) {
}
