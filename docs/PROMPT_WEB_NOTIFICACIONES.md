# Prompt de Integración — Notificaciones Push (Web Frontend)

> **Repositorio:** `fullstack2-proyecto-kt`
> **Contexto:** SyncPass — Panel de administración web para enviar notificaciones push a usuarios móviles.
> **Backend:** API ya implementada en `monolito_backend_syncpass` (Spring Boot 4, Java 21).

---

## 1. Contexto del Proyecto

SyncPass es una plataforma de gestión de eventos comunitarios con check-in por QR, modo offline, encuestas y reportes. El backend ya implementa un sistema de notificaciones push mediante **Firebase Cloud Messaging (FCM)** que envía mensajes a la app Android.

Tu tarea es integrar el **panel de gestión de notificaciones** en la aplicación web, permitiendo que los administradores envíen notificaciones manuales a los usuarios móviles.

---

## 2. Endpoints del Backend

El backend expone los siguientes endpoints REST. Todos requieren autenticación JWT vía AWS Cognito.

### 2.1. Enviar notificación manual

```
POST /api/notifications/manual
Authorization: Bearer <jwt_token>
Rol requerido: ADMINISTRADOR
```

**Request body:**
```json
{
  "titulo": "Recordatorio importante",
  "cuerpo": "El evento 'Campaña de limpieza' comienza mañana a las 8:00 AM.",
  "roles": ["VOLUNTARIO", "COORDINADOR"]
}
```

- `titulo`: Obligatorio. Título de la notificación.
- `cuerpo`: Obligatorio. Cuerpo del mensaje.
- `roles`: Opcional. Array de roles destino. Si es `null` o vacío, se envía a **todos** los usuarios activos.

**Roles válidos:**
- `ADMINISTRADOR`
- `COORDINADOR`
- `VOLUNTARIO`
- `BENEFICIARIO`

**Response 200 OK:**
```json
{
  "mensaje": "Notificación enviada a roles: VOLUNTARIO, COORDINADOR",
  "enviadoEn": "2025-11-13T22:00:00"
}
```

### 2.2. Historial de notificaciones

```
GET /api/notifications/historial
Authorization: Bearer <jwt_token>
Rol requerido: ADMINISTRADOR
```

**Response 200 OK:**
```json
[]
```

> ⚠️ Actualmente devuelve un array vacío. La tabla de historial se implementará en una futura iteración del backend.

---

## 3. Requerimientos Funcionales

### RF-01: Panel de envío de notificaciones
Como administrador, quiero una interfaz para escribir título, cuerpo y seleccionar roles destino, y enviar la notificación con un botón.

### RF-02: Validación de formulario
- Los campos título y cuerpo son obligatorios.
- Si no se selecciona ningún rol, la notificación se envía a todos los usuarios.

### RF-03: Feedback al usuario
- Mostrar mensaje de éxito cuando la notificación se envía correctamente.
- Mostrar mensaje de error si la petición falla (ej. sin permisos, error de red).

### RF-04: Restricción por rol
- Solo usuarios con rol `ADMINISTRADOR` pueden ver y usar el panel.
- Si un usuario sin el rol intenta acceder, redirigir o mostrar mensaje de acceso denegado.

### RF-05: Vista de historial (pendiente)
-预留 un espacio en la UI para mostrar el historial de notificaciones enviadas, aunque actualmente el backend devuelve un array vacío.

---

## 4. Requerimientos No Funcionales

- **NFR-01:** El panel debe integrarse con el sistema de autenticación existente (Cognito JWT).
- **NFR-02:** Las peticiones a la API deben incluir el token JWT en el header `Authorization: Bearer <token>`.
- **NFR-03:** La UI debe ser responsive y consistente con el diseño existente de la aplicación.

---

## 5. Estructura de Archivos Propuesta

```
src/
├── services/
│   └── notificationService.ts       # Funciones para llamar a la API
├── components/
│   └── notifications/
│       ├── NotificationPanel.tsx     # Formulario para enviar notificaciones
│       └── NotificationHistory.tsx   # Vista de historial (pendiente)
├── pages/
│   └── NotificationsPage.tsx        # Página principal de notificaciones
└── types/
    └── notification.types.ts         # Interfaces TypeScript
```

---

## 6. Código de Referencia

### 6.1. Tipos TypeScript

```typescript
// types/notification.types.ts

export interface ManualNotificationRequest {
  titulo: string;
  cuerpo: string;
  roles?: string[]; // null o vacío = todos
}

export interface NotificationResponse {
  mensaje: string;
  enviadoEn: string;
}

export const ROLES = ['ADMINISTRADOR', 'COORDINADOR', 'VOLUNTARIO', 'BENEFICIARIO'] as const;
export type Rol = (typeof ROLES)[number];
```

### 6.2. Servicio de Notificaciones

```typescript
// services/notificationService.ts

import { ManualNotificationRequest, NotificationResponse } from '../types/notification.types';

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
    if (response.status === 403) {
      throw new Error('No tienes permisos para enviar notificaciones');
    }
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

### 6.3. Componente NotificationPanel

```tsx
// components/notifications/NotificationPanel.tsx

import { useState } from 'react';
import { enviarNotificacionManual } from '../../services/notificationService';
import { ROLES, ManualNotificationRequest } from '../../types/notification.types';

export function NotificationPanel() {
  const [titulo, setTitulo] = useState('');
  const [cuerpo, setCuerpo] = useState('');
  const [rolesSeleccionados, setRolesSeleccionados] = useState<string[]>([]);
  const [mensaje, setMensaje] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnviando(true);
    setError('');
    setMensaje('');

    try {
      // Obtener JWT del contexto de autenticación existente
      const token = getToken(); // Adaptar según tu sistema de auth
      
      const request: ManualNotificationRequest = {
        titulo,
        cuerpo,
        roles: rolesSeleccionados.length > 0 ? rolesSeleccionados : undefined,
      };

      const response = await enviarNotificacionManual(request, token);
      setMensaje(response.mensaje);
      setTitulo('');
      setCuerpo('');
      setRolesSeleccionados([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al enviar notificación');
    } finally {
      setEnviando(false);
    }
  };

  const toggleRol = (rol: string) => {
    if (rolesSeleccionados.includes(rol)) {
      setRolesSeleccionados(rolesSeleccionados.filter(r => r !== rol));
    } else {
      setRolesSeleccionados([...rolesSeleccionados, rol]);
    }
  };

  return (
    <div className="notification-panel">
      <h2>Enviar Notificación Push</h2>

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="titulo">Título</label>
          <input
            id="titulo"
            value={titulo}
            onChange={e => setTitulo(e.target.value)}
            placeholder="Ej: Recordatorio de evento"
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="cuerpo">Cuerpo del mensaje</label>
          <textarea
            id="cuerpo"
            value={cuerpo}
            onChange={e => setCuerpo(e.target.value)}
            placeholder="Ej: El evento comienza en 1 hora"
            rows={4}
            required
          />
        </div>

        <fieldset>
          <legend>Roles destino (dejar vacío para enviar a todos)</legend>
          {ROLES.map(rol => (
            <label key={rol}>
              <input
                type="checkbox"
                checked={rolesSeleccionados.includes(rol)}
                onChange={() => toggleRol(rol)}
              />
              {rol}
            </label>
          ))}
        </fieldset>

        <button type="submit" disabled={enviando}>
          {enviando ? 'Enviando...' : 'Enviar notificación'}
        </button>
      </form>

      {mensaje && <div className="success-message">{mensaje}</div>}
      {error && <div className="error-message">{error}</div>}
    </div>
  );
}

// Adaptar esta función según tu sistema de autenticación
function getToken(): string {
  // Ejemplo: return useContext(AuthContext).token;
  throw new Error('Implementar obtención de token JWT');
}
```

---

## 7. Integración con Autenticación Existente

La aplicación web ya usa AWS Cognito para autenticación. Debes:

1. **Obtener el token JWT** del contexto de autenticación existente.
2. **Verificar el rol del usuario** antes de mostrar el panel. Solo `ADMINISTRADOR` debe tener acceso.
3. **Incluir el token** en todas las peticiones al backend en el header `Authorization: Bearer <token>`.

Adapta la función `getToken()` y la verificación de roles según la estructura actual de tu aplicación.

---

## 8. Notas Importantes

- El backend ya está implementado y funcionando. No necesitas modificar el backend.
- El endpoint de historial devuelve un array vacío por ahora.预留 la UI para cuando se implemente.
- Las notificaciones se envían de forma asíncrona desde el backend, por lo que la respuesta es inmediata.
- Si la petición devuelve `403`, el usuario no tiene permisos de administrador.

---

## 9. Checklist de Implementación

- [ ] Crear tipos TypeScript para notificaciones
- [ ] Crear servicio de notificaciones con las funciones API
- [ ] Crear componente `NotificationPanel` con formulario
- [ ] Crear página `NotificationsPage` con el panel
- [ ] Integrar con el sistema de autenticación existente
- [ ] Restringir acceso solo a rol `ADMINISTRADOR`
- [ ] Agregar navegación al panel desde el menú de administración
- [ ] Probar envío de notificación con diferentes combinaciones de roles
- [ ]预留 espacio para historial de notificaciones
