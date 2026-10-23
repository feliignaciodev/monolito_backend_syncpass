package com.backendapp.demo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.MetricasEventoDto;
import com.backendapp.demo.service.ReporteService;

/** Metricas en vivo de un evento (quorum, asistencia, encuestas). Solo COORDINADOR o ADMINISTRADOR. */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

	private final ReporteService reporteService;

	public EventoController(ReporteService reporteService) {
		this.reporteService = reporteService;
	}

	@GetMapping("/{id}/metricas")
	public MetricasEventoDto metricas(@PathVariable("id") Long id) {
		return reporteService.metricasEvento(id);
	}
}
