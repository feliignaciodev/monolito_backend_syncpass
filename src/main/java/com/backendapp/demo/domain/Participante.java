package com.backendapp.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Participante con identificador sintetico (ficticio): {@code P-0001} para inscritos previos
 * o {@code W-<8 hex>} para inscritos en sitio (id generado en el dispositivo).
 */
@Entity
@Table(name = "participante")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participante {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 100)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoParticipante tipo;

    public Participante(String id, String alias, TipoParticipante tipo) {
        this.id = id;
        this.alias = alias;
        this.tipo = tipo;
    }
}
