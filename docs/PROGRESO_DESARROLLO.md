# Progreso de Desarrollo — SyncPass Backend

> **Repositorio:** `monolito_backend_syncpass` (Backend Spring Boot)
> **Fecha de creación:** 2026-11-13
> **Referencia:** EVA-3.md — Rúbrica de Evaluación Prueba 3
>
> **Estado de la documentación (17-11-2026):** se distinguen **código**, **tests H2**,
> **nube no verificada**, **despliegue (scripts preparados)** y **E2E Android**. La disponibilidad
> actual de Supabase / EC2 / Cognito / Parameter Store / FCM **no fue verificada en esta revisión**.
> No existe AWS API Gateway desplegado (nginx en EC2 actúa como proxy inverso). Ver
> [`ARQUITECTURA.md`](ARQUITECTURA.md) y [`ALCANCE.md`](ALCANCE.md).

---

## 1. Resumen del Proyecto

SyncPass es una plataforma de gestión de eventos comunitarios con check-in por QR, modo offline, encuestas y reportes. El ecosistema consta de 3 repositorios:

| Repositorio | Tecnología | Estado |
|-------------|------------|--------|
| **monolito_backend_syncpass** (REPO-BE) | Spring Boot 4, Java 21, PostgreSQL (Supabase) | ✅ Backend completo |
| **fullstack2-proyecto-kt** (REPO-WEB) | Panel web | ⏳ Pendiente de integración |
| **syncpass-movil** (REPO-MOB) | App Android, Kotlin | ⏳ Pendiente de integración |

---

## 2. Requerimientos de EVA-3 vs. Implementación Actual

### 2.1. Infraestructura Cloud y Backend (EVA-3 §1.1.A)

| Requisito EVA-3 | Estado | Evidencia |
|-----------------|--------|-----------|
| Base de datos en **Supabase** | 🛠️ Código / nube no verificada | `application.properties` configurado con PostgreSQL/Supabase; Flyway V1 y V2 (más V3 preparada de notificaciones). Conexión real no verificada en esta revisión |
| API REST en **Spring Boot** | ✅ Código | 9 controladores REST (`@RestController`) + 1 `@RestControllerAdvice`; 24 endpoints (ver `ALCANCE.md` §3) |
| Despliegue en **AWS EC2** | 🛠️ Scripts preparados | `deploy/` presente (nginx, systemd, HTTPS, Parameter Store); instancia/HTTPS no verificados en esta revisión |
| API Gateway (opcional) | ❌ No desplegado | No requerido; nginx en EC2 actúa como proxy inverso. No dibujar como desplegado |
| Autenticación con roles (Cognito) | ✅ Código + tests H2 | Grupos Cognito: ADMINISTRADOR, COORDINADOR, VOLUNTARIO, BENEFICIARIO (equivalencias en `ALCANCE.md` §7); conexión real no verificada |

### 2.2. Aplicación Móvil Android (EVA-3 §1.1.B)

| Requisito EVA-3 | Estado | Notas |
|-----------------|--------|-------|
| App en **Kotlin** | 🛠️ Repo externo | `syncpass-movil` (`C:\Src\syncpass-movil`), no en este repositorio |
| Integración **2 APIs nativas** | 🛠️ Repo externo | Geolocalización y Cámara (QR) |
| Arquitectura **MVVM** | 🛠️ Repo externo | Documentada en `ARQUITECTURA.md` §3 (Screens → ViewModels → Repositorios → Room/DataStore/Retrofit) |
| Persistencia **offline-first** | 🛠️ Repo externo | Room + DataStore en el repo móvil; ver `ARQUITECTURA.md` §3 |
| Sincronización explícita | ✅ Código + tests H2 | `GET /api/sync/catalogo`, `POST /api/sync/asistencias` y `POST /api/sync/encuestas`. **No existe** `/api/sync/confirmar-registro` |

### 2.3. Documentación, Diseño y Testing (EVA-3 §1.2)

| Requisito EVA-3 | Estado | Evidencia |
|-----------------|--------|-----------|
| **ARQUITECTURA.md** (Diagramas C4) | ✅ Documentado | C1, C2 real (sin API Gateway desplegado) y C3 MVVM; trazabilidad a clases reales |
| **ALCANCE.md** (Levantamiento funcional) | ✅ Documentado | RF/RNF numerados, casos de uso, flujos, matriz RF→endpoint→servicio→test y backlog |
| Mockups UI/UX | 🛠️ Parcial | `syncpass.html` existe en `/docs`; trazabilidad de implementado vs. backlog en `ALCANCE.md` |
| Unit Tests | ✅ Tests H2 | 8 clases de test concretas (+ `PruebaBase`), 64 pruebas, 0 fallos; Cognito simulado |
| 30% Code Coverage | ✅ Cumplido | JaCoCo 0.8.16: 71,79% de líneas; `prepare-agent` + `report` (test) + `check` (verify, mín. 0.30) |

---

## 3. Historial de Implementación y Tickets

### [x] **[TICKET-001] [BE] Notificaciones Push — Modelo y Migración**

- **Descripción:** Crear entidad `DispositivoPush`, repositorio y migración Flyway V3 para almacenar tokens FCM.
- **Archivos afectados:**
  - `src/main/java/com/backendapp/demo/domain/DispositivoPush.java`
  - `src/main/java/com/backendapp/demo/repository/DispositivoPushRepository.java`
  - `src/main/resources/db/migration/V3__add_push_notifications.sql`
- **Contrato/Notas:** Tabla `dispositivo_push` con relación a `usuario`. Índices por `fcm_token` y `usuario_id`.
- **Impacto en Web/Móvil:** El móvil debe enviar el token FCM al backend; la web no se ve afectada aún.

---

### [x] **[TICKET-002] [BE] Notificaciones Push — Servicio FCM y Endpoints**

- **Descripción:** Implementar servicio de envío de notificaciones vía Firebase Admin SDK y endpoints REST de gestión.
- **Archivos afectados:**
  - `src/main/java/com/backendapp/demo/service/FcmClient.java`
  - `src/main/java/com/backendapp/demo/service/NotificationService.java`
  - `src/main/java/com/backendapp/demo/api/NotificationController.java`
  - `src/main/java/com/backendapp/demo/config/FcmConfiguration.java`
  - `src/main/java/com/backendapp/demo/config/FcmProperties.java`
  - `src/main/java/com/backendapp/demo/config/SecurityConfiguration.java` (reglas de seguridad)
  - `src/main/java/com/backendapp/demo/dto/TokenRequest.java`
  - `src/main/java/com/backendapp/demo/dto/ManualNotificationRequest.java`
  - `src/main/java/com/backendapp/demo/dto/NotificationResponse.java`
  - `pom.xml` (dependencia `firebase-admin 9.4.3`)
- **Contrato/Notas:**
  - `POST /api/notifications/token` — autenticado, registra token FCM del dispositivo.
  - `POST /api/notifications/manual` — ADMINISTRADOR, envía notificación por roles.
  - `GET /api/notifications/historial` — ADMINISTRADOR, historial (pendiente de implementación completa).
- **Impacto en Web/Móvil:** El móvil consumirá el endpoint de token; la web consumirá el endpoint manual y historial.

---

### [x] **[TICKET-003] [BE] Notificaciones Push — Scheduler y Eventos**

- **Descripción:** Implementar scheduler para recordatorios automáticos y eventos Spring para notificaciones al cambiar estado de inscripción.
- **Archivos afectados:**
  - `src/main/java/com/backendapp/demo/scheduler/NotificationScheduler.java`
  - `src/main/java/com/backendapp/demo/event/InscripcionStateChangedEvent.java`
  - `src/main/java/com/backendapp/demo/event/NotificationEventListener.java`
  - `src/main/java/com/backendapp/demo/SyncPassApiApplication.java` (`@EnableScheduling`)
- **Contrato/Notas:**
  - Scheduler ejecuta cada 15 minutos buscando eventos próximos (1 hora y 1 día antes).
  - Evento Spring `InscripcionStateChangedEvent` dispara notificación al participante al cambiar estado.
- **Impacto en Web/Móvil:** Los usuarios recibirán notificaciones automáticas sin acción adicional.

---

### [ ] **[TICKET-004] [MOB] Integración FCM en App Android**

- **Descripción:** Integrar Firebase Messaging en la app Android y registrar tokens en el backend.
- **Repositorio:** `syncpass-movil` (externo)
- **Archivos esperados:**
  - `app/build.gradle.kts` (dependencia Firebase Messaging)
  - `SyncPassMessagingService.kt` (extiende `FirebaseMessagingService`)
  - `NotificationRepository.kt` (envío de token al backend)
  - `AndroidManifest.xml` (registro del servicio)
- **Contrato/Notas:**
  - `onNewToken()` → enviar token a `POST /api/notifications/token`.
  - `onMessageReceived()` → mostrar notificación y manejar datos (`tipo`, `evento_id`).
  - Al iniciar sesión → obtener token FCM y registrarlo.
- **Dependencia:** TICKET-002 completado (endpoint de token disponible).
- **Documento de referencia:** `docs/PROMPT_MOVIL_NOTIFICACIONES.md`

---

### [ ] **[TICKET-005] [WEB] Panel de Notificaciones**

- **Descripción:** Crear panel de gestión de notificaciones para administradores en la web.
- **Repositorio:** `fullstack2-proyecto-kt` (externo)
- **Archivos esperados:**
  - `NotificationPanel.tsx` (componente UI)
  - `notificationService.ts` (servicio de API)
  - Ruta de historial de notificaciones
- **Contrato/Notas:**
  - Formulario para enviar notificaciones manuales (título, cuerpo, selección de roles).
  - Vista de historial de notificaciones enviadas.
  - Integrar con `POST /api/notifications/manual` y `GET /api/notifications/historial`.
- **Dependencia:** TICKET-002 completado (endpoints disponibles).
- **Documento de referencia:** `docs/PROMPT_WEB_NOTIFICACIONES.md`

---

### [x] **[TICKET-006] [BE] CRUD de Cursos (Proyectos)**

- **Descripción:** Agregar CRUD completo de cursos (sinónimo de Proyecto) con permisos de ADMINISTRADOR.
- **Archivos afectados:**
  - `src/main/java/com/backendapp/demo/dto/CursoResponse.java`
  - `src/main/java/com/backendapp/demo/dto/CursoCreateRequest.java`
  - `src/main/java/com/backendapp/demo/dto/CursoUpdateRequest.java`
  - `src/main/java/com/backendapp/demo/service/CursoService.java`
  - `src/main/java/com/backendapp/demo/api/CursoController.java`
  - `src/main/java/com/backendapp/demo/config/SecurityConfiguration.java` (reglas de seguridad)
  - `src/main/java/com/backendapp/demo/service/CatalogoService.java` (filtrado FINALIZADO)
  - `src/main/java/com/backendapp/demo/service/ReporteService.java` (filtrado FINALIZADO)
  - `src/main/java/com/backendapp/demo/repository/ProyectoRepository.java` (`findByEstadoNot`)
  - `src/main/java/com/backendapp/demo/domain/Proyecto.java` (`@Setter`)
  - `src/test/java/com/backendapp/demo/CursoTest.java` (17 pruebas)
- **Contrato/Notas:**
  - `GET /api/cursos` — listar cursos activos (autenticado).
  - `GET /api/cursos/{id}` — detalle de un curso (autenticado).
  - `POST /api/cursos` — crear un nuevo curso (ADMINISTRADOR).
  - `PUT /api/cursos/{id}` — editar un curso (ADMINISTRADOR).
  - `PATCH /api/cursos/{id}/desactivar` — desactivar un curso (ADMINISTRADOR).
  - Los cursos desactivados cambian estado a `FINALIZADO` y se excluyen del catálogo.
- **Impacto en Web/Móvil:** La app móvil ya consume `/api/sync/catalogo`, que filtra cursos FINALIZADO automáticamente.
- **Pruebas:** 17 casos de prueba en `CursoTest.java` (CRUD, validación, permisos 403, desactivación).

---

### [ ] **[TICKET-007] [MOB] Verificación de Cursos en Catálogo**

- **Descripción:** Verificar que la app móvil muestra los cursos creados desde el backend y que los desactivados no aparecen.
- **Repositorio:** `syncpass-movil` (externo)
- **Archivos afectados:** Ninguno obligatorio (el catálogo ya funciona).
- **Contrato/Notas:**
  - Verificar que `GET /api/sync/catalogo` muestra cursos creados dinámicamente.
  - Verificar que cursos `FINALIZADO` no aparecen en el catálogo.
  - Opcional: pantalla dedicada de cursos con CRUD para ADMINISTRADOR.
- **Dependencia:** TICKET-006 completado.
- **Documento de referencia:** `docs/PROMPT_MOVIL_CURSOS.md`

---

### [ ] **[TICKET-008] [WEB] Panel de Gestión de Cursos**

- **Descripción:** Crear panel web para que ADMINISTRADOR gestione cursos (crear, editar, desactivar).
- **Repositorio:** `fullstack2-proyecto-kt` (externo)
- **Archivos esperados:**
  - `CursosPage.tsx` (página principal de cursos)
  - `CursoForm.tsx` (formulario crear/editar)
  - `cursoService.ts` (servicio de API)
  - `useCursos.ts` (hook de estado)
- **Contrato/Notas:**
  - Tabla de cursos con acciones: editar, desactivar.
  - Formulario con validación de campos obligatorios.
  - Confirmación antes de desactivar.
  - Integrar con `GET /api/cursos`, `POST /api/cursos`, `PUT /api/cursos/{id}`, `PATCH /api/cursos/{id}/desactivar`.
- **Dependencia:** TICKET-006 completado.
- **Documento de referencia:** `docs/PROMPT_WEB_CURSOS.md`

---

## 4. Endpoints del Backend (Resumen)

> Listado completo y verificado de los 24 endpoints en [`ALCANCE.md` §3](ALCANCE.md).
> **No existen** `/api/sync/confirmar-registro` ni `/api/reportes/proyecto/{id}`.

### Endpoints existentes (pre-existentes)

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `POST` | `/api/auth/login` | — | Cualquier usuario | Login con Cognito |
| `POST` | `/api/auth/registro`, `/api/auth/registro/confirmar`, `/api/auth/registro/reenviar-codigo` | — | Cualquier usuario | Registro y confirmación de email |
| `GET` | `/api/me` | JWT | Autenticado | Perfil (`sub`) y roles del token |
| `GET` | `/api/sync/catalogo` | JWT | Autenticado | Catálogo completo (proyectos + eventos) |
| `POST` | `/api/sync/asistencias`, `/api/sync/encuestas` | JWT | Autenticado | Subida idempotente por lotes |
| `GET` | `/api/eventos/{id}/metricas` | JWT | COORDINADOR, ADMINISTRADOR | Métricas en vivo del evento |
| `GET` | `/api/reportes/resumen`, `/api/reportes/asistencias.csv` | JWT | COORDINADOR, ADMINISTRADOR | Resumen y CSV global de asistencias |
| `GET` | `/api/usuarios`, `GET /{id}`, `POST`, `PATCH /{id}/activo` | JWT | ADMINISTRADOR | Gestión de usuarios |
| `GET` | `/api/health`, `/actuator/health` | — | — | Health check |

### Endpoints de Notificaciones Push (TICKET-002)

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `POST` | `/api/notifications/token` | JWT | Autenticado | Registrar token FCM |
| `POST` | `/api/notifications/manual` | JWT | ADMINISTRADOR | Enviar notificación manual |
| `GET` | `/api/notifications/historial` | JWT | ADMINISTRADOR | **Devuelve `[]`** (tabla de historial en backlog) |

> Notificaciones: alcance **parcial / backlog** (token y envío manual con código; sin credenciales FCM
> de producción, sin tests dedicados y sin historial persistido). No rehacer notificaciones.

### Endpoints de Cursos (TICKET-006)

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `GET` | `/api/cursos` | JWT | Autenticado | Listar cursos activos |
| `GET` | `/api/cursos/{id}` | JWT | Autenticado | Detalle de un curso |
| `POST` | `/api/cursos` | JWT | ADMINISTRADOR | Crear un curso |
| `PUT` | `/api/cursos/{id}` | JWT | ADMINISTRADOR | Editar un curso |
| `PATCH` | `/api/cursos/{id}/desactivar` | JWT | ADMINISTRADOR | Desactivar un curso |

---

## 5. Estado General por Repositorio

### REPO-BE (monolito_backend_syncpass) — Este repositorio

| Componente | Estado | Pruebas |
|------------|--------|---------|
| Autenticación Cognito | ✅ Completado | `AuthTest.java`, `RegistroTest.java`, `CognitoServiceRegistroTest.java` |
| Catálogo y Sync | ✅ Completado | `SyncTest.java` |
| Reportes CSV | ✅ Completado | `ReporteTest.java` |
| Usuarios CRUD | ✅ Completado | `UsuarioTest.java` |
| Seguridad (roles) | ✅ Completado | `SeguridadTest.java` |
| Notificaciones Push (FCM) | ⏳ Parcial / backlog | Código de token/manual/scheduler; historial devuelve `[]`; sin tests dedicados |
| CRUD Cursos | ✅ Código + tests H2 | `CursoTest.java` (17 pruebas) |
| **Total pruebas** | **64 pruebas, 0 fallos (tests H2)** | `.\mvnw.cmd verify` → BUILD SUCCESS; JaCoCo 71,79% líneas |

**Leyenda de estado (REPO-BE):** ✅ código + tests H2 · 🛠️ código/scripts listos, nube no verificada ·
⏳ parcial/backlog. Las pruebas usan H2 y Cognito simulado: **no** prueban conectividad con AWS ni Supabase.
El login móvil se documentó históricamente como E2E; catálogo/sync/reportes desde la app siguen pendientes.

### REPO-WEB (fullstack2-proyecto-kt) — Repositorio externo

| Componente | Estado | Documento de referencia |
|------------|--------|------------------------|
| Panel de Notificaciones | ⏳ Pendiente | `docs/PROMPT_WEB_NOTIFICACIONES.md` |
| Panel de Cursos | ⏳ Pendiente | `docs/PROMPT_WEB_CURSOS.md` |

### REPO-MOB (syncpass-movil) — Repositorio externo

| Componente | Estado | Documento de referencia |
|------------|--------|------------------------|
| Integración FCM | ⏳ Pendiente | `docs/PROMPT_MOVIL_NOTIFICACIONES.md` |
| Verificación Cursos | ⏳ Pendiente | `docs/PROMPT_MOVIL_CURSOS.md` |

---

## 6. Documentos en `/docs`

| Archivo | Propósito |
|---------|-----------|
| `eva-3.md` | Rúbrica de evaluación EVA-3 (referencia) |
| `PROGRESO_DESARROLLO.md` | Este archivo — seguimiento de tickets y progreso |
| `API_CONTRACT_NOTIFICACIONES.md` | Especificación completa de API de notificaciones |
| `PROMPT_WEB_NOTIFICACIONES.md` | Prompt para integrar notificaciones en la web |
| `PROMPT_MOVIL_NOTIFICACIONES.md` | Prompt para integrar FCM en la app móvil |
| `PROMPT_WEB_CURSOS.md` | Prompt para integrar CRUD de cursos en la web |
| `PROMPT_MOVIL_CURSOS.md` | Prompt para verificar cursos en la app móvil |
| `syncpass.html` | Mockup visual de la aplicación |

---

## 7. Pendiente para EVA-3

### Documentos (EVA-3 §1.2.A) — completados en esta revisión

- [x] **`ARQUITECTURA.md`** — Diagramas C4:
  - [x] C1 (Contexto): usuarios/perfiles, Android, Web, Cognito, Supabase, FCM (parcial).
  - [x] C2 (Contenedores): Android/Web → HTTPS/nginx EC2 → Spring Boot → Cognito/Supabase/Parameter Store. **Sin API Gateway desplegado**.
  - [x] C3 (Componentes): MVVM de la app (`syncpass-movil`): Screens → ViewModels → Repositorios → Room/DataStore/Retrofit.
- [x] **`ALCANCE.md`** — Levantamiento funcional:
  - [x] RF/RNF numerados, casos de uso detallados, flujos principal/alternos.
  - [x] Matriz RF→endpoint→servicio→test, equivalencia de roles y backlog explícito.

### Testing y cobertura (EVA-3 §1.2.C)

- [x] **Reporte JaCoCo** — `jacoco-maven-plugin` 0.8.16 en `pom.xml`: `prepare-agent`, `report` en `test` y `check` en `verify` (mínimo 0.30 de líneas). Resultado real: **71,79%** (855 cubiertas / 1191). Reporte: `target/site/jacoco/index.html` y `target/site/jacoco/jacoco.xml`.
- [ ] **Pruebas de notificaciones** — Backlog (alcance parcial; no rehacer notificaciones).

### Despliegue (EVA-3 §1.1.A) — no verificado en esta revisión

- [ ] **AWS EC2** — Verificar instancia activa y accesible (scripts `deploy/` preparados).
- [ ] **Configuración FCM** — Credenciales de producción y E2E pendientes (fuera del alcance principal).

### Entidades pendientes de mantenimiento

Las siguientes entidades podrían necesitar mantenedores CRUD similares al de cursos en futuras entregas:

- [ ] **Eventos/Talleres** — CRUD completo con permisos de COORDINADOR/ADMINISTRADOR.
- [ ] **Participantes** — Gestión de participantes registrados.
- [ ] **Encuestas** — Creación y edición de encuestas de satisfacción.
- [ ] **Inscripciones** — Panel de aprobación/rechazo de inscripciones.

> **Nota:** No implementar sin confirmación explícita del usuario.

---

## 8. Comandos de Verificación

```powershell
# Compilar el proyecto
.\mvnw.cmd clean compile -DskipTests

# Ejecutar todas las pruebas (64) y generar el reporte JaCoCo
.\mvnw.cmd clean test

# Ejecutar pruebas + verificar cobertura mínima del 30%
.\mvnw.cmd verify

# Ejecutar pruebas específicas
.\mvnw.cmd test -Dtest=CursoTest
.\mvnw.cmd test -Dtest=SyncTest
```

Reporte HTML: `target/site/jacoco/index.html` · XML: `target/site/jacoco/jacoco.xml` · datos: `target/jacoco.exec`.

---

## 9. Configuración para Producción

### Variables de entorno requeridas

```properties
# Cognito (Autenticación) — nombres reales de application.properties
AWS_REGION=us-east-1
COGNITO_USER_POOL_ID=us-east-1_xxxxx
COGNITO_CLIENT_ID=xxxxx
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxx

# Base de datos (Supabase)
DB_URL=jdbc:postgresql://...
DB_USER=postgres.<project-ref>
DB_PASSWORD=...
```

En EC2 estos seis valores se leen de `/syncpass/prod/*` (Parameter Store) mediante
`deploy/server/start.sh`. Las propiedades FCM (`fcm.projectId`, `fcm.credentialsPath`) quedan en backlog:
**no hay credenciales de producción configuradas ni verificadas**.

---

*Última actualización: 2026-11-17 (corrección de coherencia documental; cobertura JaCoCo).*