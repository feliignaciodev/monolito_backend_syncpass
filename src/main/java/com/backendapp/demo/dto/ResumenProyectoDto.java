package com.backendapp.demo.dto;

/** Fila del resumen por proyecto (insumo del informe de cierre). */
public record ResumenProyectoDto(Long proyectoId,
        String titulo,
        long eventos,
        long inscritos,
        long asistentes,
        double tasaAsistencia,
        long encuestas,
        double tasaRespuesta,
        double satisfaccionPromedio) {
}
