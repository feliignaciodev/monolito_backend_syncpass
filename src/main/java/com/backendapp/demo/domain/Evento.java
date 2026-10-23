package com.backendapp.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evento o taller de un proyecto, con ubicacion referencial para validar la asistencia. */
@Entity
@Table(name = "evento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proyecto", nullable = false)
    private Proyecto proyecto;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Fecha y hora programada, en milisegundos epoch UTC. */
    @Column(name = "fecha_hora", nullable = false)
    private long fechaHora;

    @Column(name = "cupos_maximos", nullable = false)
    private int cuposMaximos;

    @Column(name = "quorum_minimo", nullable = false)
    private int quorumMinimo;

    @Column(nullable = false, length = 150)
    private String recinto;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Column(nullable = false)
    private double latitud;

    @Column(nullable = false)
    private double longitud;

    @Column(name = "radio_metros", nullable = false)
    private int radioMetros;

    public Evento(Proyecto proyecto, String nombre, long fechaHora, int cuposMaximos, int quorumMinimo,
            String recinto, String direccion, double latitud, double longitud, int radioMetros) {
        this.proyecto = proyecto;
        this.nombre = nombre;
        this.fechaHora = fechaHora;
        this.cuposMaximos = cuposMaximos;
        this.quorumMinimo = quorumMinimo;
        this.recinto = recinto;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.radioMetros = radioMetros;
    }
}
