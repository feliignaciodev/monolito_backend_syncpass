package com.backendapp.demo.dto;

/**
 * Metricas en vivo de un evento. {@code tasaRespuesta = encuestasRespondidas / asistentes} (0 si no hay asistentes).
 * {@code ultimaMarcacion} es nulo si el evento aun no tiene marcas.
 */
public record MetricasEventoDto(Long eventoId,
        long inscritos,
        long asistentes,
        int cuposMaximos,
        int quorumMinimo,
        boolean quorumAlcanzado,
        long encuestasRespondidas,
        double tasaRespuesta,
        double satisfaccionPromedio,
        double utilidadPromedio,
        Long ultimaMarcacion) {
}
