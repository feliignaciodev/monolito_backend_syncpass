package com.backendapp.demo.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backendapp.demo.domain.Asistencia;
import com.backendapp.demo.domain.Encuesta;
import com.backendapp.demo.domain.EstadoValidacion;
import com.backendapp.demo.domain.Evento;
import com.backendapp.demo.domain.Participante;
import com.backendapp.demo.domain.TipoParticipante;
import com.backendapp.demo.dto.AsistenciaDto;
import com.backendapp.demo.dto.EncuestaDto;
import com.backendapp.demo.dto.RechazoDto;
import com.backendapp.demo.dto.ResultadoSyncResponse;
import com.backendapp.demo.repository.AsistenciaRepository;
import com.backendapp.demo.repository.EncuestaRepository;
import com.backendapp.demo.repository.EventoRepository;
import com.backendapp.demo.repository.ParticipanteRepository;

/**
 * Subida por lotes, idempotente, de asistencias y encuestas capturadas offline.
 *
 * <p>Reglas (ver contrato de API de la app):
 * <ul>
 *   <li>{@code uuid} ya recibido: {@code duplicados} (reintento seguro).</li>
 *   <li>Mismo evento + participante con otro {@code uuid}: {@code duplicados}; se conserva la marca mas temprana.</li>
 *   <li>Evento inexistente o campos invalidos: {@code rechazados} con motivo.</li>
 *   <li>Participante desconocido (inscrito en sitio {@code W-...}): se crea con alias "Inscrito en sitio".</li>
 * </ul>
 * Cada registro se evalua por separado: un registro malo no invalida el resto del lote.
 */
@Service
public class SyncService {

    public static final String ALIAS_INSCRITO_EN_SITIO = "Inscrito en sitio";

    private static final int MAX_LARGO_UUID = 64;
    private static final int MAX_LARGO_PARTICIPANTE = 40;
    private static final int MAX_LARGO_DISPOSITIVO = 100;
    private static final int MAX_LARGO_COMENTARIO = 500;

    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;
    private final EventoRepository eventoRepository;
    private final ParticipanteRepository participanteRepository;

    public SyncService(AsistenciaRepository asistenciaRepository, EncuestaRepository encuestaRepository,
            EventoRepository eventoRepository, ParticipanteRepository participanteRepository) {
        this.asistenciaRepository = asistenciaRepository;
        this.encuestaRepository = encuestaRepository;
        this.eventoRepository = eventoRepository;
        this.participanteRepository = participanteRepository;
    }

    // ------------------------------------------------------------------ asistencias

    @Transactional
    public ResultadoSyncResponse sincronizarAsistencias(List<AsistenciaDto> registros, Long usuarioAutenticadoId) {
        Resultado resultado = new Resultado();
        Map<Long, Optional<Evento>> cacheEventos = new HashMap<>();
        long ahora = System.currentTimeMillis();

        for (AsistenciaDto dto : registros) {
            if (dto == null) {
                resultado.rechazar(null, "Registro vacío");
                continue;
            }
            String uuid = limpiar(dto.uuid());
            if (uuid == null) {
                resultado.rechazar(dto.uuid(), "El uuid es obligatorio");
                continue;
            }
            if (uuid.length() > MAX_LARGO_UUID) {
                resultado.rechazar(uuid, "El uuid supera los " + MAX_LARGO_UUID + " caracteres");
                continue;
            }
            // 1) Reintento del mismo registro: duplicado
            if (asistenciaRepository.existsByUuid(uuid)) {
                resultado.duplicados.add(uuid);
                continue;
            }
            // 2) Evento
            if (dto.eventoId() == null) {
                resultado.rechazar(uuid, "El eventoId es obligatorio");
                continue;
            }
            Optional<Evento> evento = cacheEventos.computeIfAbsent(dto.eventoId(), eventoRepository::findById);
            if (evento.isEmpty()) {
                resultado.rechazar(uuid, "Evento no existe");
                continue;
            }
            // 3) Campos
            String participanteId = limpiar(dto.participanteId());
            String error = validarCamposAsistencia(dto, participanteId);
            if (error != null) {
                resultado.rechazar(uuid, error);
                continue;
            }
            TipoParticipante tipoInformado = parsearEnum(TipoParticipante.class, dto.tipoParticipante());
            EstadoValidacion estadoValidacion = parsearEnum(EstadoValidacion.class, dto.estadoValidacion());

            // 4) Participante: si no existe (inscripcion en sitio) se crea
            Optional<Participante> existente = participanteRepository.findById(participanteId);
            Participante participante;
            if (existente.isPresent()) {
                participante = existente.get();
            } else {
                if (tipoInformado == null) {
                    resultado.rechazar(uuid, "El tipoParticipante es obligatorio para un participante nuevo");
                    continue;
                }
                participante = participanteRepository.save(
                        new Participante(participanteId, ALIAS_INSCRITO_EN_SITIO, tipoInformado));
            }
            TipoParticipante tipo = tipoInformado != null ? tipoInformado : participante.getTipo();

            Asistencia nueva = new Asistencia(uuid, evento.get(), participante, tipo, dto.timestamp(),
                    dto.latitud(), dto.longitud(), dto.distanciaMetros(), estadoValidacion,
                    Boolean.TRUE.equals(dto.inscritoEnSitio()),
                    dto.registradoPor() != null ? dto.registradoPor() : usuarioAutenticadoId,
                    limpiar(dto.dispositivoId()), ahora);

            // 5) Misma persona en el mismo evento con otro uuid: duplicado; se conserva la marca mas temprana
            Optional<Asistencia> previa = asistenciaRepository
                    .findByEvento_IdAndParticipante_Id(evento.get().getId(), participanteId);
            if (previa.isPresent()) {
                if (nueva.getTimestamp() < previa.get().getTimestamp()) {
                    previa.get().reemplazarPorMarcaMasTemprana(nueva);
                }
                resultado.duplicados.add(uuid);
                continue;
            }

            asistenciaRepository.save(nueva);
            resultado.aceptados.add(uuid);
        }
        return resultado.aRespuesta();
    }

    private String validarCamposAsistencia(AsistenciaDto dto, String participanteId) {
        if (participanteId == null) {
            return "El participanteId es obligatorio";
        }
        if (participanteId.length() > MAX_LARGO_PARTICIPANTE) {
            return "El participanteId supera los " + MAX_LARGO_PARTICIPANTE + " caracteres";
        }
        if (limpiar(dto.tipoParticipante()) != null
                && parsearEnum(TipoParticipante.class, dto.tipoParticipante()) == null) {
            return "tipoParticipante inválido: " + dto.tipoParticipante()
                    + " (valores permitidos: " + valores(TipoParticipante.class) + ")";
        }
        if (dto.timestamp() == null || dto.timestamp() <= 0) {
            return "El timestamp es obligatorio y debe ser positivo (milisegundos epoch)";
        }
        if (limpiar(dto.estadoValidacion()) == null) {
            return "El estadoValidacion es obligatorio";
        }
        if (parsearEnum(EstadoValidacion.class, dto.estadoValidacion()) == null) {
            return "estadoValidacion inválido: " + dto.estadoValidacion()
                    + " (valores permitidos: " + valores(EstadoValidacion.class) + ")";
        }
        if (dto.latitud() != null && (dto.latitud() < -90 || dto.latitud() > 90)) {
            return "La latitud debe estar entre -90 y 90";
        }
        if (dto.longitud() != null && (dto.longitud() < -180 || dto.longitud() > 180)) {
            return "La longitud debe estar entre -180 y 180";
        }
        if (dto.distanciaMetros() != null && dto.distanciaMetros() < 0) {
            return "La distanciaMetros no puede ser negativa";
        }
        // Jackson convierte valores como 1e400 en Infinity, que pasa el control anterior
        if (dto.distanciaMetros() != null && !Double.isFinite(dto.distanciaMetros())) {
            return "La distanciaMetros debe ser un número finito";
        }
        String dispositivo = limpiar(dto.dispositivoId());
        if (dispositivo != null && dispositivo.length() > MAX_LARGO_DISPOSITIVO) {
            return "El dispositivoId supera los " + MAX_LARGO_DISPOSITIVO + " caracteres";
        }
        return null;
    }

    // ------------------------------------------------------------------ encuestas

    @Transactional
    public ResultadoSyncResponse sincronizarEncuestas(List<EncuestaDto> encuestas) {
        Resultado resultado = new Resultado();
        Map<Long, Optional<Evento>> cacheEventos = new HashMap<>();
        long ahora = System.currentTimeMillis();

        for (EncuestaDto dto : encuestas) {
            if (dto == null) {
                resultado.rechazar(null, "Encuesta vacía");
                continue;
            }
            String uuid = limpiar(dto.uuid());
            if (uuid == null) {
                resultado.rechazar(dto.uuid(), "El uuid es obligatorio");
                continue;
            }
            if (uuid.length() > MAX_LARGO_UUID) {
                resultado.rechazar(uuid, "El uuid supera los " + MAX_LARGO_UUID + " caracteres");
                continue;
            }
            if (encuestaRepository.existsByUuid(uuid)) {
                resultado.duplicados.add(uuid);
                continue;
            }
            if (dto.eventoId() == null) {
                resultado.rechazar(uuid, "El eventoId es obligatorio");
                continue;
            }
            Optional<Evento> evento = cacheEventos.computeIfAbsent(dto.eventoId(), eventoRepository::findById);
            if (evento.isEmpty()) {
                resultado.rechazar(uuid, "Evento no existe");
                continue;
            }
            String participanteId = limpiar(dto.participanteId());
            String comentario = limpiar(dto.comentario());
            String error = validarCamposEncuesta(dto, participanteId, comentario);
            if (error != null) {
                resultado.rechazar(uuid, error);
                continue;
            }
            // Una encuesta por participante identificado y evento (las anonimas no se pueden deduplicar)
            if (participanteId != null
                    && encuestaRepository.existsByEvento_IdAndParticipanteId(evento.get().getId(), participanteId)) {
                resultado.duplicados.add(uuid);
                continue;
            }
            encuestaRepository.save(new Encuesta(uuid, evento.get(), participanteId, dto.satisfaccion(),
                    dto.utilidad(), comentario, dto.fechaEnvio(), ahora));
            resultado.aceptados.add(uuid);
        }
        return resultado.aRespuesta();
    }

    private String validarCamposEncuesta(EncuestaDto dto, String participanteId, String comentario) {
        if (dto.satisfaccion() == null || dto.satisfaccion() < 1 || dto.satisfaccion() > 5) {
            return "La satisfacción debe ser un número entre 1 y 5";
        }
        if (dto.utilidad() == null || dto.utilidad() < 1 || dto.utilidad() > 5) {
            return "La utilidad debe ser un número entre 1 y 5";
        }
        if (comentario != null && comentario.length() > MAX_LARGO_COMENTARIO) {
            return "El comentario supera los " + MAX_LARGO_COMENTARIO + " caracteres";
        }
        if (dto.fechaEnvio() == null || dto.fechaEnvio() <= 0) {
            return "La fechaEnvio es obligatoria y debe ser positiva (milisegundos epoch)";
        }
        if (participanteId != null && participanteId.length() > MAX_LARGO_PARTICIPANTE) {
            return "El participanteId supera los " + MAX_LARGO_PARTICIPANTE + " caracteres";
        }
        return null;
    }

    // ------------------------------------------------------------------ utilidades

    /** Recorta espacios; devuelve null si el texto es nulo o vacio. */
    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String recortado = texto.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    /** Convierte texto a enum sin lanzar excepcion; null si es vacio o no corresponde a ningun valor. */
    private static <E extends Enum<E>> E parsearEnum(Class<E> tipo, String texto) {
        String limpio = limpiar(texto);
        if (limpio == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, limpio.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> String valores(Class<E> tipo) {
        return String.join(", ", Arrays.stream(tipo.getEnumConstants()).map(Enum::name).toList());
    }

    /** Acumulador de la respuesta de un lote. */
    private static final class Resultado {
        private final List<String> aceptados = new ArrayList<>();
        private final List<String> duplicados = new ArrayList<>();
        private final List<RechazoDto> rechazados = new ArrayList<>();

        void rechazar(String uuid, String motivo) {
            rechazados.add(new RechazoDto(uuid, motivo));
        }

        ResultadoSyncResponse aRespuesta() {
            return new ResultadoSyncResponse(aceptados, duplicados, rechazados, System.currentTimeMillis());
        }
    }
}
