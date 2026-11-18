# Rúbrica de Evaluación – Prueba 3: Desarrollo de Aplicación Móvil Nativa & Cloud

Esta evaluación integra el desarrollo de una aplicación móvil nativa en Android (Kotlin) con arquitectura limpia, persistencia offline, servicios backend en la nube, documentación arquitectónica (C4) y pruebas unitarias.

---

## 📊 Estructura General de Calificación

* **60% – Proyecto Técnico y Documentación (Grupal / Entregable de Código y Docs)**
* **60%** Implementación Técnica (Backend, Cloud y App Android)
* **40%** Ingeniería, Documentación (Uso de IA controlado), Diseño y Calidad (Testing)


* **40% – Defensa y Evaluación Individual**
* **100%** Interrogación oral / técnica: responder correctamente **2 de 3 preguntas** sobre el código, la arquitectura o las decisiones técnicas del proyecto.



---

## 🛠️ Parte 1: Proyecto Técnico y Documentación (60% de la nota final)

### 1.1 Implementación Técnica (60% del proyecto)

#### A. Infraestructura Cloud y Backend

* **Base de Datos:** Proyecto configurado y conectado en **Supabase**.
* **API REST:** Desarrollada en **Spring Boot** y desplegada en una instancia **AWS EC2**.
* **API Gateway:** *(Opcional)* Enrutamiento y gestión de peticiones hacia la API.
* **Autenticación y Roles (Se sugiere AWS Cognito):** Gestión de identidad y control de acceso para los siguientes perfiles:
* Estudiante
* Docente
* Voluntario
* Administrador *(Opcional)*



#### B. Aplicación Móvil Android (Kotlin)

* **Lenguaje:** Desarrollada íntegramente en **Kotlin**.
* **Integración de Hardware / APIs Nativas:** Utilizar al menos **2 APIs de Android** (ej. Geolocalización, Cámara, File Storage).
* **Arquitectura en Capas (MVVM):** Separación estricta de responsabilidades:
* **Model:** `Datasource`, `Repository`, `Remote` (Retrofit/API) y `Local` (Room/SQLite/DataStore).
* **View:** `Components` y `Screens` (Jetpack Compose / XML).
* **ViewModel:** Lógica de negocio y dominio; interactúa exclusivamente con el `Repository` y expone estados hacia la `Screen`.


* **Persistencia Local y Sincronización (Offline-First):**
* Capacidad de operar sin conexión a internet (ej. crear registros o eventos en el almacenamiento local del dispositivo).
* Mecanismo explícito de sincronización (ej. botón de sincronizar) que envíe los datos locales pendientes hacia el servicio HTTP remoto una vez recuperada la conexión.



---

### 1.2 Ingeniería, Documentación, Diseño y Testing (40% del proyecto)

#### A. Uso Estratégico de IA y Levantamiento de Información (`/docs`)

La demostración del buen uso de Inteligencia Artificial se evalúa a través de la **calidad, coherencia y precisión de la documentación Markdown**, evidenciando que el estudiante realizó un levantamiento riguroso del caso, evitó alucinaciones y comprende profundamente su solución.

El repositorio debe incluir un directorio `/docs` con los siguientes archivos:

1. **`ARQUITECTURA.md`** (Modelado mediante Diagramas C4):
* **Nivel C1 (Contexto):** Explicación clara de la interacción del sistema con usuarios y servicios externos.
* **Nivel C2 (Contenedores):** Explicación de la comunicación y flujo de datos entre servicios (`App Móvil -> API Gateway -> Cognito -> EC2 (Spring Boot) -> Supabase`).
* **Nivel C3 (Componentes):** Arquitectura interna del sistema enfocada en las capas de la aplicación móvil (MVVM).


2. **`ALCANCE.md`** (Levantamiento Funcional):
* Requerimientos Funcionales (RF).
* Requerimientos No Funcionales (RNF).
* Casos de uso detallados.
* Flujos principales de la aplicación.



#### B. Diseño UI/UX (Mockups)

* Presentación de **todas las vistas** de la aplicación.
* Trazabilidad visual: Explicar claramente **qué vistas/funcionalidades están implementadas** en el entregable actual y **qué queda pendiente** en el backlog.

#### C. Calidad de Software (Testing)

* **Unit Tests:** Pruebas unitarias implementadas (enfocadas en lógica de negocio / ViewModels / Repositorios).
* **Cobertura de Código (Code Coverage):** Mínimo **30% de cobertura** verificable mediante reporte de herramientas de testing (ej. JaCoCo).

---

## 🎤 Parte 2: Evaluación Individual (40% de la nota final)

* **Modalidad:** Defensa individual frente al docente.
* **Criterio de aprobación:** Responder de forma correcta y fundamentada a **2 de 3 preguntas** técnicas.
* **Foco:** Autoría del código, funcionamiento de la sincronización offline, flujo de autenticación, arquitectura MVVM y coherencia entre la documentación (`/docs`) y la implementación real.

---

## 📋 Resumen Rápido de Ponderaciones

| Ítem Evaluado | Ponderación Interna | Peso en Nota Final |
| --- | --- | --- |
| **Backend, Cloud & App Android (MVVM + Offline)** | 60% *(del 60%)* | **36%** |
| **Docs C4/Alcance (Uso IA), Mockups & 30% Coverage** | 40% *(del 60%)* | **24%** |
| **Defensa Individual (2 de 3 preguntas)** | 100% *(del 40%)* | **40%** |
| **TOTAL** | — | **100%** |

---