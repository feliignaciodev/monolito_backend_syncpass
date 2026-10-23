package com.backendapp.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.Participante;

public interface ParticipanteRepository extends JpaRepository<Participante, String> {
}
