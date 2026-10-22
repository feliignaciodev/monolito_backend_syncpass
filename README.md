# SyncPass API

Backend monolitico modular para SyncPass. Se despliega como una aplicacion Spring Boot y organiza el codigo por funcionalidad, sin separar servicios.

## 🧭 Contexto rapido

| Componente | Tecnologia / estado |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| API | REST, prefijo previsto `/api/v1` |
| Identidad | Amazon Cognito User Pool |
| Persistencia | PostgreSQL de Supabase mediante JDBC/JPA |
| Migraciones | Flyway; Hibernate `ddl-auto=validate` |
| Aplicacion movil | Pendiente de integrar; su tecnologia/repositorio no esta en este workspace |
| Despliegue | EC2 pendiente |

Cognito administra contrasenas e identidad. PostgreSQL guarda el perfil y los datos propios del sistema. El `sub` de Cognito vincula ambos. La API no debe almacenar contrasenas.

### ✅ Existe en el codigo

- Validacion JWT de firma, emisor, expiracion, `token_use=access` y `client_id`.
- Conversor de `cognito:groups` a autoridades `ROLE_<GRUPO>`.
- Endpoint protegido `GET /api/v1/me`.
- `GET /actuator/health` publico y resto de rutas autenticadas.
- Entidades y repositorios iniciales `Rol` y `Usuario`.
- Servicios y DTO iniciales de Cognito/usuarios, aun no terminados ni validados de extremo a extremo.
- Migracion inicial `src/main/resources/db/migration/V1__create_roles_and_users.sql`.
- Cliente AWS SDK que usa la cadena estandar de credenciales.

### 📍 Estado comprobado

- ✅ Se creo el proyecto Supabase `syncpass-dev` en East US (Ohio).
- ✅ La aplicacion llego a conectarse correctamente al Session pooler de Supabase.
- ✅ Se identifico el primer bloqueo de ejecucion: Hibernate no encontraba `rol`.
- ✅ Flyway V1 se ejecuto en H2 y sus tablas pasaron la validacion de Hibernate en las pruebas.
- ❌ Aun falta comprobar que Flyway aplique V1 en Supabase real y que la aplicacion arranque contra esa base.
- ❌ Cognito real, login HTTP, CRUD, app movil y EC2 todavia no estan integrados.

## 🗺️ Ruta de trabajo / tickets

Leyenda: `[x] ✅` terminado o comprobado; `[ ] ❌` pendiente. Actualizar este listado al completar cada ticket.

### Fase 1 — Base de datos y arranque

- [x] ✅ **DB-01 — Crear Supabase de desarrollo.** Proyecto creado en Ohio.
- [x] ✅ **DB-02 — Conectar Spring a Supabase.** El log confirmo `HikariPool - Start completed` y PostgreSQL 17.11.
- [x] ✅ **DB-03 — Crear migracion V1.** Define `rol`, `usuario` y los cuatro roles iniciales.
- [ ] ❌ **DB-04 — Aplicar V1 en Supabase.** Recargar Maven en IntelliJ y arrancar; confirmar en logs que Flyway aplica version 1, Hibernate valida y Spring termina con `Started SyncPassApiApplication`.
- [ ] ❌ **DB-05 — Inspeccionar el esquema remoto.** En Supabase, verificar tablas `rol`, `usuario`, `flyway_schema_history` y los cuatro roles. Confirmar que no se crearon duplicados manualmente.
- [ ] ❌ **DB-06 — Proteger credenciales.** Revisar que la contrasena de base de datos real solo este en IntelliJ local, nunca en Git, README, capturas o logs.

### Fase 2 — Cognito y seguridad del backend

- [ ] ❌ **AUTH-01 — Crear Cognito de desarrollo.** User Pool, App Client sin secreto, flujo de password auth si se mantiene login backend, y grupos exactos `ADMINISTRADOR`, `COORDINADOR`, `VOLUNTARIO`, `BENEFICIARIO`.
- [ ] ❌ **AUTH-02 — Configurar variables Cognito.** `AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_ISSUER`; validar issuer exacto del User Pool.
- [ ] ❌ **AUTH-03 — Probar JWT real.** Confirmar access token con `iss`, `sub`, `token_use=access`, `client_id` y grupos. Verificar 401 sin token/incorrecto y 403 con rol insuficiente.
- [ ] ❌ **AUTH-04 — Terminar login HTTP.** Crear controller y contrato `POST /auth/login`; dejarlo publico en Spring Security, validar entrada, responder con access token y perfil sin registrar contrasenas ni tokens.
- [ ] ❌ **AUTH-05 — Resolver perfil y autorizacion.** Definir que hacer si existe usuario Cognito sin fila local, si tiene varios grupos, si no tiene grupo y como aplicar desactivacion y permisos por recurso.
- [ ] ❌ **AUTH-06 — Endpoints de usuario.** Completar `POST /api/v1/usuarios`, consulta y `PATCH` de activo; manejar consistencia/compensacion si Cognito tiene exito y PostgreSQL falla, o viceversa.

### Fase 3 — Dominio y API

- [ ] ❌ **API-01 — Acordar contrato funcional.** Revisar el documento de contexto de la API; fijar entidades, campos, relaciones, paginacion, errores y permisos por rol/recurso antes de ampliar el esquema.
- [ ] ❌ **API-02 — Completar proyectos.** Migraciones, modelo, repositorios, servicios, controllers, DTOs, validacion y pruebas.
- [ ] ❌ **API-03 — Completar participantes y catalogos.** Aplicar el mismo flujo y restricciones por rol.
- [ ] ❌ **API-04 — Integracion y regresion.** Probar contra PostgreSQL de desarrollo y Cognito de desarrollo; cubrir casos felices, datos invalidos, conflictos, 401/403 y fallos externos.
- [ ] ❌ **API-05 — Documentar contrato.** Publicar OpenAPI/Swagger o una especificacion equivalente para que la app movil integre rutas y respuestas sin adivinar.

### Fase 4 — Aplicacion movil

- [ ] ❌ **MOBILE-01 — Identificar y revisar el proyecto movil.** Confirmar framework (Android/iOS/Flutter/React Native), repositorio, version y como se distribuyen builds de desarrollo.
- [ ] ❌ **MOBILE-02 — Acordar flujo de autenticacion.** Consumir el contrato de login definido; decidir si la app recibe el access token del backend o autentica directamente contra Cognito. No implementar ambos flujos a la vez.
- [ ] ❌ **MOBILE-03 — Configurar ambientes.** Usar URL de API distinta para desarrollo/staging/produccion. En desarrollo local desde un telefono, `localhost` apunta al telefono, no a la PC; usar IP accesible o tunel de desarrollo. No incluir secretos de servidor en la app.
- [ ] ❌ **MOBILE-04 — Manejar sesion de forma segura.** Enviar `Authorization: Bearer <access_token>`; almacenar tokens solo en almacenamiento seguro del sistema operativo; no guardar contrasenas; definir expiracion, refresh/logout y respuesta ante 401/403.
- [ ] ❌ **MOBILE-05 — Integrar pantallas y contratos.** Login, perfil, proyectos y funcionalidades acordadas; estados de carga/error/sin conexion; probar dispositivos y versiones soportadas.
- [ ] ❌ **MOBILE-06 — Validar extremo a extremo.** Probar la app contra backend de desarrollo con usuarios y grupos de prueba, sin datos personales reales.

### Fase 5 — Despliegue

- [ ] ❌ **DEPLOY-01 — Preparar EC2.** Linux con Java 21, red restringida y servicio con usuario sin privilegios.
- [ ] ❌ **DEPLOY-02 — Rol IAM.** Instance Profile con solo permisos necesarios para Cognito/lectura de secretos; sin access keys estaticas ni politicas administrativas amplias.
- [ ] ❌ **DEPLOY-03 — Secretos y red.** Inyectar configuracion con Secrets Manager o Parameter Store; configurar acceso saliente desde EC2 a Supabase y restringir accesos administrativos.
- [ ] ❌ **DEPLOY-04 — HTTPS y observabilidad.** TLS en Load Balancer/proxy, health checks, logs, alertas y manejo seguro de datos sensibles.
- [ ] ❌ **DEPLOY-05 — Publicacion y recuperacion.** Build versionado, aplicacion de migraciones revisadas, backup, despliegue gradual y procedimiento de rollback probado.

## 🧪 Verificacion inmediata para continuar

1. En IntelliJ, abrir **Maven** y pulsar **Reload All Maven Projects** o aceptar la notificacion **Load Maven Changes**.
2. Ejecutar `SyncPassApiApplication` con las variables de Supabase ya configuradas.
3. En los logs, buscar en este orden:
   - `Successfully validated 1 migration` y `Successfully applied 1 migration ... version v1` (si aun no existia Flyway history).
   - `HikariPool - Start completed`.
   - Hibernate inicializado sin `Schema validation: missing table`.
   - `Started SyncPassApiApplication`.
4. En Supabase, abrir **Table Editor** y comprobar `rol`, `usuario` y `flyway_schema_history`; en `rol`, confirmar los cuatro nombres esperados.
5. Si hay error, compartir solo el primer `ERROR` y el `Caused by` relevante. Ocultar contrasenas, tokens y cadenas completas de conexion.

La conexion JDBC confirmada para el proyecto actual usa **Session pooler**. La URL debe incluir el host/puerto/DB mostrados por Supabase y el usuario con el identificador del proyecto. Mantener usuario y contrasena en variables separadas (`DB_USER`, `DB_PASSWORD`); nunca publicar la contrasena.

## ⚙️ Configuracion local

`.env.example` es una referencia: Spring no carga archivos `.env` automaticamente. Configurar variables en IntelliJ (**Run → Edit Configurations → Environment variables**) o en la terminal:

| Variable | Proposito |
|---|---|
| `DB_URL` | URL JDBC de Supabase Session pooler, incluyendo `sslmode=require` |
| `DB_USER` | Usuario completo mostrado por Supabase, incluyendo sufijo del proyecto |
| `DB_PASSWORD` | Contrasena de PostgreSQL de Supabase |
| `AWS_REGION` | Region del User Pool |
| `COGNITO_USER_POOL_ID` | Identificador del User Pool |
| `COGNITO_CLIENT_ID` | Identificador del App Client |
| `COGNITO_ISSUER` | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` |

Para ejecutar pruebas:

```powershell
.\mvnw.cmd test
```

Las pruebas usan H2. No prueban conectividad real con Cognito ni con Supabase.

## 🛡️ Precauciones importantes

- **Migraciones:** V1 se aplica automaticamente mediante Flyway. La primera migracion esta pensada para una base nueva. Si ya se crearon tablas manualmente, detenerse y revisar antes de arrancar. Una vez aplicada en un ambiente compartido, no editar V1: agregar V2, V3, etc.
- **Entornos:** mantener Cognito y Supabase de desarrollo separados de produccion; no probar migraciones destructivas en produccion.
- **Contraseñas y claves:** no versionar `.env`, credenciales, tokens, client secrets ni contrasenas. Un App Client movil no debe tener client secret. Si una credencial real se expone, rotarla.
- **JWT:** aceptar solo access tokens del issuer/client correctos. No registrar tokens. Roles de grupos no sustituyen autorizacion de pertenencia/propiedad de cada recurso.
- **Supabase RLS:** este backend usa JDBC/JPA directo; no asumir que las politicas RLS de la Data API protegen automaticamente las consultas del servidor. Usar credenciales de base limitadas y aplicar autorizacion en la API; evaluar un rol PostgreSQL dedicado antes de produccion.
- **Cognito y base de datos:** no existe transaccion compartida entre ambos servicios. Definir reintentos, idempotencia y compensacion ante fallos parciales antes de activar altas/bajas de usuarios.
- **Datos personales:** usar datos ficticios en pruebas, minimizar lo que se registra y no exponer detalles internos por Actuator.
- **Red movil:** la app no debe conectarse directamente a Supabase ni incluir credenciales de base; solo consume la API por HTTPS en ambientes publicados.
- **Operaciones:** no ejecutar `ddl-auto=create`, `update` o scripts manuales improvisados. Conservar `validate`, revisar cada SQL y confirmar backups antes de cambios de esquema.

## 🏗️ Build

```powershell
.\mvnw.cmd clean test package
```

El JAR se genera en `target/`. El siguiente hito es cerrar **DB-04/DB-05**: confirmar migracion y arranque exitosos en Supabase real; luego avanzar a **AUTH-01**.
