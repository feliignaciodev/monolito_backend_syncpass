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
| Aplicacion movil | Kotlin + Compose en `projects-duoc/Movile/app` (estructura core/ + feature/ con Hilt); solo cambia la URL base |
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
| POST | `/api/auth/registro` (`{nombre, email, password}` → 201), `/api/auth/registro/confirmar` (`{email, codigo}` → 204), `/api/auth/registro/reenviar-codigo` (`{email}` → 204) | publico |
| GET | `/api/me` | autenticado |
| GET | `/api/sync/catalogo` | autenticado |
| POST | `/api/sync/asistencias`, `/api/sync/encuestas` | autenticado (lotes de max. 500) |
| GET | `/api/eventos/{id}/metricas` | COORDINADOR, ADMINISTRADOR |
| GET | `/api/reportes/resumen`, `/api/reportes/asistencias.csv` | COORDINADOR, ADMINISTRADOR |
| GET/POST | `/api/usuarios`, `GET /api/usuarios/{id}`, `PATCH /api/usuarios/{id}/activo` | ADMINISTRADOR |

**Auto-registro (web SyncPass).** `POST /api/auth/registro` hace `SignUp` en Cognito, agrega el usuario al grupo `BENEFICIARIO` y guarda el perfil local; el rol nunca viene del cliente. Si falla la asignacion del grupo o el guardado en la base, la cuenta se elimina de Cognito. La cuenta queda sin confirmar hasta que el usuario envia el codigo que le llega al email. Requiere un User Pool que use el **email como nombre de usuario**, con el auto-registro habilitado y los 4 grupos creados.

### 📍 Estado comprobado

- ✅ Se creo el proyecto Supabase `syncpass-dev` en East US (Ohio); la aplicacion conecta al Session pooler.
- ✅ Flyway aplico V1 y V2 en Supabase; el arranque posterior valido el esquema en version 2 y Hibernate inicio correctamente.
- ✅ Se validó el inicio de sesión móvil con una cuenta creada y confirmada desde el frontend; la app pudo autenticarse contra el backend desplegado.
- ✅ `mvnw clean package`: 47 pruebas OK y JAR generado.
- ✅ La API local responde `UP` en `http://localhost:8080/api/health`.
- ✅ El 30-10-2026 se comprobó que el sitio responde por HTTPS en `https://syncpass.duckdns.org` y que `https://syncpass.duckdns.org/api/health` responde `UP`.

## 🗺️ Ruta de trabajo / tickets

Leyenda: `[x] ✅` terminado o comprobado; `[ ] ❌` pendiente; `🛠️` codigo listo, falta verificar con servicios reales.

### Fase 1 — Base de datos y arranque

- [x] ✅ **DB-01 — Crear Supabase de desarrollo.** Proyecto creado en Ohio.
- [x] ✅ **DB-02 — Conectar Spring a Supabase.** El log confirmo `HikariPool - Start completed` y PostgreSQL 17.11.
- [x] ✅ **DB-03 — Crear migraciones.** V1 (`rol`, `usuario`, 4 roles) y V2 (`username` + dominio).
- [x] ✅ **DB-04 — Aplicar V1 y V2 en Supabase.** Flyway confirmo ambas migraciones aplicadas y el esquema en version 2.
- [x] ✅ **DB-05 — Validar el esquema remoto.** Hibernate valida las entidades y la aplicacion arranca contra Supabase.
- [ ] ❌ **DB-06 — Proteger credenciales.** La contrasena real solo en IntelliJ local y en Parameter Store.

### Fase 2 — Cognito y seguridad del backend

- [ ] 🛠️ **AUTH-01 — Crear Cognito de desarrollo.** `deploy/cognito-setup.ps1` crea pool, App Client sin secreto (tokens de 12 h), grupos y usuarios demo.
- [x] ✅ **AUTH-02 — Configurar variables Cognito localmente.** Pool y App Client informados y guardados en la configuracion local de IntelliJ; confirmar que el pool existe, porque su endpoint JWKS respondio HTTP 404.
- [ ] 🛠️ **AUTH-03 — Probar JWT real.** Cubierto en pruebas con tokens simulados (401/403); falta probar con Cognito real.
- [x] ✅ **AUTH-04 — Login HTTP.** `POST /api/auth/login` publico, validado, sin registrar contrasenas ni tokens.
- [x] ✅ **AUTH-05 — Perfil y autorizacion.** Perfil local creado en el primer login; rol = grupo de mayor privilegio; sin grupo → 403; desactivado → 403.
- [x] ✅ **AUTH-06 — Endpoints de usuario.** Alta, consulta y `PATCH` de activo con compensacion Cognito ↔ PostgreSQL.

### Fase 3 — Dominio y API

- [x] ✅ **API-01 — Contrato funcional.** Se adopta el contrato de la app Android (`Movile/docs/contrato-api.md`).
- [x] ✅ **API-02 — Proyectos y eventos.** Migracion, modelo, repositorios, servicios, controllers, DTOs y pruebas.
- [x] ✅ **API-03 — Participantes, asistencias, encuestas y reportes.** Mismo flujo y restricciones por rol.
- [ ] 🛠️ **API-04 — Integracion y regresion.** Pruebas y conexion real a Supabase OK; falta validar login real con Cognito.
- [ ] ❌ **API-05 — Documentar contrato.** El contrato existe en Markdown; queda opcional publicar OpenAPI/Swagger.

### Fase 4 — Aplicacion movil

- [x] ✅ **MOBILE-01 — Identificar el proyecto movil.** Android nativo (Kotlin, Compose, Room, Retrofit) en `projects-duoc/Movile/app` (estructura core/ + feature/ con Hilt).
- [x] ✅ **MOBILE-02 — Flujo de autenticacion.** La app recibe el access token de Cognito desde `POST /api/auth/login`; no habla con Cognito directamente.
- [ ] ❌ **MOBILE-03 — Configurar ambientes.** En la app, *Configurar servidor* → `https://<dominio>/api/`.
- [x] ✅ **MOBILE-04 — Sesion segura.** Token en DataStore privado (sin backup ni logs); release solo HTTPS; 401 marca la sesion como expirada sin perder registros.
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

## 🚀 Publicar y probar desde otros dispositivos (EC2)

La ejecucion local solo es accesible desde tu PC o la misma red Wi-Fi; no es una URL publica. Para dejar la API disponible en Internet usa EC2 con HTTPS. Antes de desplegar, confirma en Cognito que el User Pool `us-east-1_AwWWrn8Ib` y el App Client `47v1h6d5fb83r3cle10gsv4hjq` existen en `us-east-1` y tienen los flujos y grupos requeridos. No ejecutes `deploy/cognito-setup.ps1` sin modificarlo para reutilizar esos recursos.

1. Inicia AWS Academy Learner Lab y configura sus credenciales temporales localmente siguiendo [pasos.md, secciones 0 y 4](pasos.md). No las pegues en GitHub ni en el chat.
2. En `us-east-1`, crea una instancia Amazon Linux 2023, asigna el perfil IAM `LabInstanceProfile`, crea/usa una llave SSH y asocia una Elastic IP. En el Security Group permite SSH solo desde tu IP y HTTP/HTTPS (80/443); **no abras 8080**.
3. Configura un dominio que apunte a la Elastic IP. Guarda las variables de conexion en Parameter Store. El script usa el prefijo `/syncpass/prod/`; confirma que el entorno y los datos sean los correctos antes de cargar secretos (la clave Supabase se pide de forma oculta):

   ```powershell
   .\deploy\parameter-store.ps1 `
     -DbUrl 'jdbc:postgresql://<HOST_POOLER>:5432/postgres?sslmode=require' `
     -DbUser 'postgres.<PROJECT_REF>' `
     -CognitoUserPoolId '<POOL>' -CognitoClientId '<CLIENT>'
   ```

4. Desde la raiz del repositorio instala y publica la API:

   ```powershell
   .\deploy\deploy.ps1 -Servidor '<ELASTIC_IP>' -Llave '<RUTA_A_LA_LLAVE.pem>' `
     -Instalar -Dominio '<DOMINIO>'
   ```

5. Habilita HTTPS con el dominio y un correo valido:

   ```powershell
   ssh -i '<RUTA_A_LA_LLAVE.pem>' ec2-user@'<ELASTIC_IP>' `
     'sudo /usr/local/sbin/syncpass-setup-https <DOMINIO> <CORREO>'
   ```

   Verifica `https://<DOMINIO>/api/health` y configura esa base URL en la app Android. Los detalles de Learner Lab, permisos IAM, red y solucion de problemas estan en [`pasos.md`](pasos.md).

La publicacion EC2 requiere recursos y credenciales AWS de tu cuenta; no se realiza automaticamente al subir el codigo a GitHub.

## ▶️ Uso diario: levantar y comprobar la aplicación

Una vez instalado, el backend se ejecuta como servicio `systemd`; no necesitas abrir una consola ni dejar tu PC encendido. Si la EC2 sigue en ejecución, la API y el sitio permanecen disponibles. El servicio está configurado para iniciar junto con Linux y reiniciarse si falla.

1. En AWS Console → **EC2 → Instances**, confirma que la instancia esté en estado **Running**. Si está detenida, selecciónala y pulsa **Instance state → Start instance**. Mientras esté detenida, la app móvil y el sitio no podrán conectarse.
2. Comprueba el estado público de la API desde PowerShell:

   ```powershell
   Invoke-RestMethod 'https://syncpass.duckdns.org/api/health'
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

## 🔎 Por verificar

El inicio de sesión móvil **sí se probó** con una cuenta creada y confirmada desde el frontend. Eso valida el flujo general de autenticación con una cuenta real, pero no todas las funciones ni todos los estados de sesión. Pendientes para una validación completa:

- [ ] En la app móvil, descargar el catálogo y completar una sincronización de asistencia y una encuesta; confirmar que los datos llegan a la API y aparecen en los reportes.
- [ ] Cerrar y volver a abrir la app, y probar qué ocurre cuando vence el access token. El backend entrega un access token y no ofrece un endpoint de renovación; confirmar que la app solicita iniciar sesión otra vez de forma clara.
- [ ] Si se permitirá el auto-registro desde la app móvil, completar allí la confirmación del código de Cognito. El backend expone los endpoints para confirmar y reenviar el código; el flujo móvil todavía debe verificarse y contar con la pantalla que los invoque.
- [ ] Decidir si el login web debe pasar por `POST /api/auth/login` o si se mantendrá el login directo con Cognito. El login directo ya funciona, pero no ejecuta las comprobaciones de perfil, rol y estado activo que aplica el backend en su propio flujo de login.
- [ ] Revisar la política de protección del token móvil: almacenamiento cifrado y exclusión de los respaldos del dispositivo, según el nivel de protección requerido.
- [ ] Probar por separado la conexión real a Supabase y Cognito. `/api/health` solo confirma que responde la API; por sí solo no comprueba esas dependencias.
- [ ] Compilar y probar la app Android en un entorno con Android SDK configurado; la compilación no se pudo verificar durante esta revisión.

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
