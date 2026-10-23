package com.backendapp.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.backendapp.demo.domain.EstadoInscripcion;
import com.backendapp.demo.domain.Inscripcion;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    long countByEvento_IdAndEstado(Long eventoId, EstadoInscripcion estado);

    @Query("select i from Inscripcion i join fetch i.evento join fetch i.participante "
            + "order by i.evento.id, i.participante.id")
    List<Inscripcion> findAllConRelaciones();
}
