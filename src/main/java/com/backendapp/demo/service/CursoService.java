package com.backendapp.demo.service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.domain.EstadoProyecto;
import com.backendapp.demo.domain.Proyecto;
import com.backendapp.demo.dto.CursoCreateRequest;
import com.backendapp.demo.dto.CursoResponse;
import com.backendapp.demo.dto.CursoUpdateRequest;
import com.backendapp.demo.repository.ProyectoRepository;

/** CRUD de cursos (Proyectos). Solo ADMINISTRADOR puede crear, editar y desactivar. */
@Service
public class CursoService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProyectoRepository proyectoRepository;

    public CursoService(ProyectoRepository proyectoRepository) {
        this.proyectoRepository = proyectoRepository;
    }

    /** Lista los cursos activos (excluye FINALIZADO). */
    @Transactional(readOnly = true)
    public List<CursoResponse> listar() {
        return proyectoRepository.findByEstadoNot(EstadoProyecto.FINALIZADO).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Obtiene un curso por ID. */
    @Transactional(readOnly = true)
    public CursoResponse obtener(Long id) {
        Proyecto proyecto = proyectoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso no encontrado"));
        return toResponse(proyecto);
    }

    /** Crea un nuevo curso. Solo ADMINISTRADOR. */
    @Transactional
    public CursoResponse crear(CursoCreateRequest request) {
        EstadoProyecto estado = parseEstado(request.estado());

        Proyecto proyecto = new Proyecto(
                request.titulo().trim(),
                request.descripcion(),
                request.areaTematica().trim(),
                request.unidadVecinal().trim(),
                request.fechaInicio(),
                request.fechaTermino(),
                estado);

        proyectoRepository.save(proyecto);
        return toResponse(proyecto);
    }

    /** Edita un curso existente. Solo ADMINISTRADOR. Campos nulos se ignoran. */
    @Transactional
    public CursoResponse editar(Long id, CursoUpdateRequest request) {
        Proyecto proyecto = proyectoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso no encontrado"));

        if (request.titulo() != null) {
            proyecto.setTitulo(request.titulo().trim());
        }
        if (request.descripcion() != null) {
            proyecto.setDescripcion(request.descripcion());
        }
        if (request.areaTematica() != null) {
            proyecto.setAreaTematica(request.areaTematica().trim());
        }
        if (request.unidadVecinal() != null) {
            proyecto.setUnidadVecinal(request.unidadVecinal().trim());
        }
        if (request.fechaInicio() != null) {
            proyecto.setFechaInicio(request.fechaInicio());
        }
        if (request.fechaTermino() != null) {
            proyecto.setFechaTermino(request.fechaTermino());
        }
        if (request.estado() != null) {
            proyecto.setEstado(parseEstado(request.estado()));
        }

        proyectoRepository.save(proyecto);
        return toResponse(proyecto);
    }

    /** Desactiva un curso cambiando su estado a FINALIZADO. Solo ADMINISTRADOR. */
    @Transactional
    public CursoResponse desactivar(Long id) {
        Proyecto proyecto = proyectoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso no encontrado"));

        if (proyecto.getEstado() == EstadoProyecto.FINALIZADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El curso ya está desactivado");
        }

        proyecto.setEstado(EstadoProyecto.FINALIZADO);
        proyectoRepository.save(proyecto);
        return toResponse(proyecto);
    }

    private static EstadoProyecto parseEstado(String valor) {
        try {
            return EstadoProyecto.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estado inválido: " + valor + " (valores permitidos: PLANIFICADO, EN_CURSO, FINALIZADO)");
        }
    }

    private CursoResponse toResponse(Proyecto p) {
        return new CursoResponse(
                p.getId(),
                p.getTitulo(),
                p.getDescripcion(),
                p.getAreaTematica(),
                p.getUnidadVecinal(),
                p.getFechaInicio().format(FORMATO_FECHA),
                p.getFechaTermino().format(FORMATO_FECHA),
                p.getEstado().name());
    }
}
