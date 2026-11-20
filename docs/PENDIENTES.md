# SyncPass — Lo que falta para cerrar la EVA3

> Estado al **19-11-2026 (noche)**. Complementa [`BASTIAN_LEEME.md`](./BASTIAN_LEEME.md).
> Lo que se comprobó ese día está marcado ✅. Lo que no se pudo comprobar dice **sin verificar**.

## 0. Dónde estamos

| Parte | Estado |
|---|---|
| Código, `/docs` y pruebas (web, backend, móvil) | ✅ Completo frente a la rúbrica |
| Ramas | ✅ `main` = `desarrollo` en los 3 repos: web `f4cbece`, backend `cc5f2b4`, móvil `1e7d8ba`. **Antes de seguir trabajando: `git pull` en `main`.** |
| Web en Vercel | ✅ <https://fullstack2-proyecto-kt.vercel.app>. Publica `main` automáticamente. `vercel.json` reenvía `/api/*` a `https://syncpass.duckdns.org/api/*`: la web y la API comparten origen, así que no hace falta CORS ni `VITE_API_BASE_URL`. |
| EC2 | ⚠️ Encendido: nginx y el certificado Let's Encrypt responden y `syncpass.duckdns.org` apunta a la instancia. **Pero la API devuelve `502 Bad Gateway`: Spring Boot no está corriendo detrás de nginx.** |
| Supabase | ❓ Sin verificar. Sospecha: proyecto pausado (el plan gratis se pausa tras 7 días sin uso). |

Pruebas: web 84 pruebas / 65,4 % de líneas · móvil 84 / 50,0 % (JaCoCo, medido el 19-11) · backend 69 / 72,59 %.

---

## 1. 🔴 Levantar la API en el EC2 (bloquea todo lo demás)

**Responsable:** quien administra Supabase + quien tiene acceso al EC2.

- [ ] **Supabase:** entrar a supabase.com → proyecto. Si aparece *Paused*, usar **Restore project**. Comprobar que `/syncpass/prod/DB_URL`, `DB_USER` y `DB_PASSWORD` en Parameter Store siguen siendo los del *Session Pooler* del proyecto.
- [ ] Esperar 1–2 minutos y probar:
  ```bash
  curl https://syncpass.duckdns.org/api/health
  ```
  Si el servicio está instalado como en `deploy/server/syncpass.service`, se reinicia solo cada 10 s (`Restart=on-failure`).
- [ ] Si sigue en 502, ver el error real. Desde la consola de AWS: EC2 → instancia → **Connect → EC2 Instance Connect**.
  ```bash
  sudo systemctl status syncpass --no-pager
  sudo journalctl -u syncpass -n 80 --no-pager
  # si el servicio tiene otro nombre:
  systemctl list-units --type=service | grep -i -E "sync|java"
  ```

  | Mensaje en el log | Causa probable | Arreglo |
  |---|---|---|
  | `Falta el parametro /syncpass/prod/...` | Parameter Store incompleto o el rol de la instancia no puede leerlo | `deploy/parameter-store.ps1`; revisar el rol IAM (`deploy/iam-policy.json`) |
  | `Connection refused`, timeout o `FlywayException` hacia Supabase | Proyecto pausado, o URL/contraseña cambiadas | Restaurar el proyecto o actualizar `DB_URL` / `DB_USER` / `DB_PASSWORD` |
  | `OutOfMemoryError` o el proceso muere sin error | Poca memoria en la instancia | Revisar el swap |
- [ ] Verificar las migraciones en Supabase (SQL Editor):
  ```sql
  select version, description, success from flyway_schema_history order by installed_rank;
  ```
  Deben aparecer V1, V2 y V3 con `success = true`.

## 2. 🔴 Confirmar que el EC2 corre la versión de `main`

- [ ] Con la API arriba: `curl https://syncpass.duckdns.org/api/cursos/recientes` debe responder **200** con JSON. Si da **404**, el `.jar` del servidor es anterior a `cc5f2b4` y la portada de la web ("Últimos cursos") va a fallar → redesplegar.
- [ ] ⚠️ **Antes de redesplegar, confirmar cómo está montado el servidor.** nginx responde como **Ubuntu**, pero `deploy/server/setup-server.sh` está escrito para **Amazon Linux** (`dnf`, `java-21-amazon-corretto`) y `deploy.ps1` usa `ec2-user` por defecto. O sea, el EC2 actual no se instaló con esos scripts tal cual. Quien lo montó debe confirmar el usuario SSH (en Ubuntu es `ubuntu`), la carpeta (`/opt/syncpass`?) y el nombre del servicio. Después hay que alinear `deploy/` y `pasos.md` con lo real, porque la rúbrica evalúa la coherencia entre los docs y la implementación.
- [ ] Redesplegar desde `main`. El script compila con pruebas, sube el jar, guarda el anterior para `-Rollback` y reinicia:
  ```powershell
  git checkout main; git pull
  .\deploy\deploy.ps1 -Servidor <IP pública del EC2> -Llave <ruta .pem> -Usuario ubuntu
  ```
  Requiere las credenciales vigentes del Learner Lab en `~/.aws/credentials`.
- [ ] Datos semilla: `syncpass.datos-semilla=${SYNCPASS_DATOS_SEMILLA:true}`. `BASTIAN_LEEME.md` recomienda `false` en producción. Si para la demo se necesitan los datos de ejemplo, dejarlo en `true` y anotarlo. Para cambiarlo: crear `/syncpass/prod/SYNCPASS_DATOS_SEMILLA` en Parameter Store (`start.sh` exporta todo lo que hay bajo `/syncpass/prod/`) y reiniciar el servicio.

## 3. 🟡 Cognito: comprobar el auto-registro

- [ ] En la consola de Cognito, User pool `us-east-1_AwWWrn8Ib` → *Sign-up*, comprobar que **Self-registration** está habilitado. Si no, el registro desde la web y la app (`POST /api/auth/registro`, que usa `SignUp`) falla.
- [ ] ⚠️ **No volver a correr `deploy/cognito-setup.ps1` sobre el pool existente sin revisarlo.** `cc5f2b4` le agregó `aws cognito-idp update-user-pool --admin-create-user-config ...`, y `UpdateUserPool` **restablece a su valor por defecto todo atributo que no se envía** (política de contraseñas, verificación de email, etc.). Es más seguro activar el auto-registro desde la consola. *Pendiente: corregir el script para que envíe la configuración completa o use otra vía.*

## 4. 🟡 Evidencia de punta a punta (para la entrega)

Con la API arriba, guardar capturas o un video corto:

- [ ] **Web (Vercel):** portada con "Últimos cursos" · registro + código de confirmación · login · `/cursos` (crear y editar como admin) · `/notifications`.
- [ ] **Móvil:** login → modo avión → *Registrar asistencia offline* → quitar el modo avión → **Sincronizar pendientes** (Perfil) → ver la fila en Supabase (tabla de asistencias) y en `GET /api/eventos/{id}/metricas`.
- [ ] **Supabase:** captura de las tablas con datos.

> ⚠️ **Red de la fundación:** su firewall (FortiGate) bloquea `*.duckdns.org` (categoría *Dynamic DNS*). Desde esa red la **web** funciona igual, porque el navegador solo habla con `vercel.app` y Vercel reenvía `/api`. La **app móvil** apunta directo a `syncpass.duckdns.org`, así que hay que probarla con **datos móviles** u otra red.

## 5. 🟡 Defensa individual (40 % de la nota)

Se responden 2 de 3 preguntas sobre el código. Archivos clave por tema:

| Pregunta probable | Dónde está la respuesta |
|---|---|
| ¿Cómo funciona la sincronización offline? | Móvil: `SyncRepository`, `SyncViewModel`, `SyncPassDao` (pendientes) y `SyncPassApplication` (reintento al volver la red con `NetworkCallback`). Backend: `SyncService` (lotes de hasta 500, idempotencia por `uuid`) |
| ¿Cómo autenticas? | Móvil: `AuthRepository`, `TokenInterceptor`, `TokenAuthenticator`. Web: `src/auth/cognito.ts` (Amplify, USER_SRP_AUTH). Backend: Resource Server que valida el JWT con el JWKS de Cognito; grupos → roles |
| ¿Dónde está MVVM? | `feature/*/ui/*Screen` → `*ViewModel` → repositorios → `core/datasource/{local,remote}` → Room / Retrofit / DataStore. Ningún ViewModel importa un DAO, una API ni `Context` |
| ¿Qué pasa si sincronizas dos veces? | El backend detecta el `uuid` repetido y lo informa como duplicado; la app marca aceptados y duplicados como sincronizados, así que no se reenvían |
| ¿Cómo se mapean los roles de la rúbrica? | `ADMINISTRADOR` → Administrador, `COORDINADOR` → Docente, `VOLUNTARIO` → Voluntario, `BENEFICIARIO` → Estudiante (enum `Rol` en el móvil; tabla en `ALCANCE.md`) |

## 6. ⚪ Backlog conocido (no lo exige la rúbrica, pero puede salir en la defensa)

**Móvil**
- Botones "Demo Profesor / Demo Alumno" con `password123` en `LoginScreen` / `LoginViewModel`.
- `fallbackToDestructiveMigration()` en `AppDatabase`: subir la versión de Room borra las asistencias pendientes.
- La captura por QR del docente (`SessionScreen`, modelo local de clases) no se sincroniza. El registro que sí se sincroniza es el diálogo *Registrar asistencia offline*, que no usa QR.
- El botón *Sincronizar* está solo en Perfil y no se deshabilita sin red. No hay GPS.

**Backend** (detalle en `BASTIAN_LEEME.md` §4)
- Notificaciones completas (historial, duplicados, credenciales FCM).
- Renovación del token (`/api/auth/refresh`).
- Roles literales `ESTUDIANTE` / `DOCENTE`, si el docente los exige.
- El catálogo trae eventos de cursos finalizados.
- `cognito-setup.ps1`: ver §3.
