package com.backendapp.demo.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Roles validos (= grupos de Cognito), de mayor a menor privilegio. */
    public static final List<String> NOMBRES = List.of(ADMINISTRADOR, COORDINADOR, VOLUNTARIO, BENEFICIARIO);

    public static boolean esValido(String nombre) {
        return NOMBRES.contains(nombre);
    }

    /** Rol de mayor privilegio entre los grupos de Cognito del usuario; vacio si no tiene ninguno valido. */
    public static Optional<String> principal(Collection<String> grupos) {
        return NOMBRES.stream().filter(grupos::contains).findFirst();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String nombre;
}