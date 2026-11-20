# SyncPass — Estado del Proyecto y Lo Que Falta

> Documento para Bastian (y quien continúe el proyecto).  
> Repositorio: `monolito_backend_syncpass` (backend Spring Boot).  
> Última actualización: 2026-11-19.

---

## 1. ¿Qué es SyncPass?

Aplicación para gestionar **asistencia a eventos comunitarios** (clases, talleres, actividades). Flujo principal:

1. Un **docente** crea una clase/evento.
2. Un **estudiante** escanea un QR para registrarse.
3. Si no hay internet, la asistencia se guarda **offline** en el dispositivo.
4. Al recuperar conexión, se **sincroniza** con el backend (idempotente por UUID).
5. El docente ve métricas y exporta reportes (CSV).

Perfiles: **Estudiante**, **Docente**, **Voluntario**, **Administrador** (opcional).

---

## 2. Stack

- **Backend:** Spring Boot 4.1.1, Java 21, Maven.
- **BBDD:** PostgreSQL (Supabase, Session Pooler) + Flyway (3 migraciones: V1 roles/usuarios, V2 dominio, V3 notificaciones).
- **Auth:** AWS Cognito (Resource Server, JWT, grupos → roles).
- **Despliegue:** AWS EC2 (scripts en `deploy/`), nginx como reverse proxy, Parameter Store para secretos.
- **Push:** Firebase Cloud Messaging (parcial) + ntfy (SSE).
- **Frontend:** React + TypeScript + Vite + Wouter + Tailwind (repo separado: `fullstack2-proyecto-kt`).
- **Móvil:** Android Kotlin + Jetpack Compose + Room + Hilt + Retrofit (repo separado: `syncpass-movil`).

---

## 3. Estado actual (lo que YA está hecho)

### Código
- ✅ 25 endpoints REST (`/api/auth/*`, `/api/sync/*`, `/api/cursos`, `/api/usuarios`, `/api/eventos`, `/api/reportes`, `/api/notifications`, `/api/health`).
- ✅ Autenticación Cognito real (login, registro, confirmación, reenvío de código).
- ✅ Sincronización offline idempotente (`SyncService`: catálogo, asistencias y encuestas por lotes de hasta 500, UUID, duplicados, rechazos individuales).
- ✅ Roles: `ADMINISTRADOR`, `COORDINADOR`, `VOLUNTARIO`, `BENEFICIARIO` (equivalencias documentadas; no roles literales `ESTUDIANTE`/`DOCENTE`).
- ✅ CORS configurable por env var `CORS_ALLOWED_ORIGINS` (para front en Vercel).
- ✅ Scripts de despliegue EC2 completos (`deploy/deploy.ps1`, `cognito-setup.ps1`, `parameter-store.ps1`, nginx, systemd, HTTPS).

### Tests
- ✅ **69 tests JVM**, 0 fallos (integración Spring + H2 + Cognito simulado).
- ✅ **JaCoCo 72,59 %** de cobertura de líneas (umbral 30 % cumplido).
- ✅ Reporte en `target/site/jacoco/index.html`.

### Documentación
- ✅ `docs/ARQUITECTURA.md` — C1 (contexto), C2 (contenedores real: sin API Gateway), C3 (MVVM).
- ✅ `docs/ALCANCE.md` — RF-01…RF-21, RNF-01…RNF-12, casos de uso, matriz RF→endpoint→servicio→test, equivalencia de roles, backlog.
- ✅ `docs/API_CONTRACT_NOTIFICACIONES.md` — contrato FCM.
- ✅ `docs/PROMPT_MOVIL_CURSOS.md`, `PROMPT_WEB_CURSOS.md`, `PROMPT_MOVIL_NOTIFICACIONES.md`, `PROMPT_WEB_NOTIFICACIONES.md` — guías de integración.
- ✅ `README.md`, `pasos.md`, `docs/PROGRESO_DESARROLLO.md` — estado y procedimiento operativo.

---

## 4. Lo que FALTA (backlog priorizado)

> **Estado actualizado al 19-11-2026 (noche): ver [`PENDIENTES.md`](./PENDIENTES.md)**, con lo que falta para cerrar la entrega, los pasos y su responsable.

### 🔴 Alta prioridad (bloquea la nota o la defensa)
1. **Verificación real de Supabase + EC2 + Cognito + E2E** — el código está listo pero **no se ha probado en vivo**. Hay que desplegar y capturar evidencia (health check, login real, sync de un lote, screenshot de Supabase).
2. **Commit/push del trabajo pendiente** — hay 11 archivos modificados + 21 untracked (cursos, FCM, V3, tests). Si se despliega desde Git sin commitear, no se reproducen. *(Ya se hizo commit en la iteración anterior.)*

### 🟡 Media prioridad
3. **Notificaciones completas** — historial devuelve `[]`, recordatorios pueden duplicarse, FCM sin credenciales provisionadas, envío a todos los dispositivos activos (no por participante).
4. **Refresh de token** — no hay endpoint `/api/auth/refresh`; `TokenAuthenticator` (móvil) borra tokens ante 401.
5. **Roles literales `ESTUDIANTE`/`DOCENTE`** — si el docente exige los nombres exactos, hay que agregarlos en Cognito + Flyway + seguridad (cambio amplio).
6. **Datos semilla** — `SYNCPASS_DATOS_SEMILLA=true` por defecto; desactivar en producción.

### 🟢 Baja prioridad / mejoras
7. **API Gateway** — opcional en la rúbrica; hoy nginx cumple el rol.
8. **Cursos finalizados vs catálogo** — `CatalogoService` filtra proyectos finalizados pero carga todos los eventos/inscripciones (pueden quedar huérfanos).
9. **Script Cognito setup** — `cognito-setup.ps1` crea el pool con alta solo por admin, incompatible con autoregistro público.
10. **Renombrar paquete** `com.backendapp.demo` (cosmético, no necesario).

---

## 5. Cómo correr el backend localmente

### Requisitos
- Java 21+
- Maven (wrapper incluido: `mvnw.cmd`)
- PostgreSQL local o Supabase (Session Pooler)

### Pasos
```powershell
cd C:\Src\monolito_backend_syncpass

# 1. Configurar variables (copiar .env.example y rellenar)
cp .env.example .env

# 2. Compilar y correr tests
.\mvnw.cmd test

# 3. Levantar la app (usa PostgreSQL local por defecto)
.\mvnw.cmd spring-boot:run

# 4. Verificar
curl http://localhost:8080/api/health
```

Por defecto `application.properties` apunta a PostgreSQL local (`localhost:5432`). Para Supabase, setear `DB_URL`, `DB_USER`, `DB_PASSWORD` como variables de entorno.

### Variables de entorno necesarias

| Variable | Descripción |
|---|---|
| `DB_URL` | JDBC URL de PostgreSQL (Supabase Session Pooler) |
| `DB_USER` | Usuario de BD |
| `DB_PASSWORD` | Contraseña de BD |
| `AWS_REGION` | Región de Cognito (ej. `us-east-1`) |
| `COGNITO_USER_POOL_ID` | ID del User Pool |
| `COGNITO_CLIENT_ID` | ID del App Client |
| `COGNITO_ISSUER` | URL del issuer de Cognito |
| `SYNCPASS_DATOS_SEMILLA` | `true`/`false` (datos demo) |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (Vercel + localhost) |
| `FCM_CREDENTIALS_JSON` | (Opcional) Credenciales Firebase |

---

## 6. Endpoints REST (resumen)

### Auth (públicos)
- `POST /api/auth/login` — login contra Cognito, devuelve access token + perfil.
- `POST /api/auth/registro` — registro público (rol `BENEFICIARIO`).
- `POST /api/auth/registro/confirmar` — confirma código.
- `POST /api/auth/registro/reenviar-codigo` — reenvía código.

### Sync (autenticado)
- `GET /api/sync/catalogo` — descarga catálogo para offline.
- `POST /api/sync/asistencias` — sube hasta 500 marcas (idempotente).
- `POST /api/sync/encuestas` — sube hasta 500 encuestas.

### Cursos (autenticado; escritura solo admin)
- `GET /api/cursos`, `GET /api/cursos/{id}`, `POST`, `PUT`, `PATCH /desactivar`.

### Usuarios (solo admin)
- `GET /api/usuarios`, `GET /api/usuarios/{id}`, `POST`, `PATCH /{id}/activo`.

### Reportes (coordinador/admin)
- `GET /api/eventos/{id}/metricas`, `GET /api/reportes/resumen`, `GET /api/reportes/asistencias.csv`.

### Notificaciones (parcial)
- `POST /api/notifications/token`, `POST /api/notifications/manual`, `GET /api/notifications/historial` (devuelve `[]`).

### Health
- `GET /api/health` (público), `GET /actuator/health`.

---

## 7. Roles y equivalencias

| Rol Cognito (backend) | Significado | Rol en rúbrica |
|---|---|---|
| `ADMINISTRADOR` | Admin del sistema | Administrador |
| `COORDINADOR` | Docente/coordinador | Docente |
| `VOLUNTARIO` | Voluntario/monitor | Voluntario |
| `BENEFICIARIO` | Estudiante/asistente | Estudiante |

> **Nota:** `TipoParticipante.ESTUDIANTE` y `TipoParticipante.DOCENTE` clasifican asistentes en la tabla `participante`, **no son roles de autorización Cognito**. Si el docente exige roles literales `ESTUDIANTE`/`DOCENTE` en Cognito, hay que agregarlos (cambio amplio).

---

## 8. Migraciones de BD (Flyway)

- `V1__create_roles_and_users.sql` — roles y usuarios.
- `V2__dominio_syncpass.sql` — dominio completo (proyecto, evento, participante, inscripción, asistencia, encuesta).
- `V3__add_push_notifications.sql` — dispositivos push (notificaciones).

> **Importante:** no editar V1/V2/V3 si ya se aplicaron en producción. Usar V4+ para cambios nuevos. El rollback del JAR no revierte Flyway.

---

## 9. Repositorios relacionados

- **Backend:** `https://github.com/feliignaciodev/monolito_backend_syncpass` (este repo).
- **Frontend (React):** `https://github.com/BastianGomez-Dc/fullstack2-proyecto-kt` — desplegado en Vercel.
- **Móvil (Android):** `https://github.com/feliignaciodev/syncpass-movil` — Kotlin + Compose.

---

## 10. Próximos pasos sugeridos para Bastian

1. **Clonar y correr localmente** (sección 5).
2. **Verificar Supabase:** confirmar que V1/V2/V3 están aplicadas.
3. **Desplegar en EC2** con `deploy/deploy.ps1` y capturar evidencia (health, login, sync).
4. **Configurar `CORS_ALLOWED_ORIGINS`** con el dominio de Vercel.
5. **Probar el flujo completo** desde el front en Vercel → backend EC2 → Supabase.
6. **(Opcional)** Implementar notificaciones completas (historial, dedup, FCM).
7. **(Opcional)** Agregar refresh de token.

---

## 11. Notas importantes

- **Secretos:** nunca commitear `.env` ni credenciales. El `.gitignore` ya protege `.env`, `*.pem`, `*.key`, etc. El `.env.example` es la plantilla.
- **Cobertura:** 69 tests, 72,59 % de líneas (JaCoCo). Reporte en `target/site/jacoco/index.html`.
- **No hay API Gateway desplegado:** nginx actúa como reverse proxy. API Gateway es opcional en la rúbrica.
- **No hay geolocalización** en la app móvil (backlog).
- **Datos semilla:** `SYNCPASS_DATOS_SEMILLA=true` por defecto; desactivar en producción.

---

## 12. Contacto

- Repositorio mantenido por: `feliignaciodev`.
- Para dudas sobre arquitectura, revisar `docs/ARQUITECTURA.md` y `docs/ALCANCE.md`.
- Para el procedimiento de despliegue, revisar `pasos.md` y `deploy/`.
