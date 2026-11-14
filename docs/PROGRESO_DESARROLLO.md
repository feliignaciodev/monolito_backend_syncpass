# Progreso de Desarrollo — SyncPass Backend

> **Repositorio:** `monolito_backend_syncpass` (Backend Spring Boot)
> **Fecha de creación:** 2026-11-13
> **Referencia:** EVA-3.md — Rúbrica de Evaluación Prueba 3

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
| Base de datos en **Supabase** | ✅ Completado | `application.properties` configurado con PostgreSQL/Supabase, migraciones Flyway V1 y V2 |
| API REST en **Spring Boot** | ✅ Completado | 80 archivos Java, 12 controladores REST |
| Despliegue en **AWS EC2** | ⏳ Pendiente | Script `deploy/` presente, sin verificar instancia activa |
| API Gateway (opcional) | ❌ No implementado | No requerido para EVA-3 |
| Autenticación con roles (Cognito) | ✅ Completado | AWS Cognito integrado con roles: ADMINISTRADOR, COORDINADOR, VOLUNTARIO, BENEFICIARIO |

### 2.2. Aplicación Móvil Android (EVA-3 §1.1.B)

| Requisito EVA-3 | Estado | Notas |
|-----------------|--------|-------|
| App en **Kotlin** | ⏳ Repo externo | `syncpass-movil`, no en este repositorio |
| Integración **2 APIs nativas** | ⏳ Repo externo | Geolocalización y Cámara (QR) |
| Arquitectura **MVVM** | ⏳ Repo externo | Verificar en repo móvil |
| Persistencia **offline-first** | ⏳ Repo externo | Verificar en repo móvil |
| Sincronización explícita | ✅ Backend listo | `GET /api/sync/catalogo` y `POST /api/sync/confirmar-registro` |

### 2.3. Documentación, Diseño y Testing (EVA-3 §1.2)

| Requisito EVA-3 | Estado | Evidencia |
|-----------------|--------|-----------|
| **ARQUITECTURA.md** (Diagramas C4) | ❌ No implementado | Faltan: C1 (Contexto), C2 (Contenedores), C3 (Componentes) |
| **ALCANCE.md** (Levantamiento funcional) | ❌ No implementado | Faltan: RF, RNF, casos de uso, flujos |
| Mockups UI/UX | ❌ No implementado | `syncpass.html` existe en `/docs`, pero sin trazabilidad |
| Unit Tests | ✅ Parcial | 9 clases de test en backend (64 pruebas pasando) |
| 30% Code Coverage | ⏳ Pendiente | Solo backend tiene tests; falta reporte JaCoCo |

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

### Endpoints existentes (pre-existentes)

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `POST` | `/api/auth/login` | — | Cualquier usuario | Login con Cognito |
| `POST` | `/api/auth/registro` | — | Cualquier usuario | Registro de usuario |
| `GET` | `/api/sync/catalogo` | JWT | Autenticado | Catálogo completo (proyectos + eventos) |
| `POST` | `/api/sync/confirmar-registro` | JWT | Autenticado | Confirmar inscripción |
| `GET` | `/api/reportes/proyecto/{id}` | JWT | Autenticado | Reporte CSV por proyecto |
| `GET` | `/api/health` | — | — | Health check |

### Endpoints de Notificaciones Push (TICKET-002)

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `POST` | `/api/notifications/token` | JWT | Autenticado | Registrar token FCM |
| `POST` | `/api/notifications/manual` | JWT | ADMINISTRADOR | Enviar notificación manual |
| `GET` | `/api/notifications/historial` | JWT | ADMINISTRADOR | Historial de notificaciones |

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
| Notificaciones Push (FCM) | ✅ Completado | Pendiente de pruebas específicas |
| CRUD Cursos | ✅ Completado | `CursoTest.java` (17 pruebas) |
| **Total pruebas** | **64 pruebas pasando** | `mvn test` → BUILD SUCCESS |

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

### Documentos faltantes (EVA-3 §1.2.A)

- [ ] **`ARQUITECTURA.md`** — Diagramas C4:
  - [ ] C1 (Contexto): Interacción del sistema con usuarios y servicios externos.
  - [ ] C2 (Contenedores): Flujo App Móvil → API → Cognito → EC2 → Supabase.
  - [ ] C3 (Componentes): Arquitectura MVVM de la app móvil.
- [ ] **`ALCANCE.md`** — Levantamiento funcional:
  - [ ] Requerimientos Funcionales (RF).
  - [ ] Requerimientos No Funcionales (RNF).
  - [ ] Casos de uso detallados.
  - [ ] Flujos principales de la aplicación.

### Testing y cobertura (EVA-3 §1.2.C)

- [ ] **Reporte JaCoCo** — Generar reporte de cobertura de código (mínimo 30%).
- [ ] **Pruebas de notificaciones** — Agregar tests para `NotificationService` y `NotificationScheduler`.

### Despliegue (EVA-3 §1.1.A)

- [ ] **AWS EC2** — Verificar instancia activa y accesible.
- [ ] **Configuración FCM** — Agregar credenciales de producción (`fcm.projectId`, `fcm.credentialsPath`).

### Entidades pendientes de mantenimiento

Las siguientes entidades podrían necesitar mantenedores CRUD similares al de cursos en futuras entregas:

- [ ] **Eventos/Talleres** — CRUD completo con permisos de COORDINADOR/ADMINISTRADOR.
- [ ] **Participantes** — Gestión de participantes registrados.
- [ ] **Encuestas** — Creación y edición de encuestas de satisfacción.
- [ ] **Inscripciones** — Panel de aprobación/rechazo de inscripciones.

> **Nota:** No implementar sin confirmación explícita del usuario.

---

## 8. Comandos de Verificación

```bash
# Compilar el proyecto
mvn clean compile -DskipTests

# Ejecutar todas las pruebas
mvn test

# Ejecutar pruebas específicas
mvn test -Dtest=CursoTest
mvn test -Dtest=SeguridadTest
mvn test -Dtest=SyncTest

# Generar reporte de cobertura (si JaCoCo está configurado)
mvn jacoco:report
```

---

## 9. Configuración para Producción

### Variables de entorno requeridas

```properties
# FCM (Notificaciones Push)
fcm.projectId=tu-proyecto-firebase
fcm.credentialsPath=/ruta/a/credenciales.json

# Cognito (Autenticación)
cognito.region=us-east-1
cognito.userPoolId=us-east-1_xxxxx
cognito.clientId=xxxxx

# Base de datos (Supabase)
spring.datasource.url=jdbc:postgresql://...
spring.datasource.username=...
spring.datasource.password=...
```

---

*Última actualización: 2026-11-13*