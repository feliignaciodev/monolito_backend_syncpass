package com.backendapp.demo.domain;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro persistente de una notificaci��n enviada (manual o recordatorio autom�tico).
 * Permite mostrar historial real en el panel de administraci��n y evitar duplicados en el scheduler.
 */
@Entity
@Table(name = "notificacion_historial")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String cuerpo;

    /** Roles destino separados por coma; null o vac��o = todos. */
    @Column(length = 120)
    private String roles;

    /** Tipo: manual, recordatorio_1h, recordatorio_1d. */
    @Column(nullable = false, length = 30)
    private String tipo;

    /** Evento asociado (solo para recordatorios). */
    @Column(name = "evento_id")
    private Long eventoId;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    @Column(name = "dispositivos_enviados", nullable = false)
    private int dispositivosEnviados;
}
