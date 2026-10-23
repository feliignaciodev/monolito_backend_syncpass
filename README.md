# SyncPass API

Backend monolitico modular para SyncPass: la app Android que reemplaza las planillas de papel en actividades comunitarias (acreditacion por QR, modo sin conexion, encuestas inmediatas y reportes). Se despliega como una aplicacion Spring Boot y organiza el codigo por funcionalidad, sin separar servicios.

> 📘 **Documentacion completa, plan y bitacora:** abrir [`docs/syncpass.html`](docs/syncpass.html) en el navegador.
> 🚀 **Despliegue paso a paso:** [`pasos.md`](pasos.md).

## 🧭 Contexto rapido

| Componente | Tecnologia / estado |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| API | REST bajo `/api`, **mismo contrato que la app Android** (`projects-duoc/Movile/docs/contrato-api.md`) |
| Identidad | Amazon Cognito User Pool (usuarios, contrasenas y grupos = roles) |
| Persistencia | PostgreSQL de Supabase mediante JDBC/JPA |
| Migraciones | Flyway (V1 usuarios/roles, V2 dominio); Hibernate `ddl-auto=validate` |
| Aplicacion movil | Kotlin + Compose en `projects-duoc/Movile/android`; solo cambia la URL base |
| Despliegue | EC2 + nginx + HTTPS; scripts listos en `deploy/` |

Cognito administra contrasenas e identidad. PostgreSQL guarda el perfil y los datos propios del sistema. El `sub` de Cognito vincula ambos. La API no almacena contrasenas.

### ✅ Existe en el codigo

- Validacion JWT de firma, emisor, expiracion, `token_use=access` y `client_id`; grupos `cognito:groups` → `ROLE_<GRUPO>`.
- `POST /api/auth/login` con `{username, password}` → `{token, usuario:{id, username, nombre, rol}}` (token = access token de Cognito).
- Dominio completo: proyectos, eventos, participantes, inscripciones, asistencias y encuestas.
- Sincronizacion offline: catalogo y subida por lotes idempotente (uuid, marca mas temprana, inscritos en sitio `W-...`).
- Metricas por evento, resumen por proyecto y CSV para coordinador/administrador.
- Gestion de usuarios (Cognito + perfil local) solo para administrador, con compensacion si falla la base.
- Errores siempre `{"error": "mensaje"}` (400/401/403/404/409/502).
- Datos semilla 100 % ficticios (3 proyectos, 6 eventos, 40 participantes) si la base no tiene proyectos.
- 33 pruebas automaticas (H2 + Cognito simulado; tambien pasan sobre PostgreSQL 17).

### Endpoints

| Metodo | Ruta | Acceso |
|---|---|---|
| GET | `/api/health`, `/actuator/health` | publico |
| POST | `/api/auth/login` | publico |
| GET | `/api/me` | autenticado |
| GET | `/api/sync/catalogo` | autenticado |
| POST | `/api/sync/asistencias`, `/api/sync/encuestas` | autenticado (lotes de max. 500) |
| GET | `/api/eventos/{id}/metricas` | COORDINADOR, ADMINISTRADOR |
| GET | `/api/reportes/resumen`, `/api/reportes/asistencias.csv` | COORDINADOR, ADMINISTRADOR |
| GET/POST | `/api/usuarios`, `GET /api/usuarios/{id}`, `PATCH /api/usuarios/{id}/activo` | ADMINISTRADOR |

### 📍 Estado comprobado

- ✅ Se creo el proyecto Supabase `syncpass-dev` en East US (Ohio) y la aplicacion llego a conectarse al Session pooler.
- ✅ Flyway V1 y V2 se ejecutan en H2 (modo PostgreSQL) y en PostgreSQL 17 local (Docker); Hibernate valida el esquema y las 33 pruebas pasan en ambos.
- ✅ `mvnw clean package`: 33 pruebas OK y JAR generado.
- ❌ Falta comprobar V1 + V2 en Supabase real, Cognito real, EC2 y la app contra el servidor publicado.

## 🗺️ Ruta de trabajo / tickets

Leyenda: `[x] ✅` terminado o comprobado; `[ ] ❌` pendiente; `🛠️` codigo listo, falta verificar con servicios reales.

### Fase 1 — Base de datos y arranque

- [x] ✅ **DB-01 — Crear Supabase de desarrollo.** Proyecto creado en Ohio.
- [x] ✅ **DB-02 — Conectar Spring a Supabase.** El log confirmo `HikariPool - Start completed` y PostgreSQL 17.11.
- [x] ✅ **DB-03 — Crear migraciones.** V1 (`rol`, `usuario`, 4 roles) y V2 (`username` + dominio).
- [ ] ❌ **DB-04 — Aplicar V1 y V2 en Supabase.** Arrancar en IntelliJ y confirmar en logs que Flyway aplica las versiones 1 y 2 y Spring termina con `Started SyncPassApiApplication`.
- [ ] ❌ **DB-05 — Inspeccionar el esquema remoto.** Tablas `rol`, `usuario`, `proyecto`, `evento`, `participante`, `inscripcion`, `asistencia`, `encuesta`, `flyway_schema_history`.
- [ ] ❌ **DB-06 — Proteger credenciales.** La contrasena real solo en IntelliJ local y en Parameter Store.

### Fase 2 — Cognito y seguridad del backend

- [ ] 🛠️ **AUTH-01 — Crear Cognito de desarrollo.** `deploy/cognito-setup.ps1` crea pool, App Client sin secreto (tokens de 12 h), grupos y usuarios demo.
- [ ] ❌ **AUTH-02 — Configurar variables Cognito.** `AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_ISSUER`.
- [ ] 🛠️ **AUTH-03 — Probar JWT real.** Cubierto en pruebas con tokens simulados (401/403); falta probar con Cognito real.
- [x] ✅ **AUTH-04 — Login HTTP.** `POST /api/auth/login` publico, validado, sin registrar contrasenas ni tokens.
- [x] ✅ **AUTH-05 — Perfil y autorizacion.** Perfil local creado en el primer login; rol = grupo de mayor privilegio; sin grupo → 403; desactivado → 403.
- [x] ✅ **AUTH-06 — Endpoints de usuario.** Alta, consulta y `PATCH` de activo con compensacion Cognito ↔ PostgreSQL.

### Fase 3 — Dominio y API

- [x] ✅ **API-01 — Contrato funcional.** Se adopta el contrato de la app Android (`Movile/docs/contrato-api.md`).
- [x] ✅ **API-02 — Proyectos y eventos.** Migracion, modelo, repositorios, servicios, controllers, DTOs y pruebas.
- [x] ✅ **API-03 — Participantes, asistencias, encuestas y reportes.** Mismo flujo y restricciones por rol.
- [ ] 🛠️ **API-04 — Integracion y regresion.** Pruebas completas OK contra PostgreSQL 17 local; falta Supabase real y Cognito de desarrollo.
- [ ] ❌ **API-05 — Documentar contrato.** El contrato existe en Markdown; queda opcional publicar OpenAPI/Swagger.

### Fase 4 — Aplicacion movil

- [x] ✅ **MOBILE-01 — Identificar el proyecto movil.** Android nativo (Kotlin, Compose, Room, Retrofit) en `projects-duoc/Movile/android`.
- [x] ✅ **MOBILE-02 — Flujo de autenticacion.** La app recibe el access token de Cognito desde `POST /api/auth/login`; no habla con Cognito directamente.
- [ ] ❌ **MOBILE-03 — Configurar ambientes.** En la app, *Configurar servidor* → `https://<dominio>/api/`.
- [ ] ❌ **MOBILE-04 — Sesion segura.** Revisar almacenamiento del token y poner `cleartextTrafficPermitted="false"` al usar HTTPS.
- [ ] ❌ **MOBILE-05/06 — Validar extremo a extremo** con usuarios demo ficticios.

### Fase 5 — Despliegue

- [ ] 🛠️ **DEPLOY-01 — Preparar EC2.** `deploy/server/setup-server.sh` (Java 21, nginx, usuario sin privilegios, swap, systemd).
- [ ] 🛠️ **DEPLOY-02 — Rol IAM.** En AWS Academy: `LabInstanceProfile` al lanzar la EC2. En cuentas normales: politica minima en `deploy/iam-policy.json`.
- [ ] 🛠️ **DEPLOY-03 — Secretos y red.** `deploy/parameter-store.ps1` + `start.sh` (lee Parameter Store al arrancar).
- [ ] 🛠️ **DEPLOY-04 — HTTPS y observabilidad.** `deploy/server/setup-https.sh` (Let's Encrypt), logs con `journalctl`.
- [ ] 🛠️ **DEPLOY-05 — Publicacion y recuperacion.** `deploy/deploy.ps1` (build con pruebas, subida, health check) y `-Rollback`.

## ⚙️ Configuracion local

`.env.example` es una referencia: Spring no carga archivos `.env` automaticamente. Configurar variables en IntelliJ (**Run → Edit Configurations → Environment variables**):

| Variable | Proposito |
|---|---|
| `DB_URL` | URL JDBC de Supabase Session pooler, incluyendo `sslmode=require` |
| `DB_USER` | Usuario completo mostrado por Supabase, incluyendo sufijo del proyecto |
| `DB_PASSWORD` | Contrasena de PostgreSQL de Supabase |
| `AWS_REGION` | Region del User Pool (`us-east-1`: AWS Academy solo permite us-east-1 y us-west-2) |
| `COGNITO_USER_POOL_ID` | Identificador del User Pool |
| `COGNITO_CLIENT_ID` | Identificador del App Client |
| `COGNITO_ISSUER` | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` |
| `SYNCPASS_DATOS_SEMILLA` | `true` (por defecto) carga datos ficticios si no hay proyectos |

Pruebas y build (si `JAVA_HOME` no esta configurado, sirve el JDK de Android Studio):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\mvnw.cmd clean package
```

Las pruebas usan H2 y Cognito simulado. No prueban conectividad real con Cognito ni con Supabase.

## 🛡️ Precauciones importantes

- **Migraciones:** Flyway aplica V1 y V2 automaticamente. Una vez aplicada una version en un ambiente compartido, no editarla: agregar V3, V4, etc.
- **Entornos:** mantener Cognito y Supabase de desarrollo separados de produccion.
- **Contrasenas y claves:** no versionar `.env`, credenciales, tokens ni contrasenas. El App Client movil no tiene client secret. Si una credencial real se expone, rotarla.
- **Politica de contrasenas del pool de desarrollo:** relajada (8 caracteres) solo para aceptar las cuentas demo del contrato. No usar en produccion.
- **JWT:** se aceptan solo access tokens del issuer/client correctos. Un usuario desactivado conserva su token vigente hasta que expira (12 h); el login posterior queda bloqueado.
- **Supabase RLS:** este backend usa JDBC/JPA directo; la autorizacion se aplica en la API.
- **Cognito y base de datos:** no hay transaccion compartida; la alta de usuarios compensa eliminando en Cognito si falla la base, y el cambio de estado revierte la base si falla Cognito.
- **Datos personales:** solo datos ficticios (restriccion 5.1 del caso).
- **Operaciones:** no ejecutar `ddl-auto=create` ni `update`. Conservar `validate` y respaldar antes de migraciones nuevas.
