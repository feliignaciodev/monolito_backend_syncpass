# Alcance funcional — SyncPass

> **Estado (17-11-2026):** levantamiento del **comportamiento implementado** en
> `monolito_backend_syncpass` y su contrato con la app Android (`syncpass-movil`) y el panel web
> (repo externo, pendiente). La disponibilidad de Cognito / Supabase / EC2 / Parameter Store / FCM
> **no fue verificada en esta revisión**: aquí se describe código, tests H2 y contratos, no se declaran
> requisitos cloud cumplidos. Las notificaciones push están **fuera del alcance principal** (parcial /
> backlog). No existe AWS API Gateway desplegado; el ingreso es nginx en EC2.

---

## 1. Requerimientos Funcionales (RF)

| ID | Requerimiento | Estado |
|---|---|---|
| RF-01 | Inicio de sesión con usuario/contraseña contra Cognito; devuelve access token y perfil con rol. | Implementado |
| RF-02 | Auto-registro público (Cognito + perfil local, rol `BENEFICIARIO`), confirmación por código y reenvío de código. | Implementado |
| RF-03 | Consulta del perfil autenticado (`sub` de Cognito y roles) para la app. | Implementado |
| RF-04 | Descarga del catálogo completo (proyectos activos, eventos, participantes, inscripciones) para trabajo offline. | Implementado |
| RF-05 | Registro de asistencia offline y subida por lotes (≤500) con uuid, timestamp, georreferenciación y estado de validación. | Implementado |
| RF-06 | Reintento seguro e idempotencia: uuid repetido y misma persona/evento con otro uuid → `duplicados`, conservando la marca más temprana. | Implementado |
| RF-07 | Inscripción en sitio (*walk-in*): participante desconocido (`W-…`) se crea con alias `Inscrito en sitio`. | Implementado |
| RF-08 | Encuesta offline y subida por lotes (≤500), con deduplicación por participante+evento. | Implementado |
| RF-09 | Métricas en vivo por evento (inscritos, asistentes, quorum, encuestas, promedios, última marcación). | Implementado |
| RF-10 | Reportes para cierre: resumen por proyecto y exportación CSV de asistencias. | Implementado |
| RF-11 | Gestión de usuarios (listar, obtener, crear, activar/desactivar) con compensación Cognito ↔ PostgreSQL. | Implementado (ADMINISTRADOR) |
| RF-12 | CRUD de cursos/proyectos (listar, detalle, crear, editar, desactivar). | Implementado |
| RF-13 | Health check público de la API y del actuator. | Implementado |
| RF-14 | Registro del token FCM del dispositivo del usuario autenticado. | Parcial (código; E2E pendiente) |
| RF-15 | Envío manual de notificaciones por roles. | Parcial (código; credenciales/E2E pendientes) |
| RF-16 | Historial de notificaciones. | **Backlog**: el endpoint devuelve `[]` |
| RF-17 | Renovación de token (refresh). | **No existe** en el backend |
| RF-18 | CRUD general de eventos y participantes. | **No existe** (backlog) |
| RF-19 | Confirmación de inscripción (`/api/sync/confirmar-registro`). | **No existe** (no confundir con RF-02) |
| RF-20 | Reporte CSV por proyecto (`/api/reportes/proyecto/{id}`). | **No existe** (RF-10 cubre `asistencias.csv`) |
| RF-21 | Enrutamiento/gestión con AWS API Gateway. | Opcional, **no desplegado** (nginx actúa como proxy) |

## 2. Requerimientos No Funcionales (RNF)

| ID | Requerimiento |
|---|---|
| RNF-01 | API REST JSON bajo `/api`, **stateless**, CSRF desactivado; Java 21 y Spring Boot 4.1.1. |
| RNF-02 | Resource Server OAuth2/JWT: firma, issuer, expiración, `token_use=access` y `client_id` validados contra Cognito (`SecurityConfiguration#jwtDecoder`). |
| RNF-03 | RBAC por grupos `cognito:groups` → autoridades `ROLE_*`; `hasRole(...)` por endpoint; sin grupo válido → 403. |
| RNF-04 | Formato de error único `{"error":"mensaje"}` (400/401/403/404/405/409/415/500/502 según caso). |
| RNF-05 | Persistencia PostgreSQL/Supabase vía JDBC/JPA, Flyway versionado y Hibernate `ddl-auto=validate`. No editar migraciones aplicadas. |
| RNF-06 | Idempotencia y atomicidad por lote (`@Transactional` en `SyncService`); un registro inválido no invalida el lote. |
| RNF-07 | Límites de validación: lotes ≤500; uuid ≤64; participanteId ≤40; dispositivoId ≤100; comentario ≤500; latitud [-90,90], longitud [-180,180]; distancia ≥0 y finita; satisfacción/utilidad 1..5. |
| RNF-08 | Seguridad de transporte y secretos: HTTPS terminado en nginx; secretos en Parameter Store o variables de entorno; **nunca** en Git (`.env.example` es solo plantilla). |
| RNF-09 | Cobertura mínima del 30% de líneas verificable con JaCoCo (`target/site/jacoco/index.html`). |
| RNF-10 | Logs sin contraseñas ni tokens; datos semilla 100 % ficticios y solo si la base está vacía de proyectos. |
| RNF-11 | Observabilidad: `/actuator/health` y `/api/health`; journald/systemd en EC2. |
| RNF-12 | Independencia de servicios externos en tests: H2 + Cognito simulado; las pruebas no contactan AWS ni Supabase. |

---

## 3. Endpoints reales (24)

| # | Método | Ruta | Acceso | Servicio |
|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | público | `UsuarioService` |
| 2 | POST | `/api/auth/registro` | público | `UsuarioService` |
| 3 | POST | `/api/auth/registro/confirmar` | público | `UsuarioService` |
| 4 | POST | `/api/auth/registro/reenviar-codigo` | público | `UsuarioService` |
| 5 | GET | `/api/me` | autenticado | `AuthenticatedUserController` |
| 6 | GET | `/api/health` (`/actuator/health` también público, vía Actuator) | público | `HealthController` |
| 7 | GET | `/api/sync/catalogo` | autenticado | `CatalogoService` |
| 8 | POST | `/api/sync/asistencias` | autenticado | `SyncService` |
| 9 | POST | `/api/sync/encuestas` | autenticado | `SyncService` |
| 10 | GET | `/api/eventos/{id}/metricas` | COORDINADOR, ADMINISTRADOR | `ReporteService` |
| 11 | GET | `/api/reportes/resumen` | COORDINADOR, ADMINISTRADOR | `ReporteService` |
| 12 | GET | `/api/reportes/asistencias.csv` | COORDINADOR, ADMINISTRADOR | `ReporteService` |
| 13 | GET | `/api/usuarios` | ADMINISTRADOR | `UsuarioService` |
| 14 | GET | `/api/usuarios/{id}` | ADMINISTRADOR | `UsuarioService` |
| 15 | POST | `/api/usuarios` | ADMINISTRADOR | `UsuarioService` |
| 16 | PATCH | `/api/usuarios/{id}/activo` | ADMINISTRADOR | `UsuarioService` |
| 17 | GET | `/api/cursos` | autenticado | `CursoService` |
| 18 | GET | `/api/cursos/{id}` | autenticado | `CursoService` |
| 19 | POST | `/api/cursos` | ADMINISTRADOR | `CursoService` |
| 20 | PUT | `/api/cursos/{id}` | ADMINISTRADOR | `CursoService` |
| 21 | PATCH | `/api/cursos/{id}/desactivar` | ADMINISTRADOR | `CursoService` |
| 22 | POST | `/api/notifications/token` | autenticado | `NotificationService` |
| 23 | POST | `/api/notifications/manual` | ADMINISTRADOR | `NotificationService` |
| 24 | GET | `/api/notifications/historial` | ADMINISTRADOR | `NotificationController` (devuelve `[]`) |

> Nota: los 24 son los *handler methods* de los 9 controladores `@RestController`. `/actuator/health`
> es un endpoint adicional aportado por el framework Actuator (no cuenta en los 24). El historial de
> notificaciones existe como ruta pero devuelve `[]` (backlog).

---

## 4. Casos de uso detallados

### CU-01 — Inicio de sesión (RF-01)
- **Actor:** cualquier perfil (Estudiante/Docente/Voluntario/Administrador).
- **Flujo principal:** la app envía `POST /api/auth/login` `{username,password}` → `CognitoService.login`
  → `Rol.principal(groups)` → se crea/actualiza el perfil local por `sub` → 200
  `{token, usuario:{id,username,nombre,rol}}`.
- **Flujos alternos:** sin grupo Cognito → 403; usuario desactivado → 403; credenciales inválidas →
  401/502 propagando el error de Cognito con formato `{"error":...}`.
- **Postcondición:** la app guarda el access token en DataStore (`TokenDataStore`) y lo envía como
  `Authorization: Bearer` en las siguientes peticiones.

### CU-02 — Auto-registro y confirmación (RF-02)
- **Flujo:** `POST /api/auth/registro` `{nombre,email,password,telefono?}` → SignUp en Cognito, alta en
  grupo `BENEFICIARIO`, perfil local → 201. Luego `POST /api/auth/registro/confirmar`
  `{email,codigo}` → 204; si no llegó el código, `POST .../reenviar-codigo` `{email}` → 204.
- **Alternos:** email ya registrado → 409; fallo al asignar grupo o al guardar en base → se elimina la
  cuenta de Cognito (compensación) y se devuelve el error. El rol **nunca** viene del cliente.

### CU-03 — Descarga del catálogo (RF-04)
- **Flujo:** con conexión, `GET /api/sync/catalogo` → `CatalogoService` entrega `{serverTime,
  proyectos, eventos, participantes, inscripciones}`; la app los persiste en Room (`AppDatabase`).
- **Regla:** se excluyen proyectos `FINALIZADO`. Es la base para operar offline.

### CU-04 — Asistencia offline y subida por lotes (RF-05, RF-07)
- **Flujo principal:** sin conexión, el voluntario/docente escanea el QR y la app crea una `Asistencia`
  local con `uuid`, `eventoId`, `participanteId`, `tipoParticipante`, `timestamp`,
  coordenadas, `estadoValidacion`, `inscritoEnSitio` y `dispositivoId`. Al sincronizar,
  `POST /api/sync/asistencias` con `registros[]` (≤500).
- **Walk-in:** si el `participanteId` (prefijo `W-…`) no existe, `SyncService` crea un `Participante`
  con alias **`Inscrito en sitio`** y el `tipoParticipante` informado (obligatorio en ese caso).
- **Resultado:** `ResultadoSyncResponse{aceptados, duplicados, rechazados, serverTime}`.

### CU-05 — Reintento y duplicados (RF-06)
- **Flujo alterno / reintento:** si la subida falla (red caída), la app vuelve a enviar el mismo lote.
  - `uuid` ya persistido → entra en `duplicados` (no se inserta de nuevo).
  - mismo `eventoId + participanteId` con otro `uuid` → `duplicados`; si la marca nueva es más
    temprana, se reemplaza la anterior por esa marca.
  - La app marca como sincronizados tanto `aceptados` como `duplicados` → no hay reenvío infinito.

### CU-06 — Encuesta offline (RF-08)
- **Flujo:** la app guarda la encuesta local (`uuid`, `eventoId`, `participanteId?`, `satisfaccion`,
  `utilidad`, `comentario?`, `fechaEnvio`) y la envía en `POST /api/sync/encuestas`.
- **Reglas:** satisfacción y utilidad 1..5; un `uuid` repetido o una encuesta previa del mismo
  participante+evento → `duplicados` (las anónimas no se deduplican); comentario ≤500 caracteres.

### CU-07 — Métricas en vivo (RF-09)
- **Actor:** COORDINADOR o ADMINISTRADOR.
- **Flujo:** `GET /api/eventos/{id}/metricas` → inscritos, asistentes, cupos, quorum, `cumpleQuorum`,
  encuestas, tasas, promedios de satisfacción/utilidad y última marcación. Evento inexistente → 404.

### CU-08 — Reportes y CSV (RF-10)
- **Actor:** COORDINADOR o ADMINISTRADOR.
- **Flujo:** `GET /api/reportes/resumen` → fila por proyecto activo; `GET /api/reportes/asistencias.csv`
  → `text/csv; charset=UTF-8` con `Content-Disposition: attachment; filename="asistencias.csv"`.
  Encabezado exacto definido en `ReporteService.ENCABEZADO_CSV`, escape RFC 4180 y timestamps ISO-8601
  con desfase de `America/Santiago`.

### CU-09 — Gestión de usuarios (RF-11)
- **Actor:** ADMINISTRADOR.
- **Flujo:** listar/obtener/crear y `PATCH /{id}/activo`. El alta crea primero en Cognito y luego el
  perfil local; si falla la base, se compensa borrando en Cognito. El cambio de activo revierte la base
  si falla Cognito. Roles válidos: `ADMINISTRADOR`, `COORDINADOR`, `VOLUNTARIO`, `BENEFICIARIO`.

### CU-10 — Cursos / proyectos (RF-12)
- **Flujo:** `GET /api/cursos` y `GET /{id}` para cualquier autenticado; crear/editar/desactivar solo
  ADMINISTRADOR. Desactivar cambia el estado a `FINALIZADO`, por lo que el curso deja de aparecer en
  `GET /api/sync/catalogo` y en reportes.

### CU-11 — Notificaciones (RF-14 a RF-16, **parcial / backlog**)
- **Código presente:** `POST /api/notifications/token` (registra FCM token),
  `POST /api/notifications/manual` (envío por roles), scheduler de recordatorios y eventos de
  inscripción. `GET /api/notifications/historial` devuelve `[]`.
- **No incluido en el alcance principal:** tabla de historial, credenciales FCM de producción,
  integración FCM en la app y panel web. Ver `API_CONTRACT_NOTIFICACIONES.md` (aviso de alcance) y
  la sección 7.

---

## 5. Flujos principal y alternos

```mermaid
flowchart TD
    A[Login · CU-01] --> B[Descargar catálogo · CU-03]
    B --> C[Operar offline: QR / encuesta · CU-04, CU-06]
    C --> D{¿Hay conexión?}
    D -- No --> C
    D -- Sí --> E[Sincronizar lotes]
    E --> F{Resultado por registro}
    F -- aceptados --> G[Marcar sincronizado]
    F -- duplicados --> G
    F -- rechazados --> H[Corregir / descartar registro]
    H --> C
    G --> I[Métricas y reportes · CU-07, CU-08]
    I --> J[Cierre: CSV + resumen]
    A -. Credenciales inválidas / sin rol / inactivo .-> X[401 / 403 → {"error":...}]
    E -. Falla de red .-> C
```

**Flujo alterno de autorización:** un token inválido/expirado produce 401 y la app limpia la sesión
(`TokenAuthenticator`); un rol sin permiso produce 403 con `{"error":...}`. No hay refresh en el
backend: se solicita un nuevo login.

---

## 6. Matriz RF → endpoint → servicio → test

| RF | Endpoint(s) | Servicio / clase | Test (src/test/java/com/backendapp/demo) |
|---|---|---|---|
| RF-01 | `POST /api/auth/login` | `UsuarioService`, `CognitoService` | `AuthTest`, `CognitoServiceRegistroTest` |
| RF-02 | `POST /api/auth/registro` y `/confirmar`, `/reenviar-codigo` | `UsuarioService`, `CognitoService` | `RegistroTest`, `CognitoServiceRegistroTest` |
| RF-03 | `GET /api/me` | `AuthenticatedUserController`, `SecurityConfiguration` | `SeguridadTest` |
| RF-04 | `GET /api/sync/catalogo` | `CatalogoService` | `SyncTest` |
| RF-05 | `POST /api/sync/asistencias` | `SyncService` | `SyncTest` |
| RF-06 | `POST /api/sync/asistencias` (reintentos) | `SyncService` | `SyncTest` |
| RF-07 | `POST /api/sync/asistencias` (`W-…`) | `SyncService` | `SyncTest` |
| RF-08 | `POST /api/sync/encuestas` | `SyncService` | `SyncTest` |
| RF-09 | `GET /api/eventos/{id}/metricas` | `ReporteService` | `ReporteTest` |
| RF-10 | `GET /api/reportes/resumen`, `/asistencias.csv` | `ReporteService` | `ReporteTest` |
| RF-11 | `/api/usuarios*` | `UsuarioService` | `UsuarioTest` |
| RF-12 | `/api/cursos*` | `CursoService` | `CursoTest` |
| RF-13 | `GET /api/health`, `/actuator/health` | `HealthController`, Actuator | cobertura por contexto Spring (`@SpringBootTest`) |
| RF-14 | `POST /api/notifications/token` | `NotificationService` | **Sin test dedicado** (parcial) |
| RF-15 | `POST /api/notifications/manual` | `NotificationService`, `FcmClient` | **Sin test dedicado** (parcial) |
| RF-16 | `GET /api/notifications/historial` | `NotificationController` (devuelve `[]`) | **Sin test dedicado** (backlog) |
| RF-17 a RF-21 | No existen / fuera de alcance | — | — |

**Cobertura:** 64 tests, 0 fallos; JaCoCo 71,79% de líneas (≥ 30% exigido). Reporte en
`target/site/jacoco/index.html` y `target/site/jacoco/jacoco.xml`.

---

## 7. Equivalencia de roles (decisión documental)

| Perfil de negocio / rúbrica | Grupo Cognito | Uso en la API |
|---|---|---|
| Administrador | `ADMINISTRADOR` | Usuarios, cursos (escritura), reportes, métricas, notificaciones admin |
| Docente | `COORDINADOR` | Métricas y reportes |
| Voluntario | `VOLUNTARIO` | Catálogo y sincronización |
| Estudiante | `BENEFICIARIO` | Catálogo y sincronización; rol del auto-registro |

**No se crean ni renombran roles.** «Estudiante» equivale al grupo `BENEFICIARIO`. Los valores
`TipoParticipante.ESTUDIANTE` y `TipoParticipante.DOCENTE` son **tipos de participante** de una marca
de asistencia/encuesta, **no roles Cognito** y no intervienen en `hasRole(...)` (ver
[`ARQUITECTURA.md`, §1.1](ARQUITECTURA.md)).

---

## 8. Backlog explícito (fuera del alcance principal)

| Ítem | Situación real |
|---|---|
| Historial de notificaciones | `GET /api/notifications/historial` devuelve `[]`; falta tabla de historial. |
| Recordatorios / eventos de inscripción (FCM) | Código de scheduler + eventos presente; sin credenciales de producción ni validación E2E; tratar como parcial. |
| Integración FCM en Android y panel web | Pendiente (`docs/PROMPT_MOVIL_NOTIFICACIONES.md`, `docs/PROMPT_WEB_NOTIFICACIONES.md`). |
| Refresh de token | No existe endpoint en el backend; la app limpia la sesión y pide nuevo login (`TokenAuthenticator`). |
| `POST /api/sync/confirmar-registro` | No existe. La confirmación que sí existe es la de **código de email** del registro (`/api/auth/registro/confirmar`). |
| `GET /api/reportes/proyecto/{id}` | No existe. El reporte disponible es `/api/reportes/asistencias.csv` y `/api/reportes/resumen`. |
| CRUD general de eventos y participantes | No existe; solo se gestionan cursos/proyectos y usuarios. |
| API Gateway | Opcional y no desplegado; nginx en EC2 hace de proxy inverso. |
| E2E Android completo | Solo se documentó históricamente el login; pendiente catálogo/sync/reportes. |
| Cloud (Supabase / EC2 / Cognito / Parameter Store) | Código y scripts preparados; disponibilidad actual **no verificada** en esta revisión. |
| OpenAPI/Swagger | Opcional; el contrato vive en Markdown. |

> **No cambiar sin confirmación:** autenticación, roles, contratos REST, entidades, migraciones,
> lógica de notificaciones, configuración de despliegue ni datos semilla.
