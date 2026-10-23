package com.backendapp.demo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.HealthResponse;

/** Endpoint publico para que la app verifique el servidor y obtenga su hora. */
@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("UP", System.currentTimeMillis());
	}
}
