package com.backendapp.demo.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backendapp.demo.dto.CursoCreateRequest;
import com.backendapp.demo.dto.CursoResponse;
import com.backendapp.demo.dto.CursoUpdateRequest;
import com.backendapp.demo.service.CursoService;

import jakarta.validation.Valid;

/**
 * Gestión de cursos (Proyectos). {@code GET /recientes} es público; el resto de la lectura es para
 * cualquier usuario autenticado y la escritura solo ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api/cursos")
public class CursoController {

    private final CursoService cursoService;

    public CursoController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    @GetMapping
    public List<CursoResponse> listar() {
        return cursoService.listar();
    }

    /** Publico: alimenta la portada del sitio web con los ultimos cursos creados. */
    @GetMapping("/recientes")
    public List<CursoResponse> recientes(@RequestParam(name = "limite", defaultValue = "3") int limite) {
        return cursoService.recientes(limite);
    }

    @GetMapping("/{id}")
    public CursoResponse obtener(@PathVariable("id") Long id) {
        return cursoService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CursoResponse crear(@Valid @RequestBody CursoCreateRequest request) {
        return cursoService.crear(request);
    }

    @PutMapping("/{id}")
    public CursoResponse editar(@PathVariable("id") Long id, @Valid @RequestBody CursoUpdateRequest request) {
        return cursoService.editar(id, request);
    }

    @PatchMapping("/{id}/desactivar")
    public CursoResponse desactivar(@PathVariable("id") Long id) {
        return cursoService.desactivar(id);
    }
}
