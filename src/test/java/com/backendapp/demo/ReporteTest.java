package com.backendapp.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.backendapp.demo.service.ReporteService;

class ReporteTest extends PruebaBase {

	/** El evento 5 es el "pasado" de los datos semilla: trae asistencias y encuestas. */
	@Test
	void metricasDelEventoPasadoParaCoordinador() throws Exception {
		mockMvc.perform(get("/api/eventos/5/metricas").with(token("sub-coord", "COORDINADOR")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventoId").value(5))
				.andExpect(jsonPath("$.quorumMinimo").value(12))
				.andExpect(jsonPath("$.asistentes", greaterThan(0)))
				.andExpect(jsonPath("$.encuestasRespondidas", greaterThan(0)))
				.andExpect(jsonPath("$.quorumAlcanzado").isBoolean())
				.andExpect(jsonPath("$.ultimaMarcacion").isNumber());
	}

	@Test
	void metricasDeEventoInexistenteResponde404() throws Exception {
		mockMvc.perform(get("/api/eventos/999/metricas").with(token("sub-coord", "COORDINADOR")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Evento no existe"));
	}

	@Test
	void administradorTambienVeElResumenPorProyecto() throws Exception {
		mockMvc.perform(get("/api/reportes/resumen").with(token("sub-admin", "ADMINISTRADOR")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].proyectoId").value(1))
				.andExpect(jsonPath("$[0].eventos").value(2));
	}

	@Test
	void csvTraeElEncabezadoDelContrato() throws Exception {
		String csv = mockMvc.perform(get("/api/reportes/asistencias.csv").with(token("sub-coord", "COORDINADOR")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"asistencias.csv\""))
				.andReturn().getResponse().getContentAsString();

		assertThat(csv).startsWith(ReporteService.ENCABEZADO_CSV + "\r\n");
		assertThat(csv.split("\r\n").length).isGreaterThan(1);
	}
}
