# 🚀 SyncPass API — Paso a paso hasta producción en EC2

Guía para dejar el backend funcionando en AWS EC2 con HTTPS, Cognito y Supabase, y conectar la app Android. Sigue los pasos **en orden** y marca cada casilla al terminar. El código y los scripts ya están listos (ver la bitácora en [`docs/syncpass.html`](docs/syncpass.html)); estos pasos requieren tu cuenta y tus credenciales.

| Script | Para qué | Dónde se ejecuta |
|---|---|---|
| `deploy/cognito-setup.ps1` | Crea User Pool, App Client, grupos y usuarios demo | Tu PC |
| `deploy/parameter-store.ps1` | Guarda los 6 secretos en Parameter Store | Tu PC |
| `deploy/iam-policy.json` | Permisos mínimos del rol de la instancia | Consola IAM |
| `deploy/deploy.ps1` | Compila, prueba, sube y reinicia (y `-Instalar` la primera vez, `-Rollback`) | Tu PC |
| `deploy/server/setup-server.sh` | Java 21, nginx, usuario de servicio, swap, systemd | EC2 (lo lanza `deploy.ps1`) |
| `deploy/server/setup-https.sh` | Certificado Let's Encrypt y renovación | EC2 |

---

## 📋 Valores que vas a necesitar

**No subas este listado con valores reales a Git.**

| Placeholder | Qué es | Dónde se obtiene |
|---|---|---|
| `<REGION>` | Región AWS: `us-east-1` (N. Virginia; AWS Academy solo permite `us-east-1` y `us-west-2`). Supabase sigue en Ohio, no importa | — |
| `<ACCOUNT_ID>` | ID de 12 dígitos de tu cuenta AWS | Esquina superior derecha de la consola |
| `<POOL>` / `<CLIENT>` | User Pool ID y App Client ID | Los imprime `cognito-setup.ps1` |
| `<HOST_POOLER>` / `<PROJECT_REF>` | Host del Session pooler e id del proyecto Supabase | Supabase → Connect → Session pooler |
| `<ELASTIC_IP>` | IP pública fija de la instancia | EC2 → Elastic IPs |
| `<DOMINIO>` | Dominio de la API, ej. `syncpass-dev.duckdns.org` | duckdns.org |
| `<LLAVE>` | Ruta al `.pem` de la instancia, ej. `C:\claves\syncpass-key.pem` | Paso 4 |

> ⚠️ **AWS Academy Learner Lab (nuestro caso):** no puedes crear roles IAM (usa `LabInstanceProfile` y salta el Paso 4.2); las credenciales de tu PC caducan al cerrar el lab (repite el Paso 0.2 en cada sesión); al cerrar el lab la instancia EC2 se detiene y al volver a iniciarlo arranca sola junto con la API (servicio systemd). La Elastic IP es obligatoria para que la IP no cambie.

---

## Paso 0 — Herramientas en tu PC

### 0.1 Instalar AWS CLI v2 (una sola vez)

- [ ] `winget install Amazon.AWSCLI`; cierra y abre PowerShell (e IntelliJ) y comprueba con `aws --version` → `aws-cli/2...`

### 0.2 Credenciales del Learner Lab (en cada sesión del lab)

- [ ] En AWS Academy: **Modules → Launch AWS Academy Learner Lab → Start Lab** y espera el punto verde junto a "AWS".
- [ ] **AWS Details → AWS CLI: Show** y copia el bloque completo (`[default]`, `aws_access_key_id`, `aws_secret_access_key`, `aws_session_token`).
- [ ] Pégalo reemplazando todo el contenido de `notepad $HOME\.aws\credentials` (créalo con `mkdir $HOME\.aws -Force` si no existe) y guarda.
- [ ] Región por defecto (una sola vez): `aws configure set region us-east-1`
- [ ] Comprueba: `aws sts get-caller-identity` → muestra `Account` y un `Arn` con `voclabs`.

> No uses `aws configure` para las claves: no pide el `aws_session_token` y sin él todo responde `InvalidClientTokenId`. Si aparece `ExpiredToken`, el lab se cerró: inícialo y vuelve a copiar las credenciales.

En Academy no hay Billing ni Budgets: el saldo del lab aparece arriba en la página del Learner Lab. Java no hace falta instalarlo: los scripts usan el JDK de Android Studio.

Java no hace falta instalarlo: los scripts usan el JDK de Android Studio.

---

## Paso 1 — Cognito (AUTH-01, AUTH-02)

Desde la carpeta del proyecto:

```powershell
.\deploy\cognito-setup.ps1 -PasswordAdmin 'UnaClaveSegura2026'
```

El script crea, si no existen:
- User Pool `syncpass-dev`: inicio de sesión por usuario o email, contraseña mínima de 8 caracteres y alta solo por administrador.
- App Client `syncpass-app`: sin secreto, `USER_PASSWORD_AUTH`, access token de **12 horas**.
- Grupos `ADMINISTRADOR`, `COORDINADOR`, `VOLUNTARIO`, `BENEFICIARIO`.
- Usuarios demo **ficticios**: `coordinador / coord123`, `voluntario / volun123`, `admin / <tu clave>`.

- [ ] Anota los 4 valores que imprime al final (`AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_ISSUER`).

---

## Paso 2 — Arranque local contra Supabase y Cognito (DB-04, DB-05, AUTH-03)

En IntelliJ: *Run → Edit Configurations → SyncPassApiApplication → Environment variables*:

```
DB_URL=jdbc:postgresql://<HOST_POOLER>:5432/postgres?sslmode=require
DB_USER=postgres.<PROJECT_REF>
DB_PASSWORD=<tu contraseña>
AWS_REGION=us-east-1
COGNITO_USER_POOL_ID=<POOL>
COGNITO_CLIENT_ID=<CLIENT>
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/<POOL>
```

- [ ] Arranca y confirma en el log: `Successfully applied 2 migrations` (o `...version v2`), `Datos semilla FICTICIOS cargados` y `Started SyncPassApiApplication`.
- [ ] En Supabase → Table Editor existen `rol`, `usuario`, `proyecto`, `evento`, `participante`, `inscripcion`, `asistencia`, `encuesta` y `flyway_schema_history`.
- [ ] Prueba el login y el catálogo en PowerShell:

```powershell
$r = Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login -ContentType 'application/json' `
     -Body '{"username":"coordinador","password":"coord123"}'
$r.usuario                                   # id, username, nombre, rol = COORDINADOR
$h = @{ Authorization = "Bearer $($r.token)" }
(Invoke-RestMethod http://localhost:8080/api/sync/catalogo -Headers $h).eventos.Count   # 6
Invoke-RestMethod http://localhost:8080/api/reportes/resumen -Headers $h               # 3 proyectos
curl.exe -i http://localhost:8080/api/sync/catalogo                                   # 401 {"error":...}
```

- [ ] Con `voluntario / volun123`, `GET /api/reportes/resumen` responde **403**.

> En local, la primera vez que un usuario inicia sesión la API lee su nombre en Cognito con las credenciales de `$HOME\.aws\credentials` (Paso 0.2). Si caducaron, usa el username como nombre (no falla). Reinicia la app en IntelliJ después de pegar credenciales nuevas.

---

## Paso 3 — Secretos en Parameter Store (DEPLOY-03)

```powershell
.\deploy\parameter-store.ps1 `
   -DbUrl 'jdbc:postgresql://<HOST_POOLER>:5432/postgres?sslmode=require' `
   -DbUser 'postgres.<PROJECT_REF>' `
   -CognitoUserPoolId '<POOL>' -CognitoClientId '<CLIENT>'
```

- [ ] Te pide la contraseña de Supabase de forma oculta y guarda 6 parámetros `SecureString` en `/syncpass/prod/`.

> Usa el **Session pooler**, no la *Direct connection* (es solo IPv6 y EC2 usa IPv4).

---

## Paso 4 — Rol IAM e instancia EC2 (DEPLOY-01, DEPLOY-02)

### 4.1 Lanzar la instancia

EC2 → **Launch instance** en `us-east-1` (N. Virginia, arriba a la derecha de la consola):

- [ ] Nombre `syncpass-api`, AMI **Amazon Linux 2023**, tipo **t3.small** (o `t3.micro`; el script agrega swap).
- [ ] Key pair `syncpass-key` (`.pem`), guardado **fuera del repositorio**.
- [ ] **AWS Academy:** en *Advanced details → IAM instance profile* elige **`LabInstanceProfile`**. Con eso la API lee Parameter Store y administra Cognito; no necesitas el Paso 4.2.
- [ ] Security group `syncpass-sg`: SSH 22 solo **My IP**; HTTP 80 y HTTPS 443 desde `0.0.0.0/0`. ❌ No abras el 8080.
- [ ] EC2 → Elastic IPs → **Allocate** y **Associate** a la instancia. Anota `<ELASTIC_IP>`.

### 4.2 Rol IAM (solo cuentas AWS normales; en AWS Academy sáltalo)

- [ ] IAM → Roles → Create role → **AWS service → EC2** → nombre `syncpass-ec2-role`.
- [ ] Add permissions → Create inline policy → JSON: pega `deploy/iam-policy.json` reemplazando `<ACCOUNT_ID>` y `<POOL>`.
- [ ] EC2 → instancia → Actions → Security → **Modify IAM role** → `syncpass-ec2-role` (o `LabInstanceProfile`).

> Si Windows reclama permisos del `.pem`: `icacls <LLAVE> /inheritance:r /grant:r "$($env:USERNAME):R"`

---

## Paso 5 — Dominio

- [ ] En **duckdns.org** crea un subdominio (ej. `syncpass-dev`) y apúntalo a `<ELASTIC_IP>`.
- [ ] `nslookup <DOMINIO>` devuelve `<ELASTIC_IP>`.

---

## Paso 6 — Instalar el servidor y desplegar (DEPLOY-01, DEPLOY-05)

Un solo comando desde tu PC:

```powershell
.\deploy\deploy.ps1 -Servidor <ELASTIC_IP> -Llave <LLAVE> -Instalar -Dominio <DOMINIO>
```

Hace, en orden:
1. Compila y ejecuta las 33 pruebas. Si alguna falla, no despliega.
2. Sube `deploy/server/` y ejecuta `setup-server.sh`: Java 21, nginx, usuario `syncpass`, swap, servicio systemd y verificación de Parameter Store.
3. Sube el JAR, reinicia el servicio y espera a que `/api/health` responda.

- [ ] El script termina con `Despliegue OK`.
- [ ] Si falla al arrancar, muestra las últimas líneas del log. Con `AccessDenied`, revisa el rol IAM del Paso 4.2.

---

## Paso 7 — HTTPS (DEPLOY-04)

Android bloquea HTTP sin cifrar en producción, así que la app necesita HTTPS:

```powershell
ssh -i <LLAVE> ec2-user@<ELASTIC_IP> 'sudo /usr/local/sbin/syncpass-setup-https <DOMINIO> tu-correo@ejemplo.cl'
```

- [ ] `https://<DOMINIO>/api/health` abre en el navegador con candado.

---

## Paso 8 — Verificación final

```powershell
$base = 'https://<DOMINIO>/api'
Invoke-RestMethod "$base/health"                                                           # status UP
$r = Invoke-RestMethod -Method Post "$base/auth/login" -ContentType 'application/json' `
     -Body '{"username":"voluntario","password":"volun123"}'
$h = @{ Authorization = "Bearer $($r.token)" }
(Invoke-RestMethod "$base/sync/catalogo" -Headers $h).proyectos.Count                      # 3
curl.exe -i "$base/reportes/resumen" -H "Authorization: Bearer $($r.token)"                # 403
curl.exe -m 5 http://<ELASTIC_IP>:8080/api/health                                          # debe FALLAR
```

- [ ] Las respuestas son las esperadas.
- [ ] Supabase → Settings → Database → **Network Restrictions**: permite solo `<ELASTIC_IP>` y tu IP.
- [ ] Ningún secreto en Git (DB-06).

---

## Paso 9 — Conectar la app Android (MOBILE-03 a MOBILE-06)

- [ ] En la app, pantalla **Configurar servidor**: `https://<DOMINIO>/api/`
- [ ] Inicia sesión con `voluntario / volun123`: descarga 3 proyectos y 6 eventos.
- [ ] Registra asistencias (QR de `Movile/prototipo/credenciales-qr.html`), una encuesta y pulsa **Sincronizar ahora**.
- [ ] Con `coordinador / coord123`, revisa métricas y resumen.
- [ ] Antes de entregar, en `Movile/android/app/src/main/res/xml/network_security_config.xml` deja `cleartextTrafficPermitted="false"`, porque la API ya usa HTTPS.

> Para desarrollo local sin EC2: emulador → `http://10.0.2.2:8080/api/`; teléfono en la misma Wi-Fi → `http://<IP-del-PC>:8080/api/`.

---

## Paso 10 — Nuevas versiones y rollback (DEPLOY-05)

```powershell
.\deploy\deploy.ps1 -Servidor <ELASTIC_IP> -Llave <LLAVE>             # nueva versión (guarda la anterior)
.\deploy\deploy.ps1 -Servidor <ELASTIC_IP> -Llave <LLAVE> -Rollback   # volver a la anterior
```

> ⚠️ **Migraciones:** Flyway aplica V3, V4... al arrancar. **Antes** de desplegar una migración nueva, respalda la base (`pg_dump`). El rollback del JAR **no deshace** una migración aplicada.

Logs en el servidor: `ssh -i <LLAVE> ec2-user@<ELASTIC_IP> 'sudo journalctl -u syncpass -n 100 --no-pager'`

---

## 🛠️ Solución de problemas

| Síntoma | Causa probable | Qué revisar |
|---|---|---|
| `cognito-setup.ps1`: `AccessDenied` | Credenciales sin permisos de Cognito, o región distinta de `us-east-1`/`us-west-2` en Academy | `aws sts get-caller-identity`; `aws configure get region` |
| `ExpiredToken` / `InvalidClientTokenId` | Lab cerrado o falta `aws_session_token` | Paso 0.2: iniciar el lab y volver a copiar las credenciales |
| Login: 401 con la clave correcta | App Client sin `ALLOW_USER_PASSWORD_AUTH` o `COGNITO_CLIENT_ID` de otro client | Cognito → App clients → Authentication flows |
| Login: 403 "no tiene un rol asignado" | El usuario no está en ningún grupo | Cognito → Users → Add to group |
| Login: 502 "Servicio de autenticación no disponible" | Región o client id incorrectos, o sin salida a internet | `AWS_REGION`, `COGNITO_CLIENT_ID`; log de la app |
| Token válido pero 401 | Issuer distinto o se usó el IdToken | `COGNITO_ISSUER` exacto; la API entrega el AccessToken |
| `Schema validation: missing table` | Flyway no corrió o la base es otra | `DB_URL`; tabla `flyway_schema_history` |
| Timeout a Supabase | Direct connection (IPv6) o Network Restrictions | Session pooler; permitir `<ELASTIC_IP>` |
| `setup-server.sh`: faltan parámetros | Paso 3 incompleto o rol IAM ausente | `aws ssm get-parameters-by-path --path /syncpass/prod` |
| `502 Bad Gateway` en nginx | La app no está corriendo | `sudo systemctl status syncpass`; `journalctl -u syncpass` |
| certbot falla | El DNS no apunta a la IP o el 80 está cerrado | `nslookup <DOMINIO>`; security group |
| La app cierra sesión a cada rato | Access token de 1 h (App Client creado a mano) | App client → Token expiration → Access token 12 h |

> Al compartir errores, muestra solo el primer `ERROR` y su `Caused by`. **Oculta** contraseñas, tokens y cadenas de conexión completas.

---

## ✅ Resumen de tickets

| Paso | Tickets |
|---|---|
| 1 | AUTH-01, AUTH-02 |
| 2 | DB-04, DB-05, AUTH-03 |
| 3 | DEPLOY-03, DB-06 |
| 4, 6 | DEPLOY-01, DEPLOY-02 |
| 7, 8 | DEPLOY-04 |
| 9 | MOBILE-03 a MOBILE-06 |
| 10 | DEPLOY-05 |
