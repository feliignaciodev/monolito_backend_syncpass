package com.backendapp.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code @DirtiesContext} evita que los cursos creados aqui contaminen el contexto
 * compartido con ReporteTest y SyncTest, que esperan exactamente 3 proyectos semilla.
 */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CursoTest extends PruebaBase {

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    /** Extrae el campo "id" del JSON de respuesta de un POST /api/cursos. */
    private static Long extraerId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        // Simple extraction: find "id":NUMBER
        int start = json.indexOf("\"id\":");
        if (start < 0) throw new AssertionError("No se encontró id en: " + json);
        start += 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Long.parseLong(json.substring(start, end));
    }

    @Test
    void listarCursosDevuelveSoloActivos() throws Exception {
        mockMvc.perform(get("/api/cursos").with(token("sub-x", "VOLUNTARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.estado == 'FINALIZADO')]").doesNotExist());
    }

    @Test
    void obtenerCursoPorId() throws Exception {
        mockMvc.perform(get("/api/cursos/1").with(token("sub-x", "VOLUNTARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").isString())
                .andExpect(jsonPath("$.estado").isString());
    }

    @Test
    void obtenerCursoInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/cursos/99999").with(token("sub-x", "VOLUNTARIO")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Curso no encontrado"));
    }

    @Test
    void crearCursoConAdministrador() throws Exception {
        String body = """
                {
                  "titulo": "Curso de prueba",
                  "descripcion": "Descripcion de prueba",
                  "areaTematica": "Educacion",
                  "unidadVecinal": "UV 01 Test",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo").value("Curso de prueba"))
                .andExpect(jsonPath("$.estado").value("PLANIFICADO"));
    }

    @Test
    void crearCursoSinCamposObligatoriosResponde400() throws Exception {
        String body = """
                {
                  "titulo": "",
                  "areaTematica": "",
                  "unidadVecinal": "",
                  "estado": ""
                }
                """;
        mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearCursoConEstadoInvalidoResponde400() throws Exception {
        String body = """
                {
                  "titulo": "Curso",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "ESTADO_INVALIDO"
                }
                """;
        mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Estado inválido: ESTADO_INVALIDO (valores permitidos: PLANIFICADO, EN_CURSO, FINALIZADO)"));
    }

    @Test
    void crearCursoVoluntarioResponde403() throws Exception {
        String body = """
                {
                  "titulo": "Curso",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        mockMvc.perform(post("/api/cursos")
                        .with(token("sub-vol", "VOLUNTARIO"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acceso restringido: se requiere rol ADMINISTRADOR"));
    }

    @Test
    void crearCursoCoordinadorResponde403() throws Exception {
        String body = """
                {
                  "titulo": "Curso",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        mockMvc.perform(post("/api/cursos")
                        .with(token("sub-coord", "COORDINADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarCursoConAdministrador() throws Exception {
        // Crear un curso nuevo para no interferir con datos semilla compartidos
        String crear = """
                {
                  "titulo": "Curso editable",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(crear))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = extraerId(result);

        String body = """
                {
                  "titulo": "Curso editado"
                }
                """;
        mockMvc.perform(put("/api/cursos/" + id)
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Curso editado"));
    }

    @Test
    void editarCursoInexistenteResponde404() throws Exception {
        String body = """
                {
                  "titulo": "No existe"
                }
                """;
        mockMvc.perform(put("/api/cursos/99999")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Curso no encontrado"));
    }

    @Test
    void editarCursoVoluntarioResponde403() throws Exception {
        String body = """
                {
                  "titulo": "No autorizado"
                }
                """;
        mockMvc.perform(put("/api/cursos/99999")
                        .with(token("sub-vol", "VOLUNTARIO"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acceso restringido: se requiere rol ADMINISTRADOR"));
    }

    @Test
    void desactivarCursoConAdministrador() throws Exception {
        // Crear un curso nuevo para no interferir con datos semilla compartidos
        String crear = """
                {
                  "titulo": "Curso desactivable",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(crear))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = extraerId(result);

        mockMvc.perform(patch("/api/cursos/" + id + "/desactivar")
                        .with(token("sub-admin", "ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));
    }

    @Test
    void desactivarCursoYaDesactivadoResponde409() throws Exception {
        // Crear un curso nuevo para no interferir con datos semilla compartidos
        String crear = """
                {
                  "titulo": "Curso doble desactivacion",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(crear))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = extraerId(result);

        mockMvc.perform(patch("/api/cursos/" + id + "/desactivar")
                        .with(token("sub-admin", "ADMINISTRADOR")));

        mockMvc.perform(patch("/api/cursos/" + id + "/desactivar")
                        .with(token("sub-admin", "ADMINISTRADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El curso ya está desactivado"));
    }

    @Test
    void desactivarCursoVoluntarioResponde403() throws Exception {
        mockMvc.perform(patch("/api/cursos/99999/desactivar")
                        .with(token("sub-vol", "VOLUNTARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cursoDesactivadoNoApareceEnListado() throws Exception {
        // Crear un curso nuevo para no interferir con datos semilla compartidos
        String crear = """
                {
                  "titulo": "Curso lista",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(crear))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = extraerId(result);

        mockMvc.perform(patch("/api/cursos/" + id + "/desactivar")
                        .with(token("sub-admin", "ADMINISTRADOR")));

        mockMvc.perform(get("/api/cursos").with(token("sub-x", "VOLUNTARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void cursoDesactivadoSigueObteniblePorId() throws Exception {
        // Crear un curso nuevo para no interferir con datos semilla compartidos
        String crear = """
                {
                  "titulo": "Curso obtenible",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-01-01",
                  "fechaTermino": "2026-06-30",
                  "estado": "PLANIFICADO"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(crear))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = extraerId(result);

        mockMvc.perform(patch("/api/cursos/" + id + "/desactivar")
                        .with(token("sub-admin", "ADMINISTRADOR")));

        mockMvc.perform(get("/api/cursos/" + id).with(token("sub-x", "VOLUNTARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/cursos"))
                .andExpect(status().isUnauthorized());
    }

    /** Crea un curso como ADMINISTRADOR y devuelve su id. */
    private Long crearCurso(String titulo, String inicio, String termino) throws Exception {
        String body = """
                {
                  "titulo": "%s",
                  "descripcion": "Descripcion inicial",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "%s",
                  "fechaTermino": "%s",
                  "estado": "PLANIFICADO"
                }
                """.formatted(titulo, inicio, termino);
        return extraerId(mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    void recientesEsPublicoYDevuelveDelMasNuevoAlMasAntiguo() throws Exception {
        Long primero = crearCurso("Reciente A", "2026-03-01", "2026-04-01");
        Long segundo = crearCurso("Reciente B", "2026-01-01", "2026-02-01");

        // Sin token: lo consume la portada publica de la web
        mockMvc.perform(get("/api/cursos/recientes").param("limite", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(segundo))
                .andExpect(jsonPath("$[1].id").value(primero));
    }

    @Test
    void recientesAcotaElLimiteYExcluyeFinalizados() throws Exception {
        Long id = crearCurso("Reciente finalizado", "2026-01-01", "2026-02-01");
        mockMvc.perform(patch("/api/cursos/" + id + "/desactivar").with(token("sub-admin", "ADMINISTRADOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cursos/recientes").param("limite", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/cursos/recientes").param("limite", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(12)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void crearConFechaDeTerminoAnteriorResponde400() throws Exception {
        String body = """
                {
                  "titulo": "Fechas invertidas",
                  "areaTematica": "Test",
                  "unidadVecinal": "UV 01",
                  "fechaInicio": "2026-06-30",
                  "fechaTermino": "2026-01-01",
                  "estado": "PLANIFICADO"
                }
                """;
        mockMvc.perform(post("/api/cursos")
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("La fecha de término no puede ser anterior a la fecha de inicio"));
    }

    @Test
    void editarDejandoFechasInvertidasResponde400YNoCambiaNada() throws Exception {
        Long id = crearCurso("Fechas al editar", "2026-03-01", "2026-06-30");

        // Solo se envia el termino: se compara contra el inicio que ya tenia el curso
        mockMvc.perform(put("/api/cursos/" + id)
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content("{\"titulo\": \"No debe guardarse\", \"fechaTermino\": \"2026-02-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("La fecha de término no puede ser anterior a la fecha de inicio"));

        mockMvc.perform(get("/api/cursos/" + id).with(token("sub-x", "VOLUNTARIO")))
                .andExpect(jsonPath("$.titulo").value("Fechas al editar"))
                .andExpect(jsonPath("$.fechaTermino").value("2026-06-30"));
    }

    @Test
    void editarConDescripcionVaciaLaEliminaYConservaLoNoEnviado() throws Exception {
        Long id = crearCurso("Descripcion borrable", "2026-03-01", "2026-06-30");

        mockMvc.perform(put("/api/cursos/" + id)
                        .with(token("sub-admin", "ADMINISTRADOR"))
                        .contentType(JSON)
                        .content("{\"descripcion\": \"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").doesNotExist())
                .andExpect(jsonPath("$.titulo").value("Descripcion borrable"))
                .andExpect(jsonPath("$.fechaInicio").value("2026-03-01"));
    }
}
