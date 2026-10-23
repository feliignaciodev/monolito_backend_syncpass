package com.backendapp.demo.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backendapp.demo.domain.Encuesta;

public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {

    boolean existsByUuid(String uuid);

    boolean existsByEvento_IdAndParticipanteId(Long eventoId, String participanteId);

    long countByEvento_Id(Long eventoId);

    long countByEvento_IdIn(Collection<Long> eventoIds);

    @Query("select avg(e.satisfaccion) from Encuesta e where e.evento.id = :eventoId")
    Double promedioSatisfaccion(@Param("eventoId") Long eventoId);

    @Query("select avg(e.utilidad) from Encuesta e where e.evento.id = :eventoId")
    Double promedioUtilidad(@Param("eventoId") Long eventoId);

    @Query("select avg(e.satisfaccion) from Encuesta e where e.evento.id in :eventoIds")
    Double promedioSatisfaccionDeEventos(@Param("eventoIds") Collection<Long> eventoIds);
}
