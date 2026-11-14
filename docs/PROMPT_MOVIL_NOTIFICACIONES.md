# Prompt de Integración — Notificaciones Push (App Móvil Android)

> **Repositorio:** `syncpass-movil`
> **Contexto:** SyncPass — App Android para gestión de eventos comunitarios con notificaciones push FCM.
> **Backend:** API ya implementada en `monolito_backend_syncpass` (Spring Boot 4, Java 21).

---

## 1. Contexto del Proyecto

SyncPass es una plataforma de gestión de eventos comunitarios con check-in por QR, modo offline, encuestas y reportes. La app Android permite a los usuarios (participantes, voluntarios, coordinadores y administradores) gestionar inscripciones y asistencias a eventos.

Tu tarea es integrar **Firebase Cloud Messaging (FCM)** en la app Android para recibir notificaciones push del backend. Las notificaciones incluyen recordatorios de eventos, cambios de estado de inscripciones y mensajes manuales de los administradores.

---

## 2. Endpoints del Backend

El backend expone un endpoint para registrar el token FCM del dispositivo.

### 2.1. Registrar token FCM

```
POST /api/notifications/token
Authorization: Bearer <jwt_token>
Autenticación: Cualquier usuario autenticado (Cognito JWT)
```

**Request body:**
```json
{
  "fcmToken": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

- `fcmToken`: Obligatorio. Token FCM del dispositivo.

**Response 200 OK:**
```json
{
  "mensaje": "Token registrado exitosamente",
  "enviadoEn": "2025-11-13T22:00:00"
}
```

**Notas:**
- Si el token ya existe en otro usuario, se reasigna al usuario actual y se marca como activo.
- Si es un token nuevo, se crea un registro `DispositivoPush` asociado al usuario.

---

## 3. Tipos de Notificaciones Recibidas

El backend envía notificaciones con datos adicionales que indican el tipo y permiten navegar a la pantalla correspondiente.

### 3.1. Estructura del payload FCM

```json
{
  "notification": {
    "title": "Recordatorio de evento",
    "body": "El evento 'Campaña de limpieza' comienza en 1 hora"
  },
  "data": {
    "tipo": "recordatorio",
    "evento_id": "123"
  }
}
```

### 3.2. Valores de `tipo`

| Valor | Descripción | Acción sugerida |
|-------|-------------|-----------------|
| `recordatorio` | Recordatorio automático (1 hora o 1 día antes del evento) | Navegar a detalles del evento |
| `inscripcion_aprobada` | La inscripción del usuario fue aprobada | Navegar a detalles de la inscripción |
| `inscripcion_rechazada` | La inscripción del usuario fue rechazada | Navegar a lista de inscripciones |
| `inscripcion_pendiente` | La inscripción del usuario está pendiente | Navegar a detalles de la inscripción |
| `manual` | Notificación enviada manualmente por un administrador | Mostrar mensaje genérico |

---

## 4. Requerimientos Funcionales

### RF-01: Integración con Firebase Messaging
Agregar la dependencia de Firebase Messaging y configurar el proyecto con Firebase.

### RF-02: Servicio de mensajería
Crear un `FirebaseMessagingService` que maneje:
- `onNewToken()`: Cuando cambia el token FCM, registrarlo en el backend.
- `onMessageReceived()`: Cuando llega una notificación, mostrarla y manejar los datos.

### RF-03: Registro de token al iniciar sesión
Después de autenticarse exitosamente, obtener el token FCM y enviarlo al backend.

### RF-04: Mostrar notificaciones
Mostrar las notificaciones en el sistema de notificaciones de Android con el título y cuerpo del mensaje.

### RF-05: Navegación al tocar notificación
Al tocar una notificación con `evento_id`, navegar a la pantalla de detalles del evento correspondiente.

### RF-06: Manejo de notificaciones en segundo plano
Las notificaciones deben mostrarse correctamente incluso cuando la app está en segundo plano o cerrada.

---

## 5. Requerimientos No Funcionales

- **NFR-01:** El registro del token no debe bloquear el flujo de inicio de sesión.
- **NFR-02:** Si el registro del token falla (sin conexión, error de red), registrar el error en logs y reintentar la próxima vez que se inicie la app.
- **NFR-03:** Las notificaciones deben usar el canal de notificaciones adecuado (Android 8.0+).

---

## 6. Estructura de Archivos Propuesta

```
app/src/main/java/com/syncpass/app/
├── service/
│   └── SyncPassMessagingService.kt        # Servicio FCM
├── data/
│   └── repository/
│       └── NotificationRepository.kt      # Registro de token FCM
└── di/
    └── NotificationModule.kt              # Inyección de dependencias (si usa Hilt)
```

---

## 7. Código de Referencia

### 7.1. Configuración de Firebase

En el `build.gradle.kts` del proyecto (nivel app):

```kotlin
plugins {
    id('com.android.application')
    id('org.jetbrains.kotlin.android')
    id('com.google.gms.google-services') // Agregar este plugin
}

dependencies {
    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    
    // Firebase Messaging
    implementation("com.google.firebase:firebase-messaging-ktx")
}
```

En el `build.gradle.kts` del proyecto (nivel raíz):

```kotlin
plugins {
    id('com.google.gms.google-services') version '4.4.2' apply false
}
```

Además, descargar el archivo `google-services.json` desde **Firebase Console → Project Settings → Your apps → Download google-services.json** y colocarlo en `app/google-services.json`.

### 7.2. SyncPassMessagingService

```kotlin
// service/SyncPassMessagingService.kt

package com.syncpass.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.syncpass.app.MainActivity
import com.syncpass.app.data.repository.NotificationRepository
import com.syncpass.app.util.AuthManager
import kotlinx.coroutines.*

class SyncPassMessagingService : FirebaseMessagingService() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val notificationChannelId = "syncpass_notifications"

    // Inyectar o crear instancias según tu arquitectura
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var authManager: AuthManager

    override fun onCreate() {
        super.onCreate()
        // Inicializar dependencias
        // notificationRepository = ...
        // authManager = ...
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Registrar el nuevo token en el backend
        coroutineScope.launch {
            registerTokenInBackground(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Mostrar notificación en el sistema
        remoteMessage.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "SyncPass",
                body = notification.body ?: "",
                data = remoteMessage.data
            )
        }

        // Si solo es data message (sin notification), manejarlo también
        if (remoteMessage.notification == null && remoteMessage.data.isNotEmpty()) {
            val tipo = remoteMessage.data["tipo"]
            val titulo = remoteMessage.data["title"] ?: "SyncPass"
            val cuerpo = remoteMessage.data["body"] ?: ""
            showNotification(title = titulo, body = cuerpo, data = remoteMessage.data)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Notificaciones SyncPass",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de eventos y actualizaciones"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Pasar datos para navegación
            putExtra("NOTIFICATION_TYPE", data["tipo"])
            putExtra("EVENTO_ID", data["evento_id"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.drawable.ic_notification) // Adaptar a tu icono
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private suspend fun registerTokenInBackground(token: String) {
        try {
            val jwtToken = authManager.getJwtToken()
            if (jwtToken != null) {
                withContext(Dispatchers.IO) {
                    notificationRepository.registerFcmToken(token, jwtToken)
                }
            }
        } catch (e: Exception) {
            // Registrar error en logs, no bloquear
            android.util.Log.e("SyncPassFCM", "Error al registrar token", e)
        }
    }
}
```

### 7.3. NotificationRepository

```kotlin
// data/repository/NotificationRepository.kt

package com.syncpass.app.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NotificationRepository(
    private val baseUrl: String = "https://tu-backend-url.com" // Adaptar a tu URL
) {

    private val client = OkHttpClient()

    suspend fun registerFcmToken(fcmToken: String, jwtToken: String) {
        val json = """{"fcmToken": "$fcmToken"}"""
        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/notifications/token")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $jwtToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Error al registrar token: ${response.code}")
            }
        }
    }
}
```

### 7.4. Registro en AndroidManifest.xml

```xml
<!-- En app/src/main/AndroidManifest.xml -->

<application
    ...>

    <!-- Servicio de mensajería FCM -->
    <service
        android:name=".service.SyncPassMessagingService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>

</application>
```

### 7.5. Registro de token al iniciar sesión

```kotlin
// En tu ViewModel o flujo de login, después de autenticación exitosa

import com.google.firebase.messaging.FirebaseMessaging

fun registerFcmTokenAfterLogin(jwtToken: String) {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val fcmToken = task.result
            // Enviar al backend
            coroutineScope.launch {
                try {
                    notificationRepository.registerFcmToken(fcmToken, jwtToken)
                } catch (e: Exception) {
                    android.util.Log.e("SyncPassLogin", "Error al registrar FCM token", e)
                }
            }
        }
    }
}
```

### 7.6. Manejo de navegación en MainActivity

```kotlin
// En tu MainActivity, manejar los datos de la notificación

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Verificar si la app se abrió desde una notificación
    intent.extras?.let { extras ->
        val notificationType = extras.getString("NOTIFICATION_TYPE")
        val eventoId = extras.getString("EVENTO_ID")
        
        if (notificationType != null && eventoId != null) {
            navigateToEvent(eventoId, notificationType)
        }
    }
}

private fun navigateToEvent(eventoId: String, tipo: String) {
    // Navegar a la pantalla de detalles del evento
    // Adaptar según tu sistema de navegación (Navigation Component, etc.)
}
```

---

## 8. Configuración de Firebase

### 8.1. Pasos para configurar Firebase

1. Ir a [Firebase Console](https://console.firebase.google.com/).
2. Crear un nuevo proyecto o seleccionar uno existente.
3. Agregar una app Android:
   - Package name: `com.syncpass.app` (adaptar al package name real de tu app).
   - SHA-1: Obtener con `keytool -list -v -keystore ~/.android/debug.keystore` o desde el keystore de producción.
4. Descargar `google-services.json` y colocarlo en `app/google-services.json`.
5. En Firebase Console → Cloud Messaging → Copiar el **Server Key** para configurarlo en el backend.

### 8.2. Configuración del backend

En el backend (`application.properties`), configurar:

```properties
fcm.projectId=tu-proyecto-firebase
fcm.credentialsPath=/ruta/a/credenciales.json
```

El archivo `credenciales.json` se obtiene desde **Firebase Console → Project Settings → Service Accounts → Generate new private key**.

---

## 9. Notas Importantes

- El backend ya está implementado y funcionando. No necesitas modificar el backend.
- Las notificaciones se envían automáticamente desde el backend:
  - **Recordatorios:** 1 hora y 1 día antes de cada evento.
  - **Cambios de inscripción:** Cuando el estado de una inscripción cambia (aprobada, rechazada, pendiente).
  - **Manuales:** Enviadas por administradores desde la web.
- El token FCM puede cambiar en cualquier momento. `onNewToken()` se llama automáticamente cuando esto ocurre.
- Asegúrate de que el `google-services.json` esté correctamente configurado con el mismo proyecto de Firebase que usa el backend.

---

## 10. Checklist de Implementación

- [ ] Agregar plugin `google-services` y dependencia Firebase Messaging
- [ ] Descargar y configurar `google-services.json`
- [ ] Crear `SyncPassMessagingService` que extienda `FirebaseMessagingService`
- [ ] Implementar `onNewToken()` para registrar token en el backend
- [ ] Implementar `onMessageReceived()` para mostrar notificaciones
- [ ] Crear canal de notificaciones (Android 8.0+)
- [ ] Registrar el servicio en `AndroidManifest.xml`
- [ ] Crear `NotificationRepository` para llamar al endpoint de token
- [ ] Integrar registro de token después del login exitoso
- [ ] Manejar navegación al tocar una notificación con `evento_id`
- [ ] Probar notificaciones en diferentes estados (foreground, background, killed)
- [ ] Configurar credenciales FCM en el backend para producción
