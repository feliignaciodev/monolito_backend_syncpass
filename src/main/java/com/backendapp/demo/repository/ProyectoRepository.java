package com.backendapp.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.Proyecto;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
}
