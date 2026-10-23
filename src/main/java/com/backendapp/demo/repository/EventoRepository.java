package com.backendapp.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByProyecto_IdOrderByFechaHoraAsc(Long proyectoId);
}
