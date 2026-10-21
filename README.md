# SyncPass API

Backend monolitico modular para SyncPass. La aplicacion se despliega como un solo servicio Spring Boot; el codigo se organiza por modulos funcionales para mantener limites claros sin introducir microservicios.

## Resumen del proyecto

| Area | Decision |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| API | REST bajo `/api/v1` |
| Identidad | Amazon Cognito User Pool |
| Autorizacion | Spring Security, JWT de Cognito y grupos como roles |
| Datos | PostgreSQL administrado por Supabase |
| Despliegue previsto | EC2 con rol IAM y configuracion fuera del repositorio |

La API no debe guardar contrasenas. Cognito administra las credenciales y emite los tokens; PostgreSQL guardara los perfiles y datos propios del dominio. El `sub` del token sera la identidad externa que vincule al usuario de Cognito con el perfil local.

### Que existe hoy

- Validacion de firma, emisor, expiracion, `token_use=access` y `client_id` de los JWT.
- Conversor de `cognito:groups` a autoridades `ROLE_<GRUPO>`.
- `GET /api/v1/me`, protegido, devuelve el `sub` y roles del token.
- `GET /actuator/health`, publico; el resto de las rutas exige autenticacion.
- Cliente AWS SDK preparado para usar la cadena estandar de credenciales y el rol IAM de EC2.
- Pruebas con H2 para carga del contexto, salud, acceso anonimo y conversion de roles.

Todavia no existen login propio, entidades/repositorios del dominio, CRUD, tablas, migraciones ni despliegue. Hibernate usa `ddl-auto=validate`: no crea ni modifica el esquema.

## Ruta de trabajo

1. **Acordar el dominio y el contrato:** revisar el documento de contexto completo; definir entidades, campos, relaciones, reglas de propiedad, permisos por rol, estados de usuario y comportamiento de cuentas sin perfil local.
2. **Preparar ambientes:** crear Cognito de desarrollo y Supabase; cargar sus valores como variables locales. Mantener separados desarrollo, pruebas y produccion.
3. **Definir y versionar el esquema:** escribir migraciones SQL revisables para tablas, claves, restricciones e indices. Aplicarlas primero en una base de desarrollo; no depender de cambios automaticos de Hibernate.
4. **Implementar persistencia:** entidades, repositorios y servicios por modulo; validar contra PostgreSQL real, ademas de H2 cuando sea util.
5. **Cerrar seguridad y perfil local:** probar 401/403; definir acceso por rol y por recurso; implementar la asociacion segura de `sub` con el perfil local y la politica de usuarios desactivados.
6. **Construir funcionalidades:** en orden, login Cognito (`POST /auth/login`), administracion de usuarios, proyectos, participantes y catalogos; incluir validacion, errores consistentes y pruebas.
7. **Probar integracion y fallos:** verificar tokens reales, grupos, usuario inexistente, errores de Cognito o PostgreSQL y consistencia cuando una operacion externa falla parcialmente.
8. **Desplegar en EC2:** crear rol IAM, red y almacenamiento de secretos; ejecutar el JAR como servicio; agregar HTTPS, health check, logs, monitoreo y procedimiento de actualizacion/rollback.

No se debe migrar ni alterar la base existente hasta acordar el esquema y revisar los datos actuales.

## Configurar Amazon Cognito

En la consola de AWS, en la region elegida:

1. Crear un **User Pool**. Definir como se identifican los usuarios (por ejemplo, email) y si la verificacion de email sera obligatoria.
2. Crear un **App client sin client secret** para la aplicacion. Habilitar `ALLOW_USER_PASSWORD_AUTH` solo si se implementara el login del backend con `InitiateAuth`; no usar un secreto de cliente en una app que no puede protegerlo.
3. Crear estos grupos, con los nombres exactos que espera la API:
   - `ADMINISTRADOR`
   - `COORDINADOR`
   - `VOLUNTARIO`
   - `BENEFICIARIO`
4. Para pruebas, crear usuarios ficticios y asignar cada uno al grupo que corresponda. No usar cuentas reales ni contrasenas compartidas.
5. Guardar el **Region**, **User Pool ID** y **App client ID**. No guardar contrasenas ni tokens en Git.
6. Confirmar que un access token emitido tenga `iss`, `sub`, `token_use=access`, `client_id` y, para los usuarios de prueba, `cognito:groups`.

Configurar estas variables para la API:

| Variable | Valor |
|---|---|
| `AWS_REGION` | Region del User Pool, por ejemplo `us-east-1` |
| `COGNITO_USER_POOL_ID` | ID del User Pool; se usara en operaciones administrativas |
| `COGNITO_CLIENT_ID` | ID del App client sin secreto |
| `COGNITO_ISSUER` | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` |

La validacion JWT usa las claves publicas del User Pool y no necesita claves AWS. Las credenciales IAM se necesitaran cuando el backend invoque operaciones administrativas del SDK. En EC2 se deben entregar mediante un rol IAM asociado a la instancia, nunca mediante access keys guardadas en archivos.

## Configurar Supabase / PostgreSQL

1. Crear un proyecto Supabase y elegir una region cercana a la EC2 prevista.
2. Desde **Connect**, obtener los datos de PostgreSQL. Para una EC2 de larga ejecucion, preferir una conexion persistente directa o el **Session pooler**. Usar el Transaction pooler solo despues de validar compatibilidad y limites con JDBC/JPA.
3. Habilitar SSL y restringir el acceso de red si la configuracion del proyecto lo permite. La EC2 debe poder salir hacia el host/puerto de PostgreSQL; si se restringen redes de entrada en Supabase, autorizar la IP publica de salida estable de la EC2/NAT.
4. Crear/usar un usuario de base de datos con permisos minimos para la aplicacion. Mantener las credenciales administrativas fuera del runtime de la API.
5. Configurar:

| Variable | Valor |
|---|---|
| `DB_URL` | JDBC, por ejemplo `jdbc:postgresql://<host>:5432/postgres?sslmode=require` (usar host y puerto exactos de Supabase) |
| `DB_USER` | Usuario PostgreSQL indicado por Supabase |
| `DB_PASSWORD` | Contrasena de ese usuario, solo como secreto de entorno |

El proyecto aun no incluye migraciones ni tablas de negocio. Cuando se incorporen entidades, aplicar las migraciones antes de iniciar el backend: `ddl-auto=validate` comprueba que el esquema coincida, pero no lo crea ni lo actualiza. No ejecutar cambios de esquema directamente desde Hibernate en produccion.

## Configuracion local

`.env.example` es una referencia, no se carga automaticamente por Spring ni por Maven. Configura las variables en el IDE o en la terminal. No agregues un `.env` real al repositorio.

Ejemplo de sesion PowerShell (sustituir los valores localmente):

```powershell
$env:DB_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:DB_USER = "<usuario>"
$env:DB_PASSWORD = "<contrasena>"
$env:AWS_REGION = "<region>"
$env:COGNITO_USER_POOL_ID = "<user-pool-id>"
$env:COGNITO_CLIENT_ID = "<app-client-id>"
$env:COGNITO_ISSUER = "https://cognito-idp.<region>.amazonaws.com/<user-pool-id>"

.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Para llamadas AWS desde el equipo local, usar un perfil AWS configurado en la cadena estandar del SDK. No copiar credenciales personales en el repositorio.

## Despliegue previsto en EC2

### Red y acceso

- Usar una instancia Linux compatible con Java 21 y desplegar el JAR construido con Maven.
- Asignar un **Instance Profile / rol IAM** a la EC2. Dar solo permisos necesarios para las operaciones de Cognito que implemente el backend, limitados al User Pool correcto. No adjuntar permisos administrativos generales.
- Restringir SSH a IPs administrativas o preferir AWS Systems Manager Session Manager.
- No exponer PostgreSQL a toda Internet. Limitar el acceso a la salida estable de EC2/NAT cuando Supabase permita restricciones de red.
- Para produccion, terminar HTTPS en un Application Load Balancer o proxy inverso con certificado TLS. No publicar directamente el puerto de la aplicacion como endpoint HTTP abierto.

### Configuracion y operacion

- Inyectar `DB_URL`, `DB_USER`, `DB_PASSWORD`, `AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID` y `COGNITO_ISSUER` desde AWS Systems Manager Parameter Store o Secrets Manager. Para secretos, preferir Secrets Manager o parametros `SecureString`.
- Conceder al rol de EC2 solo lectura de los parametros/secretos necesarios. Evitar claves AWS estaticas, secretos en el JAR, en la AMI o en Git.
- Ejecutar la app con un usuario de sistema sin privilegios y como servicio (por ejemplo, systemd). Configurar reinicio controlado, logs y limites de recursos.
- Configurar el health check del balanceador hacia `/actuator/health`. El endpoint es publico y solo debe exponer estado general, nunca detalles sensibles.
- Centralizar logs y alertas (por ejemplo, CloudWatch), monitorizar disponibilidad, errores, uso de memoria y conexiones a PostgreSQL.
- Automatizar despliegue, respaldo/verificacion de migraciones y rollback. Probar primero en desarrollo/staging.

Build del artefacto:

```powershell
.\mvnw.cmd clean test package
```

El JAR se genera en `target/`. Los detalles de servicio, red y secretos se completaran cuando se conozcan la region, el sistema operativo/arquitectura de EC2 y el metodo de despliegue elegido.
