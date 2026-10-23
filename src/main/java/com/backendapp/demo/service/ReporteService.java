package com.backendapp.demo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.domain.Asistencia;
import com.backendapp.demo.domain.EstadoInscripcion;
import com.backendapp.demo.domain.Evento;
import com.backendapp.demo.domain.Proyecto;
import com.backendapp.demo.dto.MetricasEventoDto;
import com.backendapp.demo.dto.ResumenProyectoDto;
import com.backendapp.demo.repository.AsistenciaRepository;
import com.backendapp.demo.repository.EncuestaRepository;
import com.backendapp.demo.repository.EventoRepository;
import com.backendapp.demo.repository.InscripcionRepository;
import com.backendapp.demo.repository.ProyectoRepository;

/** Metricas en vivo por evento, resumen por proyecto y exportacion CSV de asistencias. */
@Service
public class ReporteService {

    /** Encabezado exacto del CSV definido en el contrato. */
    public static final String ENCABEZADO_CSV = "uuid,eventoId,evento,proyecto,participanteId,tipoParticipante,"
            + "timestamp_iso,latitud,longitud,distanciaMetros,estadoValidacion,inscritoEnSitio,registradoPor";

    private final EventoRepository eventoRepository;
    private final ProyectoRepository proyectoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;
    private final DateTimeFormatter formatoIso;

    public ReporteService(EventoRepository eventoRepository, ProyectoRepository proyectoRepository,
            InscripcionRepository inscripcionRepository, AsistenciaRepository asistenciaRepository,
            EncuestaRepository encuestaRepository,
            @Value("${syncpass.zona-horaria:America/Santiago}") String zonaHoraria) {
        this.eventoRepository = eventoRepository;
        this.proyectoRepository = proyectoRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.encuestaRepository = encuestaRepository;
        // ISO-8601 con desfase horario local, ej. 2026-10-15T14:30:00-03:00
        this.formatoIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of(zonaHoraria));
    }

    // ------------------------------------------------------------------ metricas por evento

    @Transactional(readOnly = true)
    public MetricasEventoDto metricasEvento(Long eventoId) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no existe"));

        long inscritos = inscripcionRepository.countByEvento_IdAndEstado(eventoId, EstadoInscripcion.INSCRITO);
        long asistentes = asistenciaRepository.countByEvento_Id(eventoId);
        long encuestas = encuestaRepository.countByEvento_Id(eventoId);

        return new MetricasEventoDto(
                evento.getId(),
                inscritos,
                asistentes,
                evento.getCuposMaximos(),
                evento.getQuorumMinimo(),
                asistentes >= evento.getQuorumMinimo(),
                encuestas,
                tasa(encuestas, asistentes),
                redondear(encuestaRepository.promedioSatisfaccion(eventoId)),
                redondear(encuestaRepository.promedioUtilidad(eventoId)),
                asistenciaRepository.ultimaMarcacion(eventoId));
    }

    // ------------------------------------------------------------------ resumen por proyecto

    @Transactional(readOnly = true)
    public List<ResumenProyectoDto> resumenProyectos() {
        List<ResumenProyectoDto> filas = new ArrayList<>();
        for (Proyecto proyecto : proyectoRepository.findAll(Sort.by("id"))) {
            List<Evento> eventos = eventoRepository.findByProyecto_IdOrderByFechaHoraAsc(proyecto.getId());
            List<Long> idsEventos = eventos.stream().map(Evento::getId).toList();

            long inscritos = 0;
            long asistentes = 0;
            for (Long id : idsEventos) {
                inscritos += inscripcionRepository.countByEvento_IdAndEstado(id, EstadoInscripcion.INSCRITO);
                asistentes += asistenciaRepository.countByEvento_Id(id);
            }
            long encuestas = idsEventos.isEmpty() ? 0 : encuestaRepository.countByEvento_IdIn(idsEventos);
            Double satisfaccion = idsEventos.isEmpty() ? null
                    : encuestaRepository.promedioSatisfaccionDeEventos(idsEventos);

            filas.add(new ResumenProyectoDto(
                    proyecto.getId(),
                    proyecto.getTitulo(),
                    eventos.size(),
                    inscritos,
                    asistentes,
                    tasa(asistentes, inscritos),
                    encuestas,
                    tasa(encuestas, asistentes),
                    redondear(satisfaccion)));
        }
        return filas;
    }

    // ------------------------------------------------------------------ CSV

    @Transactional(readOnly = true)
    public String asistenciasCsv() {
        StringBuilder csv = new StringBuilder(ENCABEZADO_CSV).append("\r\n");
        for (Asistencia a : asistenciaRepository.findAllParaReporte()) {
            csv.append(String.join(",",
                    celda(a.getUuid()),
                    String.valueOf(a.getEvento().getId()),
                    celda(a.getEvento().getNombre()),
                    celda(a.getEvento().getProyecto().getTitulo()),
                    celda(a.getParticipante().getId()),
                    a.getTipoParticipante().name(),
                    formatoIso.format(Instant.ofEpochMilli(a.getTimestamp())),
                    numero(a.getLatitud()),
                    numero(a.getLongitud()),
                    numero(a.getDistanciaMetros()),
                    a.getEstadoValidacion().name(),
                    String.valueOf(a.isInscritoEnSitio()),
                    a.getRegistradoPor() == null ? "" : String.valueOf(a.getRegistradoPor())))
                    .append("\r\n");
        }
        return csv.toString();
    }

    /** Escapa una celda CSV (RFC 4180): entre comillas si contiene coma, comillas o saltos de linea. */
    private static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    /** Numero con punto decimal y sin notacion cientifica (1.0E-4), independiente del locale; vacio si es nulo. */
    private static String numero(Double valor) {
        if (valor == null || !Double.isFinite(valor)) {
            return "";
        }
        return BigDecimal.valueOf(valor).toPlainString();
    }

    // ------------------------------------------------------------------ calculo

    /** Cociente redondeado a 2 decimales; 0 si el denominador es 0. */
    static double tasa(long numerador, long denominador) {
        if (denominador <= 0) {
            return 0.0;
        }
        return redondear((double) numerador / denominador);
    }

    /** Redondea a 2 decimales; 0 si el valor es nulo (sin datos). */
    static double redondear(Double valor) {
        if (valor == null || valor.isNaN()) {
            return 0.0;
        }
        return Math.round(valor * 100.0) / 100.0;
    }
}
