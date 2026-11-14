# Prompt de Integración — CRUD de Cursos (App Móvil Android)

> **Repositorio:** `syncpass-movil`
> **Contexto:** SyncPass — App Android para gestión de eventos comunitarios.
> **Backend:** API ya implementada en `monolito_backend_syncpass` (Spring Boot 4, Java 21).

---

## 1. Contexto del Proyecto

SyncPass es una plataforma de gestión de eventos comunitarios con check-in por QR, modo offline, encuestas y reportes. La app Android permite a los usuarios (participantes, voluntarios, coordinadores y administradores) gestionar inscripciones y asistencias a eventos.

El backend ha agregado un **CRUD completo de cursos** (sinónimo de "Proyecto" en el dominio). Un administrador puede crear, editar y desactivar cursos desde el panel web, y estos cursos aparecen automáticamente en la app móvil tras sincronizar el catálogo.

Tu tarea es **verificar que la app móvil ya consume correctamente los cursos** a través del endpoint de catálogo existente, y opcionalmente agregar la capacidad de ver el detalle de un curso.

---

## 2. ¿Qué es un "Curso" en SyncPass?

En el backend, **"Curso" es sinónimo de "Proyecto"**. No es una nueva entidad: reutiliza la entidad `Proyecto` existente con los siguientes campos:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | `Long` | Identificador único |
| `titulo` | `String` | Título del curso |
| `descripcion` | `String` | Descripción detallada |
| `areaTematica` | `String` | Área temática (ej. "Educación", "Salud") |
| `unidadVecinal` | `String` | Unidad vecinal asociada |
| `fechaInicio` | `String (yyyy-MM-dd)` | Fecha de inicio |
| `fechaTermino` | `String (yyyy-MM-dd)` | Fecha de término |
| `estado` | `String` | `PLANIFICADO`, `EN_CURSO` o `FINALIZADO` |

**Importante:** Los cursos con estado `FINALIZADO` están desactivados y **no aparecen en el catálogo** de la app móvil.

---

## 3. Endpoints del Backend

### 3.1. Endpoints existentes (ya consumidos por la app)

| Método | Ruta | Autenticación | Descripción |
|--------|------|---------------|-------------|
| `GET` | `/api/sync/catalogo` | JWT | Catálogo completo (proyectos activos + eventos) |

**El endpoint `/api/sync/catalogo` ya filtra los proyectos `FINALIZADO`.** Los cursos creados o editados desde el panel web aparecen automáticamente en este catálogo.

**Response de ejemplo:**
```json
{
  "proyectos": [
    {
      "id": 1,
      "titulo": "Alfabetización Digital Adulto Mayor",
      "descripcion": "Enseñar el uso de smartphones y apps básicas",
      "areaTematica": "Educación",
      "unidadVecinal": "UV 01",
      "fechaInicio": "2026-01-15",
      "fechaTermino": "2026-06-30",
      "estado": "EN_CURSO"
    }
  ],
  "eventos": [...]
}
```

### 3.2. Nuevos endpoints de cursos (opcionales para la app)

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `GET` | `/api/cursos` | JWT | Cualquier usuario | Lista cursos activos (excluye `FINALIZADO`) |
| `GET` | `/api/cursos/{id}` | JWT | Cualquier usuario | Detalle de un curso |
| `POST` | `/api/cursos` | JWT | `ADMINISTRADOR` | Crear un nuevo curso |
| `PUT` | `/api/cursos/{id}` | JWT | `ADMINISTRADOR` | Editar un curso |
| `PATCH` | `/api/cursos/{id}/desactivar` | JWT | `ADMINISTRADOR` | Desactivar un curso |

---

## 4. Impacto en la App Móvil

### 4.1. Lo que ya funciona sin cambios

- **Catálogo de proyectos:** `GET /api/sync/catalogo` devuelve los proyectos activos. Los cursos creados desde el panel web aparecen aquí automáticamente.
- **Sincronización offline:** El flujo de sync existente ya maneja los proyectos del catálogo.
- **Visualización de eventos:** Los eventos dentro de un curso se muestran como antes.

### 4.2. Lo que PUEDE agregarse (opcional)

Si se desea una pantalla dedicada de cursos con CRUD para administradores:

1. **Lista de cursos:** Consumir `GET /api/cursos` para mostrar todos los cursos activos.
2. **Detalle de curso:** Consumir `GET /api/cursos/{id}` para ver la información completa.
3. **Crear/Editar curso:** Solo para usuarios con rol `ADMINISTRADOR`.
4. **Desactivar curso:** Solo para `ADMINISTRADOR`. Cambia el estado a `FINALIZADO` y oculta el curso del catálogo.

---

## 5. Contrato de Datos

### 5.1. CursoResponse (lectura)

```json
{
  "id": 1,
  "titulo": "Alfabetización Digital Adulto Mayor",
  "descripcion": "Enseñar el uso de smartphones y apps básicas",
  "areaTematica": "Educación",
  "unidadVecinal": "UV 01",
  "fechaInicio": "2026-01-15",
  "fechaTermino": "2026-06-30",
  "estado": "EN_CURSO"
}
```

### 5.2. CursoCreateRequest (crear)

```json
{
  "titulo": "Curso de prueba",
  "descripcion": "Descripción del curso",
  "areaTematica": "Educación",
  "unidadVecinal": "UV 01",
  "fechaInicio": "2026-01-01",
  "fechaTermino": "2026-06-30",
  "estado": "PLANIFICADO"
}
```

**Validaciones del servidor:**
- `titulo`: obligatorio, máximo 150 caracteres.
- `areaTematica`: obligatorio, máximo 60 caracteres.
- `unidadVecinal`: obligatorio, máximo 100 caracteres.
- `fechaInicio`: obligatorio.
- `fechaTermino`: obligatorio.
- `estado`: obligatorio, debe ser `PLANIFICADO`, `EN_CURSO` o `FINALIZADO`.
- `descripcion`: opcional, máximo 1000 caracteres.

### 5.3. CursoUpdateRequest (editar)

```json
{
  "titulo": "Nuevo título",
  "estado": "EN_CURSO"
}
```

Todos los campos son opcionales. Solo se actualizan los campos enviados.

### 5.4. Errores comunes

| Código HTTP | Situación |
|-------------|-----------|
| `400 Bad Request` | Campos obligatorios vacíos o estado inválido |
| `403 Forbidden` | Rol no autorizado (no `ADMINISTRADOR` para POST/PUT/PATCH) |
| `404 Not Found` | Curso no encontrado |
| `409 Conflict` | Intentar desactivar un curso ya desactivado |

**Formato de error:**
```json
{
  "error": "El título es obligatorio",
  "status": 400
}
```

---

## 6. Código de Referencia (Kotlin)

### 6.1. Data Classes

```kotlin
// data/model/Curso.kt

package com.syncpass.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Curso(
    val id: Long,
    val titulo: String,
    val descripcion: String?,
    val areaTematica: String,
    val unidadVecinal: String,
    val fechaInicio: String,  // yyyy-MM-dd
    val fechaTermino: String, // yyyy-MM-dd
    val estado: String         // PLANIFICADO, EN_CURSO, FINALIZADO
)
```

### 6.2. Repository

```kotlin
// data/repository/CursoRepository.kt

package com.syncpass.app.data.repository

import com.syncpass.app.data.model.Curso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json

class CursoRepository(
    private val baseUrl: String = "https://tu-backend-url.com",
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private val client = OkHttpClient()

    suspend fun listarCursos(jwtToken: String): List<Curso> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/cursos")
            .get()
            .addHeader("Authorization", "Bearer $jwtToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<List<Curso>>(body)
        }
    }

    suspend fun obtenerCurso(id: Long, jwtToken: String): Curso = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/cursos/$id")
            .get()
            .addHeader("Authorization", "Bearer $jwtToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<Curso>(body)
        }
    }

    suspend fun crearCurso(curso: CursoCreateRequest, jwtToken: String): Curso = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CursoCreateRequest.serializer(), curso)
        val requestBody = body.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/cursos")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $jwtToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error: ${response.code} - ${response.body?.string()}")
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<Curso>(respBody)
        }
    }

    suspend fun editarCurso(id: Long, curso: CursoUpdateRequest, jwtToken: String): Curso = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CursoUpdateRequest.serializer(), curso)
        val requestBody = body.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/cursos/$id")
            .put(requestBody)
            .addHeader("Authorization", "Bearer $jwtToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error: ${response.code} - ${response.body?.string()}")
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<Curso>(respBody)
        }
    }

    suspend fun desactivarCurso(id: Long, jwtToken: String): Curso = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/cursos/$id/desactivar")
            .patch("")
            .addHeader("Authorization", "Bearer $jwtToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error: ${response.code} - ${response.body?.string()}")
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<Curso>(respBody)
        }
    }
}

@kotlinx.serialization.Serializable
data class CursoCreateRequest(
    val titulo: String,
    val descripcion: String?,
    val areaTematica: String,
    val unidadVecinal: String,
    val fechaInicio: String,
    val fechaTermino: String,
    val estado: String
)

@kotlinx.serialization.Serializable
data class CursoUpdateRequest(
    val titulo: String?,
    val descripcion: String?,
    val areaTematica: String?,
    val unidadVecinal: String?,
    val fechaInicio: String?,
    val fechaTermino: String?,
    val estado: String?
)
```

### 6.3. ViewModel

```kotlin
// viewmodel/CursoViewModel.kt

package com.syncpass.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncpass.app.data.model.Curso
import com.syncpass.app.data.repository.CursoRepository
import com.syncpass.app.data.repository.CursoCreateRequest
import com.syncpass.app.data.repository.CursoUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CursoUiState {
    object Loading : CursoUiState()
    data class Success(val cursos: List<Curso>) : CursoUiState()
    data class Error(val message: String) : CursoUiState()
}

class CursoViewModel(
    private val repository: CursoRepository,
    private val authManager: AuthManager // Tu manager de autenticación
) : ViewModel() {

    private val _uiState = MutableStateFlow<CursoUiState>(CursoUiState.Loading)
    val uiState: StateFlow<CursoUiState> = _uiState.asStateFlow()

    fun loadCursos() {
        viewModelScope.launch {
            _uiState.value = CursoUiState.Loading
            try {
                val jwt = authManager.getJwtToken()
                    ?: throw Exception("No autenticado")
                val cursos = repository.listarCursos(jwt)
                _uiState.value = CursoUiState.Success(cursos)
            } catch (e: Exception) {
                _uiState.value = CursoUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun crearCurso(request: CursoCreateRequest) {
        viewModelScope.launch {
            _uiState.value = CursoUiState.Loading
            try {
                val jwt = authManager.getJwtToken()
                    ?: throw Exception("No autenticado")
                repository.crearCurso(request, jwt)
                loadCursos() // Recargar lista
            } catch (e: Exception) {
                _uiState.value = CursoUiState.Error(e.message ?: "Error al crear curso")
            }
        }
    }

    fun desactivarCurso(id: Long) {
        viewModelScope.launch {
            _uiState.value = CursoUiState.Loading
            try {
                val jwt = authManager.getJwtToken()
                    ?: throw Exception("No autenticado")
                repository.desactivarCurso(id, jwt)
                loadCursos() // Recargar lista
            } catch (e: Exception) {
                _uiState.value = CursoUiState.Error(e.message ?: "Error al desactivar curso")
            }
        }
    }
}
```

---

## 7. Reglas de Negocio

1. **Los cursos desactivados no aparecen en la app.** Al desactivar un curso (`PATCH /desactivar`), el backend cambia el estado a `FINALIZADO`, y el catálogo ya no lo incluye.
2. **Solo `ADMINISTRADOR` puede crear, editar o desactivar.** Si un usuario con otro rol intenta estas operaciones, recibe `403 Forbidden`.
3. **Cualquier usuario autenticado puede leer cursos.** `GET /api/cursos` y `GET /api/cursos/{id}` no requieren rol especial.
4. **Los eventos dentro de un curso no se borran al desactivar.** Solo el curso desaparece del catálogo; los eventos, inscripciones y asistencias se mantienen en la base de datos.
5. **El catálogo existente (`/api/sync/catalogo`) sigue funcionando.** No es necesario migrar a `/api/cursos` si el catálogo actual satisface las necesidades de la app.

---

## 8. Notas Importantes

- **El backend ya está implementado y funcionando.** No necesitas modificar el backend.
- **El catálogo existente ya filtra cursos desactivados.** Si la app usa `GET /api/sync/catalogo`, los cursos creados o desactivados se reflejan automáticamente en la próxima sincronización.
- **`ProyectoDto` y `CursoResponse` tienen la misma estructura.** Los campos son idénticos: `id`, `titulo`, `descripcion`, `areaTematica`, `unidadVecinal`, `fechaInicio`, `fechaTermino`, `estado`.
- **Los endpoints de escritura (`POST`, `PUT`, `PATCH`) están protegidos por rol.** El backend valida el rol `ADMINISTRADOR` en el JWT, no confía en que la app oculte los botones.

---

## 9. Checklist de Implementación

### Mínimo requerido (el catálogo ya funciona)
- [ ] Verificar que `GET /api/sync/catalogo` muestra los cursos creados desde el backend
- [ ] Verificar que los cursos desactivados (`FINALIZADO`) no aparecen en el catálogo

### Opcional: pantalla dedicada de cursos
- [ ] Crear data class `Curso` (misma estructura que `ProyectoDto`)
- [ ] Crear `CursoRepository` con métodos para listar, obtener, crear, editar y desactivar
- [ ] Crear `CursoViewModel` con estado UI
- [ ] Crear pantalla de lista de cursos (lectura para todos, escritura solo `ADMINISTRADOR`)
- [ ] Crear pantalla de detalle de curso
- [ ] Crear formulario de crear/editar curso (solo `ADMINISTRADOR`)
- [ ] Agregar botón de desactivar curso con confirmación (solo `ADMINISTRADOR`)
- [ ] Manejar errores: `400` (validación), `403` (sin permiso), `404` (no encontrado), `409` (ya desactivado)
- [ ] Probar que un usuario no `ADMINISTRADOR` recibe `403` al intentar crear/editar/desactivar
