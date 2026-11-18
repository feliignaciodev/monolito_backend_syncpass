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
                .andExpect(status().isForbidden());
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
                .andExpect(status().isForbidden());
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
}
