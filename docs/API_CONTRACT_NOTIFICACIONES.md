# API Contract — Notificaciones Push SyncPass

> Documento de integración para **Web** (`fullstack2-proyecto-kt`) y **Móvil** (`syncpass-movil`).
> Generado a partir del backend implementado en `monolito_backend_syncpass`.
>
> ⚠️ **Alcance parcial / backlog:** el registro de token y el envío manual están implementados en
> código, pero **no hay credenciales FCM de producción ni validación E2E**. El historial
> (`GET /api/notifications/historial`) **devuelve `[]`**: no está implementada la tabla de historial.
> Las notificaciones están fuera del alcance principal; ver `ALCANCE.md` §8. No rehacer notificaciones.

---

## 1. Endpoints disponibles

| Método | Ruta | Autenticación | Rol requerido | Descripción |
|--------|------|---------------|---------------|-------------|
| `POST` | `/api/notifications/token` | JWT (Cognito) | Cualquier usuario autenticado | Registra/actualiza el token FCM del dispositivo |
| `POST` | `/api/notifications/manual` | JWT (Cognito) | `ADMINISTRADOR` | Envía una notificación manual a todos o por roles |
| `GET` | `/api/notifications/historial` | JWT (Cognito) | `ADMINISTRADOR` | Devuelve el historial de notificaciones enviadas |

---

## 2. DTOs y payloads

### 2.1. `POST /api/notifications/token`

**Request body:**
```json
{
  "fcmToken": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response 200 OK:**
```json
{
  "mensaje": "Token registrado exitosamente",
  "enviadoEn": "2025-11-13T22:00:00"
}
```

**Validaciones:**
- `fcmToken` es obligatorio (`@NotBlank`).

**Notas:**
- Si el token ya existe, se reasigna al usuario actual y se marca como activo.
- Si es nuevo, se crea un registro `DispositivoPush`.

---

### 2.2. `POST /api/notifications/manual`

**Request body:**
```json
{
  "titulo": "Recordatorio importante",
  "cuerpo": "El evento 'Campaña de limpieza' comienza mañana a las 8:00 AM.",
  "roles": ["VOLUNTARIO", "COORDINADOR"]
}
```

**Response 200 OK:**
```json
{
  "mensaje": "Notificación enviada a roles: VOLUNTARIO, COORDINADOR",
  "enviadoEn": "2025-11-13T22:00:00"
}
```

**Validaciones:**
- `titulo` y `cuerpo` son obligatorios (`@NotBlank`).
- `roles` es opcional. Si es `null` o vacío, se envía a **todos** los usuarios activos.

**Roles válidos:**
- `ADMINISTRADOR`
- `COORDINADOR`
- `VOLUNTARIO`
- `BENEFICIARIO`

---

### 2.3. `GET /api/notifications/historial`

**Response 200 OK:**
```json
[]
```

> ⚠️ Actualmente devuelve un array vacío. La tabla de historial se implementará en una futura iteración.

---

## 3. Payload de FCM (Backend → Dispositivo)

El backend envía notificaciones mediante Firebase Admin SDK con esta estructura:

### 3.1. Notificación simple
```json
{
  "message": {
    "token": "<fcm_token>",
    "notification": {
      "title": "Título de la notificación",
      "body": "Cuerpo del mensaje"
    }
  }
}
```

### 3.2. Notificación con datos (recordatorios y eventos)
```json
{
  "message": {
    "token": "<fcm_token>",
    "notification": {
      "title": "Recordatorio de evento",
      "body": "El evento 'X' comienza en 1 hora"
    },
    "data": {
      "tipo": "recordatorio",
      "evento_id": "123"
    }
  }
}
```

**Valores de `tipo`:**
| Valor | Descripción |
|-------|-------------|
| `recordatorio` | Recordatorio automático (1 hora o 1 día antes del evento) |
| `inscripcion_aprobada` | La inscripción del usuario fue aprobada |
| `inscripcion_rechazada` | La inscripción del usuario fue rechazada |
| `inscripcion_pendiente` | La inscripción del usuario está pendiente |
| `manual` | Notificación enviada manualmente por un administrador |

---

## 4. Notificaciones automáticas (Scheduler)

El backend ejecuta automáticamente las siguientes tareas:

| Frecuencia | Acción |
|------------|--------|
| Cada 15 minutos | Buscar eventos que comienzan en **1 hora** y enviar recordatorio a todos los participantes inscritos |
| Cada 15 minutos | Buscar eventos que comienzan en **1 día** y enviar recordatorio a todos los participantes inscritos |

### Eventos Spring (disparadores automáticos)

| Evento | Disparador | Notificación |
|--------|-----------|--------------|
| `InscripcionStateChangedEvent` | Al cambiar el estado de una inscripción (`APROBADA`, `RECHAZADA`, `PENDIENTE`) | Se envía al participante afectado |

---

## 5. Guía de integración — Mobile (Android/Kotlin)

### 5.1. Dependencia

En `app/build.gradle.kts`:
```kotlin
implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
implementation("com.google.firebase:firebase-messaging-ktx")
```

### 5.2. Servicio de mensajería

```kotlin
class SyncPassMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Enviar token al backend
        registerToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Mostrar notificación en el sistema
        remoteMessage.notification?.let { notification ->
            showNotification(notification.title, notification.body)
        }

        // Manejar datos adicionales
        remoteMessage.data.isNotEmpty().let {
            val tipo = remoteMessage.data["tipo"]
            val eventoId = remoteMessage.data["evento_id"]
            handleDataMessage(tipo, eventoId)
        }
    }

    private fun registerToken(token: String) {
        // POST /api/notifications/token
        // Body: { "fcmToken": "$token" }
        // Header: Authorization: Bearer <jwt_token>
    }

    private fun showNotification(title: String?, body: String?) {
        // Usar NotificationManager para mostrar la notificación
    }

    private fun handleDataMessage(tipo: String?, eventoId: String?) {
        // Navegar a la pantalla correspondiente según el tipo
    }
}
```

### 5.3. Manifest

```xml
<service
    android:name=".SyncPassMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### 5.4. Flujo de registro

1. Al iniciar sesión, obtener el token FCM actual:
   ```kotlin
   FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
       if (task.isSuccessful) {
           val token = task.result
           // Enviar al backend después de obtener el JWT
       }
   }
   ```
2. Llamar a `POST /api/notifications/token` con el JWT del usuario autenticado.

---

## 6. Guía de integración — Web (React/TypeScript)

### 6.1. Servicio de notificaciones

```typescript
interface ManualNotificationRequest {
  titulo: string;
  cuerpo: string;
  roles?: string[]; // null o vacío = todos
}

interface NotificationResponse {
  mensaje: string;
  enviadoEn: string;
}

const API_BASE = '/api/notifications';

export async function enviarNotificacionManual(
  request: ManualNotificationRequest,
  token: string
): Promise<NotificationResponse> {
  const response = await fetch(`${API_BASE}/manual`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error('Error al enviar notificación');
  }

  return response.json();
}

export async function getHistorial(token: string): Promise<any[]> {
  const response = await fetch(`${API_BASE}/historial`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('Error al obtener historial');
  }

  return response.json();
}
```

### 6.2. Componente de panel (React ejemplo)

```tsx
import { useState } from 'react';
import { enviarNotificacionManual } from './notificationService';

const ROLES = ['ADMINISTRADOR', 'COORDINADOR', 'VOLUNTARIO', 'BENEFICIARIO'];

export function NotificationPanel() {
  const [titulo, setTitulo] = useState('');
  const [cuerpo, setCuerpo] = useState('');
  const [rolesSeleccionados, setRolesSeleccionados] = useState<string[]>([]);
  const [mensaje, setMensaje] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const token = getToken(); // Obtener JWT del contexto de autenticación
      const response = await enviarNotificacionManual(
        { titulo, cuerpo, roles: rolesSeleccionados.length ? rolesSeleccionados : undefined },
        token
      );
      setMensaje(response.mensaje);
    } catch (error) {
      setMensaje('Error al enviar notificación');
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input value={titulo} onChange={e => setTitulo(e.target.value)} placeholder="Título" required />
      <textarea value={cuerpo} onChange={e => setCuerpo(e.target.value)} placeholder="Cuerpo" required />
      
      <fieldset>
        <legend>Roles destino (vacío = todos)</legend>
        {ROLES.map(rol => (
          <label key={rol}>
            <input
              type="checkbox"
              checked={rolesSeleccionados.includes(rol)}
              onChange={e => {
                if (e.target.checked) {
                  setRolesSeleccionados([...rolesSeleccionados, rol]);
                } else {
                  setRolesSeleccionados(rolesSeleccionados.filter(r => r !== rol));
                }
              }}
            />
            {rol}
          </label>
        ))}
      </fieldset>

      <button type="submit">Enviar notificación</button>
      {mensaje && <p>{mensaje}</p>}
    </form>
  );
}
```

---

## 7. Seguridad

- Todos los endpoints requieren autenticación JWT vía Cognito.
- `/api/notifications/manual` y `/api/notifications/historial` requieren rol `ADMINISTRADOR`.
- Los tokens FCM se almacenan en la tabla `dispositivo_push` con relación a `usuario`.
- Los tokens inválidos se marcan automáticamente como inactivos.

---

## 8. Configuración del backend

Para producción, configurar en `application.properties`:

```properties
fcm.projectId=tu-proyecto-firebase
fcm.credentialsPath=/ruta/a/credenciales.json
```

El archivo `credenciales.json` se obtiene desde Firebase Console → Project Settings → Service Accounts → Generate new private key.

En modo desarrollo sin credenciales, las notificaciones se registran en logs pero no se envían.
