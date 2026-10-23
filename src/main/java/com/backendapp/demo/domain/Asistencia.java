package com.backendapp.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Marca de asistencia sincronizada desde un dispositivo.
 * Restricciones: {@code uuid} unico (idempotencia) y una sola marca por evento + participante.
 */
@Entity
@Table(name = "asistencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evento", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_participante", nullable = false)
    private Participante participante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_participante", nullable = false, length = 20)
    private TipoParticipante tipoParticipante;

    /** Momento de la marca en el dispositivo, en milisegundos epoch UTC. */
    @Column(name = "marcado_en", nullable = false)
    private long timestamp;

    private Double latitud;

    private Double longitud;

    @Column(name = "distancia_metros")
    private Double distanciaMetros;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", nullable = false, length = 20)
    private EstadoValidacion estadoValidacion;

    @Column(name = "inscrito_en_sitio", nullable = false)
    private boolean inscritoEnSitio;

    /** Id local del usuario (voluntario/coordinador) que registro la marca. */
    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "dispositivo_id", length = 100)
    private String dispositivoId;

    /** Momento en que el servidor recibio la marca, en milisegundos epoch UTC. */
    @Column(name = "recibido_en", nullable = false)
    private long recibidoEn;

    public Asistencia(String uuid, Evento evento, Participante participante, TipoParticipante tipoParticipante,
            long timestamp, Double latitud, Double longitud, Double distanciaMetros,
            EstadoValidacion estadoValidacion, boolean inscritoEnSitio, Long registradoPor,
            String dispositivoId, long recibidoEn) {
        this.uuid = uuid;
        this.evento = evento;
        this.participante = participante;
        this.tipoParticipante = tipoParticipante;
        this.timestamp = timestamp;
        this.latitud = latitud;
        this.longitud = longitud;
        this.distanciaMetros = distanciaMetros;
        this.estadoValidacion = estadoValidacion;
        this.inscritoEnSitio = inscritoEnSitio;
        this.registradoPor = registradoPor;
        this.dispositivoId = dispositivoId;
        this.recibidoEn = recibidoEn;
    }

    /**
     * Reemplaza los datos de la marca por los de otra mas temprana del mismo evento y participante
     * (regla "se conserva la marca mas temprana").
     */
    public void reemplazarPorMarcaMasTemprana(Asistencia otra) {
        this.uuid = otra.uuid;
        this.tipoParticipante = otra.tipoParticipante;
        this.timestamp = otra.timestamp;
        this.latitud = otra.latitud;
        this.longitud = otra.longitud;
        this.distanciaMetros = otra.distanciaMetros;
        this.estadoValidacion = otra.estadoValidacion;
        this.inscritoEnSitio = otra.inscritoEnSitio;
        this.registradoPor = otra.registradoPor;
        this.dispositivoId = otra.dispositivoId;
        this.recibidoEn = otra.recibidoEn;
    }
}
