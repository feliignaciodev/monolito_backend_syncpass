package com.backendapp.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.NotificacionHistorial;

public interface NotificacionHistorialRepository extends JpaRepository<NotificacionHistorial, Long> {

    /** Historial ordenado del m�s reciente al m�s antiguo. */
    List<NotificacionHistorial> findAllByOrderByEnviadoEnDesc();

    /** Evita enviar el mismo recordatorio dos veces para un evento. */
    boolean existsByEventoIdAndTipo(Long eventoId, String tipo);
}
