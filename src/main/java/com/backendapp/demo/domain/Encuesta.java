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

/**
 * Encuesta de valoracion de un evento. {@code participanteId} puede ser nulo (encuesta anonima);
 * se guarda como texto (sin clave foranea) porque puede llegar antes que la marca de un inscrito en sitio.
 */
@Entity
@Table(name = "encuesta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evento", nullable = false)
    private Evento evento;

    @Column(name = "participante_id", length = 40)
    private String participanteId;

    /** Satisfaccion con el evento (1..5). */
    @Column(nullable = false)
    private int satisfaccion;

    /** Utilidad percibida del taller (1..5). */
    @Column(nullable = false)
    private int utilidad;

    @Column(length = 500)
    private String comentario;

    /** Momento de envio en el dispositivo, en milisegundos epoch UTC. */
    @Column(name = "fecha_envio", nullable = false)
    private long fechaEnvio;

    /** Momento en que el servidor recibio la encuesta, en milisegundos epoch UTC. */
    @Column(name = "recibido_en", nullable = false)
    private long recibidoEn;

    public Encuesta(String uuid, Evento evento, String participanteId, int satisfaccion, int utilidad,
            String comentario, long fechaEnvio, long recibidoEn) {
        this.uuid = uuid;
        this.evento = evento;
        this.participanteId = participanteId;
        this.satisfaccion = satisfaccion;
        this.utilidad = utilidad;
        this.comentario = comentario;
        this.fechaEnvio = fechaEnvio;
        this.recibidoEn = recibidoEn;
    }
}
