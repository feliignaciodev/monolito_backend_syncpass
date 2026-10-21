# SyncPass API

Backend monolitico modular con Java 21, Spring Boot 4.1.1, PostgreSQL/Supabase y Cognito.

## Estado del esqueleto

- Seguridad stateless como OAuth2 Resource Server: valida firma, emisor, expiracion, `token_use=access` y `client_id`.
- `cognito:groups` se convierte a autoridades `ROLE_<GRUPO>`.
- `GET /api/v1/me` devuelve el `sub` y los roles del token autenticado.
- `GET /actuator/health` queda publico; el resto de rutas requiere autenticacion.
- El cliente AWS usa la cadena estandar de credenciales del SDK, compatible con un rol IAM en EC2.
- Todavia no se implementaron login, CRUD ni entidades/migraciones de negocio.

La organizacion prevista es por funcionalidad: `auth`, `usuarios`, `proyectos`, `participantes` y `catalogos`. Cada modulo alojara sus controladores, servicios y persistencia cuando definamos el esquema y las reglas del dominio.

## Configuracion local

Usa `.env.example` como referencia y configura las variables de entorno en tu terminal o IDE. Spring no carga automaticamente archivos `.env`.

| Variable | Uso |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL, por ejemplo `jdbc:postgresql://localhost:5432/syncpass` |
| `DB_USER` / `DB_PASSWORD` | Credenciales de PostgreSQL |
| `COGNITO_ISSUER` | Emisor del User Pool: `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` |
| `COGNITO_CLIENT_ID` | ID del App Client sin secreto |
| `AWS_REGION` | Region del User Pool |

Hibernate esta configurado en `validate`: no crea ni modifica tablas. La base debe existir y tener el esquema correspondiente antes de iniciar la aplicacion. No guardes secretos en el repositorio.

En PowerShell, con las variables configuradas:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Las pruebas usan una base H2 temporal; el entorno real usa PostgreSQL.
