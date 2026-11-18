package com.backendapp.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.EstadoProyecto;
import com.backendapp.demo.domain.Proyecto;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    List<Proyecto> findByEstadoNot(EstadoProyecto estado);
}
