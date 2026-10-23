package com.backendapp.demo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.backendapp.demo.dto.ErrorResponse;

/** Traduce las excepciones a la forma de error del contrato: {@code {"error": "..."}}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> estado(ResponseStatusException ex) {
		HttpStatus estado = HttpStatus.resolve(ex.getStatusCode().value());
		String mensaje = ex.getReason() != null ? ex.getReason()
				: estado != null ? estado.getReasonPhrase() : "Error";
		return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(mensaje));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
		String mensaje = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(FieldError::getDefaultMessage)
				.orElse("Datos de entrada inválidos");
		return error(HttpStatus.BAD_REQUEST, mensaje);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> jsonMalFormado(HttpMessageNotReadableException ex) {
		return error(HttpStatus.BAD_REQUEST, "JSON mal formado o con tipos de datos inválidos");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException ex) {
		return error(HttpStatus.BAD_REQUEST, "Valor inválido para el parámetro '" + ex.getName() + "'");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> parametroFaltante(MissingServletRequestParameterException ex) {
		return error(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + ex.getParameterName() + "'");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> conflicto(DataIntegrityViolationException ex) {
		log.warn("Conflicto de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
		return error(HttpStatus.CONFLICT, "El registro entra en conflicto con datos existentes");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException ex) {
		return error(HttpStatus.NOT_FOUND, "Recurso no encontrado");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido: " + ex.getMethod());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> tipoContenido(HttpMediaTypeNotSupportedException ex) {
		return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado; use application/json");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> inesperado(Exception ex) {
		log.error("Error no controlado", ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
	}

	private static ResponseEntity<ErrorResponse> error(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado).body(new ErrorResponse(mensaje));
	}
}
