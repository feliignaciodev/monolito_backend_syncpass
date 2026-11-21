# SyncPass API

Backend monolítico modular para SyncPass: la app Android que reemplaza las planillas de papel en actividades comunitarias (acreditación por QR, modo sin conexión, encuestas inmediatas y reportes). Se despliega como una aplicación Spring Boot y organiza el código por funcionalidad, sin separar servicios.

> **Arquitectura (C4):** [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md)  
> **Alcance (RF/RNF, casos de uso, backlog):** [`docs/ALCANCE.md`](docs/ALCANCE.md)  
> **Documentación completa, plan y bitácora:** abrir [`docs/syncpass.html`](docs/syncpass.html) en el navegador.  
> **Despliegue paso a paso:** [`pasos.md`](pasos.md).  
> **Estado y pendientes:** [`docs/PENDIENTES.md`](docs/PENDIENTES.md)  
> **Guía para colaboradores:** [`docs/BASTIAN_LEEME.md`](docs/BASTIAN_LEEME.md)

## Contexto rápido

| Componente | Tecnología / estado |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| API | REST bajo `/api`, **mismo contrato que la app Android** (repo `syncpass-movil`; 24 endpoints, ver `docs/ALCANCE.md` §3) |
| Identidad | Amazon Cognito User Pool (usuarios, contraseñas y grupos = roles) |
| Persistencia | PostgreSQL de Supabase mediante JDBC/JPA (Session Pooler, región us-east-2) |
| Migraciones | Flyway (V1 usuarios/roles, V2 dominio, V3 notificaciones); Hibernate `ddl-auto=validate` |
| Aplicación móvil | Kotlin + Compose en `syncpass-movil` (estructura core/ + feature/ con Hilt); solo cambia la URL base |
| Frontend web | React + TypeScript + Vite en `fullstack2-proyecto-kt`, desplegado en Vercel |
| Despliegue | EC2 (Ubuntu 26.04) + nginx + HTTPS (Certbot); scripts en `deploy/`; **sin API Gateway desplegado** (nginx hace de proxy inverso) |

Cognito administra contraseñas e identidad. PostgreSQL guarda el perfil y los datos propios del sistema. El `sub` de Cognito vincula ambos. La API no almacena contraseñas.

### Existe en el código

- Validación JWT de firma, emisor, expiración, `token_use=access` y `client_id`; grupos `cognito:groups` → `ROLE_<GRUPO>`.
- `POST /api/auth/login` con `{username, password}` → `{token, usuario:{id, username, nombre, rol}}` (token = access token de Cognito).
- Dominio completo: proyectos, eventos, participantes, inscripciones, asistencias y encuestas.
- Sincronización offline: catálogo y subida por lotes idempotente (uuid, marca más temprana, inscritos en sitio `W-...`).
- Métricas por evento, resumen por proyecto y CSV para coordinador/administrador.
- Gestión de usuarios (Cognito + perfil local) solo para administrador, con compensación si falla la base.
- Errores siempre `{"error": "mensaje"}` (400/401/403/404/409/502).
- Datos semilla 100 % ficticios (3 proyectos, 6 eventos, 42 participantes) si la base no tiene proyectos.
- **69 pruebas automáticas** (H2 + Cognito simulado; no prueban conectividad cloud).
- **Cobertura JaCoCo 72,6 %** de líneas (mínimo exigido 30 %): `target/site/jacoco/index.html`.
- Notificaciones push: **historial persistido** (`notificacion_historial` + V4). `NotificationController.historial()` devuelve registros reales. Scheduler corregido (dedup por evento+tipo, ventanas no solapadas). FCM sin credenciales de producción (log warning + retorna false). Envío manual a roles o a todos.

### Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/health`, `/actuator/health` | público |
| POST | `/api/auth/login` | público |
| POST | `/api/auth/registro` (`{nombre, email, telefono, password}` → 201; `telefono` en formato E.164, ej. `+56912345678`), `/api/auth/registro/confirmar` (`{email, codigo}` → 204), `/api/auth/registro/reenviar-codigo` (`{email}` → 204) | público |
| GET | `/api/me` | autenticado |
| GET | `/api/sync/catalogo` | autenticado |
| POST | `/api/sync/asistencias`, `/api/sync/encuestas` | autenticado (lotes de max. 500) |
| GET | `/api/cursos`, `/api/cursos/{id}`, `/api/cursos/recientes` | autenticado (recientes es público) |
| POST/PUT/PATCH | `/api/cursos`, `/api/cursos/{id}`, `/api/cursos/{id}/desactivar` | ADMINISTRADOR |
| GET/POST/PATCH | `/api/usuarios`, `GET /api/usuarios/{id}`, `PATCH /api/usuarios/{id}/activo` | ADMINISTRADOR |
| GET | `/api/eventos/{id}/metricas` | COORDINADOR, ADMINISTRADOR |
| GET | `/api/reportes/resumen`, `/api/reportes/asistencias.csv` | COORDINADOR, ADMINISTRADOR |
| POST/GET | `/api/notifications/token`, `/api/notifications/manual`, `/api/notifications/historial` | autenticado / ADMINISTRADOR |

**Auto-registro (web SyncPass).** `POST /api/auth/registro` hace `SignUp` en Cognito, agrega el usuario al grupo `BENEFICIARIO` y guarda el perfil local; el rol nunca viene del cliente. Si falla la asignación del grupo o el guardado en la base, la cuenta se elimina de Cognito. La cuenta queda sin confirmar hasta que el usuario envía el código que le llega al email. Requiere un User Pool que use el **email como nombre de usuario**, con el auto-registro habilitado y los 4 grupos creados.

### Estado real y verificación (20-11-2026)

| Categoría | Estado |
|---|---|
| **Código** | API REST, seguridad JWT/RBAC, sync offline idempotente, reportes, usuarios y cursos implementados. Ver `docs/ARQUITECTURA.md` y `docs/ALCANCE.md`. |
| **Tests H2** | `.\mvnw.cmd verify` → **69 pruebas, 0 fallos**, BUILD SUCCESS; JaCoCo **72,6 %** de líneas. Usan H2 + Cognito simulado: **no** prueban conectividad cloud. |
| **Nube (verificada)** | ✅ Supabase (conexión OK, Flyway V1/V2/V3 aplicadas). ✅ EC2 (Spring Boot corriendo, Java 21, Ubuntu 26.04). ✅ Nginx + HTTPS (Certbot). ✅ Cognito (login real exitoso). ✅ Sync E2E (catálogo + asistencia + idempotencia). ✅ CORS (Vercel origin permitido). |
| **Desplegado** | ✅ EC2 activo. Front en Vercel (`https://fullstack2-proyecto-kt.vercel.app`). API en `https://syncpass.duckdns.org`. **No hay API Gateway desplegado**; nginx actúa como proxy inverso. |
| **E2E Android** | ✅ Login real probado (BENEFICIARIO y COORDINADOR). Sync E2E probado (catálogo + asistencia + idempotencia). |

> Antes de afirmar disponibilidad: `Invoke-RestMethod 'https://<dominio>/api/health'` debe responder `UP`.

## Ruta de trabajo / tickets

Leyenda: `[x] ✅` terminado o comprobado; `[ ] ⏳` pendiente; `🟡` código listo, falta verificar con servicios reales.

### Fase 1 — Base de datos y arranque

- [x] ✅ **DB-01 — Crear Supabase de desarrollo.** Proyecto creado en Ohio (us-east-2).
- [x] ✅ **DB-02 — Conectar Spring a Supabase.** Conexión JDBC verificada (Session Pooler, PostgreSQL 17.11).
- [x] ✅ **DB-03 — Crear migraciones.** V1 (`rol`, `usuario`, 4 roles), V2 (`username` + dominio), V3 (notificaciones).
- [x] ✅ **DB-04 — Aplicar V1, V2 y V3 en Supabase.** Flyway confirmó las 3 migraciones aplicadas.
- [x] ✅ **DB-05 — Validar el esquema remoto.** Hibernate valida las entidades y la aplicación arranca contra Supabase.
- [x] ✅ **DB-06 — Proteger credenciales.** Contraseña solo en Parameter Store de AWS; `.env` en `.gitignore`.

### Fase 2 — Cognito y seguridad del backend

- [x] ✅ **AUTH-01 — Crear Cognito de desarrollo.** User Pool `us-east-1_AwWWrn8Ib`, App Client `47v1h6d5fb83r3cle10gsv4hjq`.
- [x] ✅ **AUTH-02 — Configurar variables Cognito localmente.** Pool y App Client informados y guardados en Parameter Store.
- [x] ✅ **AUTH-03 — Probar JWT real.** Login real probado con cuentas BENEFICIARIO y COORDINADOR; token válido.
- [x] ✅ **AUTH-04 — Login HTTP.** `POST /api/auth/login` público, validado, sin registrar contraseñas ni tokens.
- [x] ✅ **AUTH-05 — Perfil y autorización.** Perfil local creado en el primer login; rol = grupo de mayor privilegio; sin grupo → 403; desactivado → 403.
- [x] ✅ **AUTH-06 — Endpoints de usuario.** Alta, consulta y `PATCH` de activo con compensación Cognito ↔ PostgreSQL.

### Fase 3 — Dominio y API

- [x] ✅ **API-01 — Contrato funcional.** Contrato de la app Android documentado en `docs/ALCANCE.md` §3 (repo móvil `syncpass-movil`).
- [x] ✅ **API-02 — Proyectos y eventos.** Migración, modelo, repositorios, servicios, controllers, DTOs y pruebas.
- [x] ✅ **API-03 — Participantes, asistencias, encuestas y reportes.** Mismo flujo y restricciones por rol.
- [x] ✅ **API-04 — Integración y regresión.** 69 pruebas + conexión real a Supabase + login real con Cognito. ✅
- [ ] ⏳ **API-05 — Documentar contrato.** El contrato existe en Markdown; queda opcional publicar OpenAPI/Swagger.

### Fase 4 — Aplicación móvil

- [x] ✅ **MOBILE-01 — Identificar el proyecto móvil.** Android nativo (Kotlin, Compose, Room, Retrofit) en `syncpass-movil/app` (estructura core/ + feature/ con Hilt).
- [x] ✅ **MOBILE-02 — Flujo de autenticación.** La app recibe el access token de Cognito desde `POST /api/auth/login`; no habla con Cognito directamente.
- [x] ✅ **MOBILE-03 — Configurar ambientes.** URL base configurada a Vercel (proxy a EC2).
- [x] ✅ **MOBILE-04 — Sesión segura.** Token en DataStore privado (sin backup ni logs); release solo HTTPS; 401 marca la sesión como expirada sin perder registros.
- [x] ✅ **MOBILE-05/06 — Validar extremo a extremo.** Login real probado (BENEFICIARIO y COORDINADOR). Sync E2E probado (catálogo + asistencia + idempotencia).

### Fase 5 — Despliegue

- [x] ✅ **DEPLOY-01 — Preparar EC2.** Ubuntu 26.04, Java 21, nginx, usuario `syncpass`, systemd, swap.
- [x] ✅ **DEPLOY-02 — Rol IAM.** `LabRole` asignado a la instancia.
- [x] ✅ **DEPLOY-03 — Secretos y red.** Parameter Store (`/syncpass/prod/*`) con DB_URL, DB_USER, DB_PASSWORD, COGNITO_*, CORS_ALLOWED_ORIGINS.
- [x] ✅ **DEPLOY-04 — HTTPS y observabilidad.** Certbot (Let's Encrypt), logs con `journalctl`.
- [x] ✅ **DEPLOY-05 — Publicación y recuperación.** JAR desplegado, health check OK, servicio activo con reinicio automático.

## Configuración local

`.env.example` es una referencia: Spring no carga archivos `.env` automáticamente. Configurar variables en IntelliJ (**Run → Edit Configurations → Environment variables**):

| Variable | Propósito |
|---|---|
| `DB_URL` | URL JDBC de Supabase Session pooler, incluyendo `sslmode=require` |
| `DB_USER` | Usuario completo mostrado por Supabase, incluyendo sufijo del proyecto |
| `DB_PASSWORD` | Contraseña de PostgreSQL de Supabase |
| `AWS_REGION` | Región del User Pool (`us-east-1`) |
| `COGNITO_USER_POOL_ID` | Identificador del User Pool |
| `COGNITO_CLIENT_ID` | Identificador del App Client |
| `COGNITO_ISSUER` | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` |
| `SYNCPASS_DATOS_SEMILLA` | `true` (por defecto) carga datos ficticios si no hay proyectos |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (Vercel + localhost) |

Pruebas y build (si `JAVA_HOME` no está configurado, sirve el JDK de Android Studio):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\mvnw.cmd clean package
```

Las pruebas usan H2 y Cognito simulado. No prueban conectividad real con Cognito ni con Supabase.
Tras `.\mvnw.cmd verify`, el reporte de cobertura queda en `target/site/jacoco/index.html`
(XML en `target/site/jacoco/jacoco.xml`); la verificación exige al menos 30 % de líneas.

## Uso diario: levantar y comprobar la aplicación

Una vez instalado, el backend se ejecuta como servicio `systemd`; no necesitas abrir una consola ni dejar tu PC encendido. Si la EC2 sigue en ejecución, la API y el sitio permanecen disponibles. El servicio está configurado para iniciar junto con Linux y reiniciarse si falla.

1. En AWS Console → **EC2 → Instances**, confirma que la instancia está en estado **Running**. Si está detenida, selecciónala y pulsa **Instance state → Start instance**. Mientras esté detenida, la app móvil y el sitio no podrán conectarse.
2. Comprueba el estado público de la API desde PowerShell:

   ```powershell
   Invoke-RestMethod 'https://fullstack2-proyecto-kt.vercel.app/api/health'
   ```

   Debe responder `UP`. Con eso el backend está listo; abre la app instalada en el teléfono y asegúrate de tener conexión a Internet.
3. Si no responde, entra por SSH y revisa/arranca los servicios:

   ```powershell
   ssh -i '<RUTA_A_LA_LLAVE.pem>' ec2-user@'<ELASTIC_IP>' `
     'sudo systemctl status syncpass nginx --no-pager'
   ssh -i '<RUTA_A_LA_LLAVE.pem>' ec2-user@'<ELASTIC_IP>' `
     'sudo systemctl restart syncpass nginx'
   ```

   Luego consulta el diagnóstico del backend si sigue fallando:

   ```powershell
   ssh -i '<RUTA_A_LA_LLAVE.pem>' ec2-user@'<ELASTIC_IP>' `
     'sudo journalctl -u syncpass -n 100 --no-pager'
   ```

**Qué ocurre si algo se apaga:** al reiniciar Linux, `systemd` vuelve a levantar la API y Nginx automáticamente. Si se detiene la instancia EC2 (por ejemplo, al finalizar una sesión de AWS Academy), el sistema queda inaccesible hasta que vuelvas a iniciar la instancia; después verifica `/api/health`. La Elastic IP conserva el dominio apuntando al servidor. Las credenciales temporales de AWS CLI de tu PC pueden caducar y bloquear despliegues, pero no detienen por sí solas la API: el servicio EC2 obtiene los parámetros mediante el rol IAM de la instancia.

## Precauciones importantes

- **Migraciones:** Flyway gestiona V1, V2 y V3. Una vez aplicada una version en un ambiente compartido, no editarla: agregar V4, V5, etc.
- **Entornos:** mantener Cognito y Supabase de desarrollo separados de producción.
- **Contraseñas y claves:** no versionar `.env`, credenciales, tokens ni contraseñas. El App Client móvil no tiene client secret. Si una credencial real se expone, rotarla.
- **Política de contraseñas del pool de desarrollo:** relajada (8 caracteres) solo para aceptar las cuentas demo del contrato. No usar en producción.
- **JWT:** se aceptan solo access tokens del issuer/client correctos. Un usuario desactivado conserva su token vigente hasta que expira (12 h); el login posterior queda bloqueado.
- **Supabase RLS:** este backend usa JDBC/JPA directo; la autorización se aplica en la API.
- **Cognito y base de datos:** no hay transacción compartida; la alta de usuarios compensa eliminando en Cognito si falla la base, y el cambio de estado revierte la base si falla Cognito.
- **Datos personales:** solo datos ficticios (restricción 5.1 del caso).
- **Operaciones:** no ejecutar `ddl-auto=create` ni `update`. Conservar `validate` y respaldar antes de migraciones nuevas.

## Repositorios relacionados

- **Backend:** [monolito_backend_syncpass](https://github.com/feliignaciodev/monolito_backend_syncpass) (este repo).
- **Frontend:** [fullstack2-proyecto-kt](https://github.com/BastianGomez-Dc/fullstack2-proyecto-kt) — desplegado en Vercel (`https://fullstack2-proyecto-kt.vercel.app`).
- **Móvil:** [syncpass-movil](https://github.com/feliignaciodev/syncpass-movil) — Kotlin + Compose.
- **Producción API:** `https://syncpass.duckdns.org` (EC2, Ubuntu 26.04, nginx + HTTPS).
- **Producción Front:** `https://fullstack2-proyecto-kt.vercel.app` (Vercel, rewrites `/api/*` → EC2).
