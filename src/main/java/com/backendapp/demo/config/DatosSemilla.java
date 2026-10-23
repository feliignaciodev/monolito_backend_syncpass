package com.backendapp.demo.config;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backendapp.demo.domain.Asistencia;
import com.backendapp.demo.domain.Encuesta;
import com.backendapp.demo.domain.EstadoInscripcion;
import com.backendapp.demo.domain.EstadoProyecto;
import com.backendapp.demo.domain.EstadoValidacion;
import com.backendapp.demo.domain.Evento;
import com.backendapp.demo.domain.Inscripcion;
import com.backendapp.demo.domain.Participante;
import com.backendapp.demo.domain.Proyecto;
import com.backendapp.demo.domain.TipoParticipante;
import com.backendapp.demo.repository.AsistenciaRepository;
import com.backendapp.demo.repository.EncuestaRepository;
import com.backendapp.demo.repository.EventoRepository;
import com.backendapp.demo.repository.InscripcionRepository;
import com.backendapp.demo.repository.ParticipanteRepository;
import com.backendapp.demo.repository.ProyectoRepository;
import com.backendapp.demo.service.SyncService;

/**
 * Carga datos de demostracion 100 % FICTICIOS al iniciar, solo si no hay proyectos.
 * Las fechas de los eventos son relativas al momento de la siembra para que la demo siempre
 * tenga eventos "hoy", proximos y uno pasado con asistencia y encuestas.
 * Los usuarios NO se siembran: viven en Cognito (ver deploy/cognito-setup.ps1).
 * Se desactiva con {@code syncpass.datos-semilla=false}.
 */
@Component
@ConditionalOnProperty(name = "syncpass.datos-semilla", havingValue = "true", matchIfMissing = true)
public class DatosSemilla implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosSemilla.class);

    private static final int TOTAL_PARTICIPANTES = 40;
    private static final int RADIO_METROS = 300;
    private static final double METROS_POR_GRADO = 111_320.0;

    private static final String[] COMENTARIOS = {
            "Muy claro", "Me gustó mucho, ojalá se repita", "Faltó un poco más de tiempo para practicar",
            "Excelente disposición de los voluntarios", "Aprendí cosas nuevas", "El lugar era un poco pequeño"
    };

    private final ProyectoRepository proyectoRepository;
    private final EventoRepository eventoRepository;
    private final ParticipanteRepository participanteRepository;
    private final InscripcionRepository inscripcionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;
    private final ZoneId zona;

    /** Semilla fija: los mismos datos en cada siembra (salvo fechas, que son relativas a "ahora"). */
    private final Random random = new Random(1105);

    public DatosSemilla(ProyectoRepository proyectoRepository, EventoRepository eventoRepository,
            ParticipanteRepository participanteRepository, InscripcionRepository inscripcionRepository,
            AsistenciaRepository asistenciaRepository, EncuestaRepository encuestaRepository,
            @Value("${syncpass.zona-horaria:America/Santiago}") String zonaHoraria) {
        this.proyectoRepository = proyectoRepository;
        this.eventoRepository = eventoRepository;
        this.participanteRepository = participanteRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.encuestaRepository = encuestaRepository;
        this.zona = ZoneId.of(zonaHoraria);
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (proyectoRepository.count() > 0) {
            log.info("Base de datos con proyectos existentes: se omite la carga de datos semilla");
            return;
        }

        // --- Proyectos ---
        LocalDate hoy = LocalDate.now(zona);
        Proyecto tecnologia = proyectoRepository.save(new Proyecto(
                "Alfabetización Digital Adulto Mayor",
                "Talleres prácticos para que personas mayores del sector aprendan a usar su smartphone, "
                        + "mensajería y trámites en línea de forma segura.",
                "Tecnología", "UV 23 Los Copihues", hoy.minusDays(70), hoy.plusDays(60), EstadoProyecto.EN_CURSO));
        Proyecto salud = proyectoRepository.save(new Proyecto(
                "Salud Preventiva en tu Barrio",
                "Operativos de control básico y charlas de autocuidado realizadas junto a estudiantes "
                        + "voluntarios del área de salud.",
                "Salud", "UV 12 Villa Los Aromos", hoy.minusDays(30), hoy.plusDays(90), EstadoProyecto.EN_CURSO));
        Proyecto emprendimiento = proyectoRepository.save(new Proyecto(
                "Emprende tu Feria",
                "Acompañamiento a microemprendimientos vecinales: costos, precios, redes sociales y una feria "
                        + "de cierre para vender sus productos.",
                "Emprendimiento", "UV 31 Las Araucarias", hoy.plusDays(2), hoy.plusDays(120),
                EstadoProyecto.PLANIFICADO));

        // --- Eventos (fechas relativas a "ahora") ---
        ZonedDateTime ahora = ZonedDateTime.now(zona).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime enUnaHora = redondearACincoMinutos(ahora.plusHours(1));
        ZonedDateTime enTresHoras = redondearACincoMinutos(ahora.plusHours(3));

        String recintoTec = "Sede Junta de Vecinos UV 23";
        String dirTec = "Pasaje Ficticio 123, La Florida";
        String recintoSalud = "Centro Comunitario Villa Los Aromos";
        String dirSalud = "Calle Imaginaria 456, La Florida";
        String recintoEmp = "Salón Multiuso Las Araucarias";
        String dirEmp = "Avenida Simulada 789, Puente Alto";

        Evento hoyTec = eventoRepository.save(new Evento(tecnologia, "Taller: Mi primer smartphone",
                millis(enUnaHora), 30, 12, recintoTec, dirTec, -33.5225, -70.5986, RADIO_METROS));
        Evento hoySalud = eventoRepository.save(new Evento(salud, "Operativo de control de presión arterial",
                millis(enTresHoras), 25, 10, recintoSalud, dirSalud, -33.5312, -70.5874, RADIO_METROS));
        Evento mananaSalud = eventoRepository.save(new Evento(salud, "Charla: Alimentación saludable en familia",
                millis(hoy.plusDays(1).atTime(LocalTime.of(10, 30)).atZone(zona)), 25, 10,
                recintoSalud, dirSalud, -33.5312, -70.5874, RADIO_METROS));
        Evento en3DiasEmp = eventoRepository.save(new Evento(emprendimiento,
                "Taller: Costos y precios para tu emprendimiento",
                millis(hoy.plusDays(3).atTime(LocalTime.of(18, 0)).atZone(zona)), 20, 8,
                recintoEmp, dirEmp, -33.5610, -70.5850, RADIO_METROS));
        Evento pasadoTec = eventoRepository.save(new Evento(tecnologia, "Taller: WhatsApp y videollamadas seguras",
                millis(hoy.minusDays(2).atTime(LocalTime.of(11, 0)).atZone(zona)), 30, 12,
                recintoTec, dirTec, -33.5225, -70.5986, RADIO_METROS));
        Evento en7DiasEmp = eventoRepository.save(new Evento(emprendimiento, "Feria de emprendedores del barrio",
                millis(hoy.plusDays(7).atTime(LocalTime.of(10, 0)).atZone(zona)), 40, 15,
                recintoEmp, dirEmp, -33.5610, -70.5850, RADIO_METROS));
        List<Evento> eventos = List.of(hoyTec, hoySalud, mananaSalud, en3DiasEmp, pasadoTec, en7DiasEmp);

        // --- Participantes P-0001..P-0040 (identificadores sinteticos) ---
        List<Participante> participantes = new ArrayList<>();
        for (int i = 1; i <= TOTAL_PARTICIPANTES; i++) {
            TipoParticipante tipo = i % 5 == 0 ? TipoParticipante.DOCENTE
                    : i % 3 == 0 ? TipoParticipante.ESTUDIANTE : TipoParticipante.VECINO;
            participantes.add(participanteRepository.save(new Participante(
                    String.format("P-%04d", i), String.format("Participante %03d", i), tipo)));
        }

        // --- Inscripciones: 15 a 25 por evento; las 3 ultimas quedan 2 en LISTA_ESPERA y 1 CANCELADO ---
        int totalInscripciones = 0;
        List<Participante> inscritosPasado = new ArrayList<>();
        for (Evento evento : eventos) {
            List<Participante> mezcla = new ArrayList<>(participantes);
            Collections.shuffle(mezcla, random);
            int cantidad = 15 + random.nextInt(11);
            for (int i = 0; i < cantidad; i++) {
                EstadoInscripcion estado = i < cantidad - 3 ? EstadoInscripcion.INSCRITO
                        : i < cantidad - 1 ? EstadoInscripcion.LISTA_ESPERA : EstadoInscripcion.CANCELADO;
                inscripcionRepository.save(new Inscripcion(evento, mezcla.get(i), estado));
                if (evento == pasadoTec && estado == EstadoInscripcion.INSCRITO) {
                    inscritosPasado.add(mezcla.get(i));
                }
            }
            totalInscripciones += cantidad;
        }

        // --- Asistencia y encuestas del evento pasado (para que metricas y reportes no esten vacios) ---
        int[] totales = sembrarEventoPasado(pasadoTec, inscritosPasado);

        log.info("Datos semilla FICTICIOS cargados: 3 proyectos, {} eventos, {} participantes, "
                + "{} inscripciones, {} asistencias y {} encuestas",
                eventos.size(), participanteRepository.count(), totalInscripciones, totales[0], totales[1]);
    }

    /** Devuelve {asistencias, encuestas} creadas. */
    private int[] sembrarEventoPasado(Evento evento, List<Participante> inscritos) {
        long inicio = evento.getFechaHora();
        long recibido = inicio + Duration.ofHours(3).toMillis();
        List<Asistencia> asistencias = new ArrayList<>();

        // ~80 % de los inscritos asistio
        int cantidad = Math.round(inscritos.size() * 0.8f);
        for (int i = 0; i < cantidad; i++) {
            Participante p = inscritos.get(i);
            EstadoValidacion estado = i == 3 ? EstadoValidacion.FUERA_DE_RADIO
                    : i == 7 ? EstadoValidacion.SIN_UBICACION : EstadoValidacion.VALIDO;
            asistencias.add(crearMarca(evento, p, inicio, estado, false, recibido));
        }
        // Dos personas inscritas en sitio (id generado en el dispositivo: W-<8 hex>)
        for (int i = 0; i < 2; i++) {
            String id = String.format("W-%08x", random.nextInt() & 0x7fffffff);
            TipoParticipante tipo = i == 0 ? TipoParticipante.VECINO : TipoParticipante.ESTUDIANTE;
            Participante enSitio = participanteRepository.save(
                    new Participante(id, SyncService.ALIAS_INSCRITO_EN_SITIO, tipo));
            asistencias.add(crearMarca(evento, enSitio, inicio, EstadoValidacion.VALIDO, true, recibido));
        }
        asistenciaRepository.saveAll(asistencias);

        // ~70 % responde la encuesta, mas una encuesta anonima
        int encuestas = 0;
        for (Asistencia a : asistencias) {
            if (random.nextInt(10) >= 7) {
                continue;
            }
            encuestaRepository.save(crearEncuesta(evento, a.getParticipante().getId(), a.getTimestamp(), recibido));
            encuestas++;
        }
        encuestaRepository.save(crearEncuesta(evento, null, inicio + Duration.ofMinutes(100).toMillis(), recibido));
        encuestas++;

        return new int[] { asistencias.size(), encuestas };
    }

    private Asistencia crearMarca(Evento evento, Participante p, long inicio, EstadoValidacion estado,
            boolean enSitio, long recibido) {
        // Llegadas entre 15 minutos antes y 25 minutos despues del inicio
        long timestamp = inicio + Duration.ofMinutes(random.nextInt(41) - 15L).toMillis()
                + random.nextInt(60) * 1000L;
        Double lat = null;
        Double lon = null;
        Double distancia = null;
        if (estado != EstadoValidacion.SIN_UBICACION) {
            distancia = estado == EstadoValidacion.FUERA_DE_RADIO
                    ? 450.0 + random.nextInt(200)
                    : 5.0 + random.nextInt(120);
            double rumbo = random.nextDouble() * 2 * Math.PI;
            lat = evento.getLatitud() + distancia * Math.cos(rumbo) / METROS_POR_GRADO;
            lon = evento.getLongitud() + distancia * Math.sin(rumbo)
                    / (METROS_POR_GRADO * Math.cos(Math.toRadians(evento.getLatitud())));
            lat = Math.round(lat * 1_000_000d) / 1_000_000d;
            lon = Math.round(lon * 1_000_000d) / 1_000_000d;
            distancia = Math.round(distancia * 10d) / 10d;
        }
        // registradoPor queda nulo: los usuarios demo existen en Cognito, no en esta siembra
        return new Asistencia(UUID.randomUUID().toString(), evento, p, p.getTipo(), timestamp, lat, lon, distancia,
                estado, enSitio, null, "demo-dispositivo-01", recibido);
    }

    private Encuesta crearEncuesta(Evento evento, String participanteId, long marca, long recibido) {
        int satisfaccion = 3 + Math.min(2, random.nextInt(4)); // sesgo hacia 4 y 5
        int utilidad = 3 + random.nextInt(3);
        String comentario = random.nextInt(3) == 0 ? null : COMENTARIOS[random.nextInt(COMENTARIOS.length)];
        long fechaEnvio = Math.max(marca, evento.getFechaHora())
                + Duration.ofMinutes(90 + random.nextInt(30)).toMillis();
        return new Encuesta(UUID.randomUUID().toString(), evento, participanteId, satisfaccion, utilidad,
                comentario, fechaEnvio, recibido);
    }

    private static ZonedDateTime redondearACincoMinutos(ZonedDateTime fecha) {
        int resto = fecha.getMinute() % 5;
        return resto == 0 ? fecha : fecha.plusMinutes(5L - resto);
    }

    private static long millis(ZonedDateTime fecha) {
        return fecha.toInstant().toEpochMilli();
    }
}
