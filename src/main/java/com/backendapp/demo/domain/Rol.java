package com.backendapp.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rol")
@Getter
@Setter
@NoArgsConstructor
public class Rol {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String COORDINADOR = "COORDINADOR";
    public static final String VOLUNTARIO = "VOLUNTARIO";
    public static final String BENEFICIARIO = "BENEFICIARIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String nombre;
}