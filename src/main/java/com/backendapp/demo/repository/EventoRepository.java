package com.backendapp.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.backendapp.demo.domain.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByProyecto_IdOrderByFechaHoraAsc(Long proyectoId);

    /** Eventos cuya fecha_hora está entre now y now + offsetMs (inclusive). */
    @Query("select e from Evento e where e.fechaHora >= :desde and e.fechaHora <= :hasta")
    List<Evento> findBetween(long desde, long hasta);
}
