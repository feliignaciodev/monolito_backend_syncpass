package com.backendapp.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Catalogo y subida por lotes; los ids de evento 1..6 vienen de los datos semilla ficticios. */
class SyncTest extends PruebaBase {

	private static final String VOLUNTARIO = "VOLUNTARIO";

	@Test
	void catalogoTraeLosDatosSemillaFicticios() throws Exception {
		mockMvc.perform(get("/api/sync/catalogo").with(token("sub-cat", VOLUNTARIO)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serverTime").isNumber())
				.andExpect(jsonPath("$.proyectos", hasSize(3)))
				.andExpect(jsonPath("$.proyectos[0].fechaInicio").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}")))
				.andExpect(jsonPath("$.eventos", hasSize(6)))
				.andExpect(jsonPath("$.eventos[0].radioMetros").value(300))
				.andExpect(jsonPath("$.participantes.length()", greaterThanOrEqualTo(42)))
				.andExpect(jsonPath("$.participantes[*].id", hasItem("P-0040")))
				.andExpect(jsonPath("$.inscripciones.length()", greaterThanOrEqualTo(90)));
	}

	@Test
	void asistenciaAceptadaYReintentoQuedaComoDuplicado() throws Exception {
		String lote = lote(asistencia("a-0001", 1, "P-0001", 1_760_539_000_000L, "VALIDO"));

		subirAsistencias(lote)
				.andExpect(jsonPath("$.aceptados", contains("a-0001")))
				.andExpect(jsonPath("$.duplicados", empty()))
				.andExpect(jsonPath("$.rechazados", empty()));
		subirAsistencias(lote)
				.andExpect(jsonPath("$.aceptados", empty()))
				.andExpect(jsonPath("$.duplicados", contains("a-0001")));
	}

	@Test
	void mismaPersonaEnElMismoEventoConservaLaMarcaMasTemprana() throws Exception {
		subirAsistencias(lote(asistencia("a-tarde", 1, "P-0002", 2_000_000_000_000L, "VALIDO")))
				.andExpect(jsonPath("$.aceptados", contains("a-tarde")));
		subirAsistencias(lote(asistencia("a-temprano", 1, "P-0002", 1_900_000_000_000L, "SIN_UBICACION")))
				.andExpect(jsonPath("$.duplicados", contains("a-temprano")));

		String csv = mockMvc.perform(get("/api/reportes/asistencias.csv").with(token("sub-coord", "COORDINADOR")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(csv).contains("a-temprano").doesNotContain("a-tarde");
	}

	@Test
	void cadaRegistroInvalidoSeRechazaSinAfectarAlResto() throws Exception {
		String lote = lote(
				asistencia("a-ok", 1, "P-0003", 1_760_539_000_000L, "VALIDO"),
				asistencia("a-sin-evento", 999, "P-0004", 1_760_539_000_000L, "VALIDO"),
				asistencia("a-estado-malo", 1, "P-0005", 1_760_539_000_000L, "EN_LA_LUNA"),
				asistencia("a-sin-hora", 1, "P-0006", 0L, "VALIDO"));

		subirAsistencias(lote)
				.andExpect(jsonPath("$.aceptados", contains("a-ok")))
				.andExpect(jsonPath("$.rechazados", hasSize(3)))
				.andExpect(jsonPath("$.rechazados[0].motivo").value("Evento no existe"))
				.andExpect(jsonPath("$.rechazados[1].motivo").value(
						"estadoValidacion inválido: EN_LA_LUNA (valores permitidos: VALIDO, FUERA_DE_RADIO, SIN_UBICACION)"))
				.andExpect(jsonPath("$.rechazados[2].uuid").value("a-sin-hora"));
	}

	@Test
	void distanciaInfinitaSeRechazaYLasPequenasNoSaleEnNotacionCientifica() throws Exception {
		String lote = lote(
				asistencia("a-infinita", 4, "P-0012", 1_760_539_000_000L, "VALIDO").replace("18.4", "1e400"),
				asistencia("a-cercana", 4, "P-0013", 1_760_539_000_000L, "VALIDO").replace("18.4", "0.0001"));

		subirAsistencias(lote)
				.andExpect(jsonPath("$.aceptados", contains("a-cercana")))
				.andExpect(jsonPath("$.rechazados[0].uuid").value("a-infinita"));

		String csv = mockMvc.perform(get("/api/reportes/asistencias.csv").with(token("sub-coord", "COORDINADOR")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String fila = csv.lines().filter(l -> l.startsWith("a-cercana,")).findFirst().orElseThrow();
		assertThat(fila).contains(",0.00010,").doesNotContain("E-");
	}

	@Test
	void inscritoEnSitioCreaElParticipante() throws Exception {
		subirAsistencias(lote(asistencia("a-sitio", 2, "W-3f9a1c02", 1_760_539_000_000L, "VALIDO")))
				.andExpect(jsonPath("$.aceptados", contains("a-sitio")));

		mockMvc.perform(get("/api/sync/catalogo").with(token("sub-cat", VOLUNTARIO)))
				.andExpect(jsonPath("$.participantes[?(@.id == 'W-3f9a1c02')].alias", contains("Inscrito en sitio")));

		String sinTipo = lote("""
				{"uuid":"a-sitio-sin-tipo","eventoId":2,"participanteId":"W-00000001",
				 "timestamp":1760539000000,"estadoValidacion":"VALIDO"}""");
		subirAsistencias(sinTipo)
				.andExpect(jsonPath("$.rechazados[0].motivo")
						.value("El tipoParticipante es obligatorio para un participante nuevo"));
	}

	@Test
	void loteDeMasDe500RegistrosResponde400() throws Exception {
		String registros = String.join(",", Collections.nCopies(501, "{}"));
		mockMvc.perform(post("/api/sync/asistencias").with(token("sub-v", VOLUNTARIO))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registros\":[" + registros + "]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El lote supera el máximo de 500 registros"));
	}

	@Test
	void encuestasSeValidanYSeDeduplicanPorParticipante() throws Exception {
		String lote = """
				{"encuestas":[
				 {"uuid":"e-1","eventoId":3,"participanteId":"P-0010","satisfaccion":5,"utilidad":4,
				  "comentario":"Muy claro","fechaEnvio":1760541000000},
				 {"uuid":"e-2","eventoId":3,"participanteId":"P-0010","satisfaccion":3,"utilidad":3,
				  "fechaEnvio":1760541000001},
				 {"uuid":"e-3","eventoId":3,"participanteId":null,"satisfaccion":4,"utilidad":5,
				  "fechaEnvio":1760541000002},
				 {"uuid":"e-4","eventoId":3,"participanteId":null,"satisfaccion":4,"utilidad":5,
				  "fechaEnvio":1760541000003},
				 {"uuid":"e-5","eventoId":3,"participanteId":"P-0011","satisfaccion":6,"utilidad":5,
				  "fechaEnvio":1760541000004}
				]}""";

		mockMvc.perform(post("/api/sync/encuestas").with(token("sub-v", VOLUNTARIO))
				.contentType(MediaType.APPLICATION_JSON).content(lote))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aceptados", contains("e-1", "e-3", "e-4")))
				.andExpect(jsonPath("$.duplicados", contains("e-2")))
				.andExpect(jsonPath("$.rechazados[0].uuid").value("e-5"))
				.andExpect(jsonPath("$.rechazados[0].motivo").value("La satisfacción debe ser un número entre 1 y 5"));
	}

	private ResultActions subirAsistencias(String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/sync/asistencias").with(token("sub-v", VOLUNTARIO))
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isOk());
	}

	private static String lote(String... registros) {
		return "{\"registros\":[" + String.join(",", registros) + "]}";
	}

	private static String asistencia(String uuid, long eventoId, String participanteId, long timestamp,
			String estado) {
		return """
				{"uuid":"%s","eventoId":%d,"participanteId":"%s","tipoParticipante":"VECINO",
				 "timestamp":%d,"latitud":-33.5226,"longitud":-70.5984,"distanciaMetros":18.4,
				 "estadoValidacion":"%s","inscritoEnSitio":false,"dispositivoId":"test-01"}"""
				.formatted(uuid, eventoId, participanteId, timestamp, estado);
	}
}
