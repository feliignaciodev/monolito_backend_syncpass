package com.backendapp.demo.service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backendapp.demo.domain.Evento;
import com.backendapp.demo.domain.Proyecto;
import com.backendapp.demo.dto.CatalogoResponse;
import com.backendapp.demo.dto.EventoDto;
import com.backendapp.demo.dto.InscripcionDto;
import com.backendapp.demo.dto.ParticipanteDto;
import com.backendapp.demo.dto.ProyectoDto;
import com.backendapp.demo.repository.EventoRepository;
import com.backendapp.demo.repository.InscripcionRepository;
import com.backendapp.demo.repository.ParticipanteRepository;
import com.backendapp.demo.repository.ProyectoRepository;

/** Arma el catalogo completo que la app descarga para trabajar offline. */
@Service
public class CatalogoService {

    /** Formato {@code yyyy-MM-dd}. */
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProyectoRepository proyectoRepository;
    private final EventoRepository eventoRepository;
    private final ParticipanteRepository participanteRepository;
    private final InscripcionRepository inscripcionRepository;

    public CatalogoService(ProyectoRepository proyectoRepository, EventoRepository eventoRepository,
            ParticipanteRepository participanteRepository, InscripcionRepository inscripcionRepository) {
        this.proyectoRepository = proyectoRepository;
        this.eventoRepository = eventoRepository;
        this.participanteRepository = participanteRepository;
        this.inscripcionRepository = inscripcionRepository;
    }

    @Transactional(readOnly = true)
    public CatalogoResponse obtenerCatalogo() {
        List<ProyectoDto> proyectos = proyectoRepository.findAll(Sort.by("id")).stream()
                .map(CatalogoService::aDto)
                .toList();
        List<EventoDto> eventos = eventoRepository.findAll(Sort.by("id")).stream()
                .map(CatalogoService::aDto)
                .toList();
        List<ParticipanteDto> participantes = participanteRepository.findAll(Sort.by("id")).stream()
                .map(p -> new ParticipanteDto(p.getId(), p.getAlias(), p.getTipo().name()))
                .toList();
        List<InscripcionDto> inscripciones = inscripcionRepository.findAllConRelaciones().stream()
                .map(i -> new InscripcionDto(i.getEvento().getId(), i.getParticipante().getId(),
                        i.getEstado().name()))
                .toList();
        return new CatalogoResponse(System.currentTimeMillis(), proyectos, eventos, participantes, inscripciones);
    }

    private static ProyectoDto aDto(Proyecto p) {
        return new ProyectoDto(p.getId(), p.getTitulo(), p.getDescripcion(), p.getAreaTematica(),
                p.getUnidadVecinal(), p.getFechaInicio().format(FORMATO_FECHA),
                p.getFechaTermino().format(FORMATO_FECHA), p.getEstado().name());
    }

    private static EventoDto aDto(Evento e) {
        return new EventoDto(e.getId(), e.getProyecto().getId(), e.getNombre(), e.getFechaHora(),
                e.getCuposMaximos(), e.getQuorumMinimo(), e.getRecinto(), e.getDireccion(),
                e.getLatitud(), e.getLongitud(), e.getRadioMetros());
    }
}
