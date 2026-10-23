package com.backendapp.demo.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Proyecto de vinculacion comunitaria. */
@Entity
@Table(name = "proyecto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "area_tematica", nullable = false, length = 60)
    private String areaTematica;

    @Column(name = "unidad_vecinal", nullable = false, length = 100)
    private String unidadVecinal;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_termino", nullable = false)
    private LocalDate fechaTermino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProyecto estado;

    public Proyecto(String titulo, String descripcion, String areaTematica, String unidadVecinal,
            LocalDate fechaInicio, LocalDate fechaTermino, EstadoProyecto estado) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.areaTematica = areaTematica;
        this.unidadVecinal = unidadVecinal;
        this.fechaInicio = fechaInicio;
        this.fechaTermino = fechaTermino;
        this.estado = estado;
    }
}
