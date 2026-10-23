package com.backendapp.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backendapp.demo.domain.Asistencia;

public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    boolean existsByUuid(String uuid);

    Optional<Asistencia> findByEvento_IdAndParticipante_Id(Long eventoId, String participanteId);

    long countByEvento_Id(Long eventoId);

    @Query("select max(a.timestamp) from Asistencia a where a.evento.id = :eventoId")
    Long ultimaMarcacion(@Param("eventoId") Long eventoId);

    /** Todas las marcas con evento, proyecto y participante cargados (para el CSV). */
    @Query("select a from Asistencia a join fetch a.evento e join fetch e.proyecto join fetch a.participante "
            + "order by a.timestamp asc, a.id asc")
    List<Asistencia> findAllParaReporte();
}
