package com.backendapp.demo.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.ResumenProyectoDto;
import com.backendapp.demo.service.ReporteService;

/** Reportes para el informe de cierre. Solo COORDINADOR o ADMINISTRADOR. */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

	private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final ReporteService reporteService;

	public ReporteController(ReporteService reporteService) {
		this.reporteService = reporteService;
	}

	@GetMapping("/resumen")
	public List<ResumenProyectoDto> resumen() {
		return reporteService.resumenProyectos();
	}

	@GetMapping("/asistencias.csv")
	public ResponseEntity<byte[]> asistenciasCsv() {
		byte[] contenido = reporteService.asistenciasCsv().getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.contentType(TEXT_CSV_UTF8)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asistencias.csv\"")
				.body(contenido);
	}
}
