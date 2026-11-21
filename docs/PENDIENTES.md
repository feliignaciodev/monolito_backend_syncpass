# SyncPass — Lo que falta para cerrar la EVA3

> Estado al **20-11-2026**. Complementa [`BASTIAN_LEEME.md`](./BASTIAN_LEEME.md).  
> Lo que se comprobó ese día está marcado ✅. Lo que no se pudo comprobar dice **sin verificar**.

## 0. Dónde estamos

| Parte | Estado |
|---|---|
| Código, `/docs` y pruebas (web, backend, móvil) | ✅ Completo frente a la rúbrica |
| Ramas | ✅ `main` = `develop` en los 3 repos |
| Web en Vercel | ✅ https://fullstack2-proyecto-kt.vercel.app — publica `main` automáticamente. `vercel.json` reenvía `/api/*` a `https://syncpass.duckdns.org/api/*` |
| EC2 | ✅ **VERIFICADO.** Spring Boot corriendo (Java 21, Ubuntu 26.04). Health check OK. Nginx + HTTPS (Certbot) funcionando. API responde en `https://syncpass.duckdns.org/api/*` |
| Supabase | ✅ **VERIFICADO.** Conexión OK (Session Pooler, región us-east-2). Flyway: V1, V2, V3 aplicadas. Login real y sync E2E comprobados |

Pruebas: web 84 / 65.4 % líneas — móvil 84 / 50.0 % (JaCoCo) — backend 69 / 72.59 %.

---

## 1. Verificación real de punta a punta (COMPLETADA ✅)

### 1.1 Supabase
- ✅ Conexión JDBC exitosa (`aws-0-us-east-2.pooler.supabase.com:5432`, IPv4).
- ✅ Flyway: V1 (roles/users), V2 (dominio), V3 (notificaciones) aplicadas con `success=true`.
- ✅ Base de datos activa (no pausada).

### 1.2 EC2
- ✅ Spring Boot corriendo (Java 21, Ubuntu 26.04, systemd).
- ✅ JAR actualizado (última versión con CORS).
- ✅ Nginx reverse proxy + HTTPS (Certbot) funcionando.
- ✅ Health check: `{"status":"UP"}`.

### 1.3 Cognito
- ✅ Login real exitoso (usuario `prueba@syncpass.cl`, rol BENEFICIARIO).
- ✅ Login como COORDINADOR (`docente@syncpass.cl`) exitoso.
- ✅ Token JWT válido con grupos `cognito:groups`.

### 1.4 Sync E2E
- ✅ Catálogo: 3 proyectos, 6 eventos, 42 participantes, 116 inscripciones.
- ✅ Asistencia: aceptada en primer envío.
- ✅ Idempotencia: duplicado al repetir el mismo UUID.
- ✅ Métricas: endpoint requiere COORDINADOR (403 para BENEFICIARIO, correcto).
- ✅ Reportes: 3 proyectos con datos reales.

### 1.5 Front (Vercel)
- ✅ Desplegado: https://fullstack2-proyecto-kt.vercel.app
- ✅ `/api/health` vía Vercel rewrite → EC2: `{"status":"UP"}` (HTTP 200).
- ✅ CORS: `Access-Control-Allow-Origin: https://fullstack2-proyecto-kt.vercel.app`.

---

## 2. Lo que queda pendiente (no bloquea la nota)

### Media prioridad
1. **Notificaciones** — historial ahora persiste y devuelve registros reales. Scheduler corregido (dedup por evento+tipo, ventanas no solapadas). FCM sigue sin credenciales provisionadas (log warning + retorna false).
2. **Refresh de token** — no hay endpoint `/api/auth/refresh`.
3. **Roles literales `ESTUDIANTE`/`DOCENTE`** — si el docente los exige (cambio amplio).
4. **Unificar sync con `SessionScreen`/`ClaseEntity`** — hoy el registro offline usa el catálogo remoto (Perfil), no las clases locales.

### Baja prioridad
5. **Datos semilla** — `SYNCPASS_DATOS_SEMILLA=true` por defecto; desactivar en producción (ya configurado en Parameter Store).
6. **Cursos finalizados vs catálogo** — `CatalogoService` filtra proyectos finalizados pero carga todos los eventos/inscripciones.
7. **Script Cognito setup** — `cognito-setup.ps1` crea el pool con alta solo por admin (incompatible con autoregistro).
8. **Renombrar paquete** `com.backendapp.demo` (cosmético).

### Preparación
9. **Defensa individual** (40 % de la nota) — ensayar 2 de 3 preguntas.

---

## 3. Usuarios de prueba creados

| Email | Rol | Password |
|---|---|---|
| `prueba@syncpass.cl` | BENEFICIARIO (Estudiante) | `SyncPass2026!` |
| `docente@syncpass.cl` | COORDINADOR (Docente) | `SyncPass2026!` |

---

## 4. Resumen de verificación real

| Componente | Estado | Evidencia |
|---|---|---|
| Supabase | ✅ Verificado | Conexión JDBC OK, Flyway V1/V2/V3 aplicadas |
| EC2 (Spring Boot) | ✅ Verificado | Health UP, Java 21, Ubuntu 26.04 |
| Nginx + HTTPS | ✅ Verificado | Certbot, reverse proxy funcionando |
| Cognito (login) | ✅ Verificado | Token JWT válido, roles correctos |
| Sync E2E | ✅ Verificado | Catálogo + asistencia aceptada + idempotencia |
| Front (Vercel) | ✅ Verificado | HTTP 200, /api rewrite → EC2 funciona |
| CORS | ✅ Verificado | `Access-Control-Allow-Origin: https://fullstack2-proyecto-kt.vercel.app` |

---

## 5. Archivos clave por tema (para defensa)

| Pregunta probable | Dónde está la respuesta |
|---|---|
| ¿Cómo funciona la sincronización offline? | Móvil: `SyncRepository`, `SyncViewModel`, `SyncPassDao` (pendientes) y `SyncPassApplication` (reintento al volver la red con `NetworkCallback`). Backend: `SyncService` (lotes de hasta 500, idempotencia por `uuid`) |
| ¿Cómo autenticas? | Móvil: `AuthRepository`, `TokenInterceptor`, `TokenAuthenticator`. Web: `src/auth/cognito.ts` (Amplify, USER_SRP_AUTH). Backend: Resource Server que valida el JWT con el JWKS de Cognito; grupos → roles |
| ¿Dónde está MVVM? | `feature/*/ui/*Screen` → `*ViewModel` → repositorios → `core/datasource/{local,remote}` → Room / Retrofit / DataStore. Ningún ViewModel importa un DAO, una API ni `Context` |
| ¿Qué pasa si sincronizas dos veces? | El backend detecta el `uuid` repetido y lo informa como duplicado; la app marca aceptados y duplicados como sincronizados, así que no se reenvían |
| ¿Cómo se mapean los roles de la rúbrica? | `ADMINISTRADOR` → Administrador, `COORDINADOR` → Docente, `VOLUNTARIO` → Voluntario, `BENEFICIARIO` → Estudiante (enum `Rol` en el móvil; tabla en `ALCANCE.md`) |
