# Prompt de Integración — CRUD de Cursos (Panel Web)

> **Repositorio:** `fullstack2-proyecto-kt`
> **Contexto:** SyncPass — Panel web para gestión de eventos comunitarios con CRUD de cursos.
> **Backend:** API ya implementada en `monolito_backend_syncpass` (Spring Boot 4, Java 21).

---

## 1. Contexto del Proyecto

SyncPass es una plataforma de gestión de eventos comunitarios con check-in por QR, modo offline, encuestas y reportes. El panel web permite a los administradores y coordinadores gestionar proyectos, eventos, participantes y reportes.

El backend ha agregado un **CRUD completo de cursos** (sinónimo de "Proyecto" en el dominio). Tu tarea es crear un **panel de gestión de cursos** en la web que permita a los administradores consultar, crear, editar y desactivar cursos.

---

## 2. ¿Qué es un "Curso" en SyncPass?

En el backend, **"Curso" es sinónimo de "Proyecto"**. No es una nueva entidad: reutiliza la entidad `Proyecto` existente con los siguientes campos:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | `number` | Identificador único |
| `titulo` | `string` | Título del curso |
| `descripcion` | `string` | Descripción detallada |
| `areaTematica` | `string` | Área temática (ej. "Educación", "Salud") |
| `unidadVecinal` | `string` | Unidad vecinal asociada |
| `fechaInicio` | `string (yyyy-MM-dd)` | Fecha de inicio |
| `fechaTermino` | `string (yyyy-MM-dd)` | Fecha de término |
| `estado` | `string` | `PLANIFICADO`, `EN_CURSO` o `FINALIZADO` |

**Importante:** Los cursos con estado `FINALIZADO` están desactivados y no aparecen en el catálogo de la app móvil.

---

## 3. Endpoints del Backend

| Método | Ruta | Autenticación | Rol | Descripción |
|--------|------|---------------|-----|-------------|
| `GET` | `/api/cursos` | JWT | Cualquier usuario | Lista cursos activos (excluye `FINALIZADO`) |
| `GET` | `/api/cursos/{id}` | JWT | Cualquier usuario | Detalle de un curso |
| `POST` | `/api/cursos` | JWT | `ADMINISTRADOR` | Crear un nuevo curso |
| `PUT` | `/api/cursos/{id}` | JWT | `ADMINISTRADOR` | Editar un curso |
| `PATCH` | `/api/cursos/{id}/desactivar` | JWT | `ADMINISTRADOR` | Desactivar un curso |

### 3.1. CursoResponse (lectura)

```json
{
  "id": 1,
  "titulo": "Alfabetización Digital Adulto Mayor",
  "descripcion": "Enseñar el uso de smartphones y apps básicas",
  "areaTematica": "Educación",
  "unidadVecinal": "UV 01",
  "fechaInicio": "2026-01-15",
  "fechaTermino": "2026-06-30",
  "estado": "EN_CURSO"
}
```

### 3.2. CursoCreateRequest (crear)

```json
{
  "titulo": "Curso de prueba",
  "descripcion": "Descripción del curso",
  "areaTematica": "Educación",
  "unidadVecinal": "UV 01",
  "fechaInicio": "2026-01-01",
  "fechaTermino": "2026-06-30",
  "estado": "PLANIFICADO"
}
```

**Validaciones del servidor:**
- `titulo`: obligatorio, máximo 150 caracteres.
- `areaTematica`: obligatorio, máximo 60 caracteres.
- `unidadVecinal`: obligatorio, máximo 100 caracteres.
- `fechaInicio`: obligatorio.
- `fechaTermino`: obligatorio.
- `estado`: obligatorio, debe ser `PLANIFICADO`, `EN_CURSO` o `FINALIZADO`.
- `descripcion`: opcional, máximo 1000 caracteres.

### 3.3. CursoUpdateRequest (editar)

```json
{
  "titulo": "Nuevo título",
  "estado": "EN_CURSO"
}
```

Todos los campos son opcionales. Solo se actualizan los campos enviados.

### 3.4. Errores comunes

| Código HTTP | Situación |
|-------------|-----------|
| `400 Bad Request` | Campos obligatorios vacíos o estado inválido |
| `403 Forbidden` | Rol no autorizado (no `ADMINISTRADOR` para POST/PUT/PATCH) |
| `404 Not Found` | Curso no encontrado |
| `409 Conflict` | Intentar desactivar un curso ya desactivado |

**Formato de error:**
```json
{
  "error": "El título es obligatorio",
  "status": 400
}
```

---

## 4. Requerimientos Funcionales

### RF-01: Lista de cursos
Mostrar una tabla con todos los cursos activos. Debe incluir: título, área temática, unidad vecinal, fechas, estado y acciones (editar, desactivar).

### RF-02: Crear curso
Formulario para crear un nuevo curso con validación de campos obligatorios. Solo visible para `ADMINISTRADOR`.

### RF-03: Editar curso
Formulario de edición con los campos pre-llenados. Solo visible para `ADMINISTRADOR`.

### RF-04: Desactivar curso
Botón para desactivar un curso con confirmación. Cambia el estado a `FINALIZADO` y oculta el curso del catálogo de la app móvil. Solo visible para `ADMINISTRADOR`.

### RF-05: Ver detalle de curso
Vista de detalle con toda la información del curso.

### RF-06: Manejo de errores
Mostrar mensajes claros al usuario ante errores de validación (`400`), permisos (`403`) o curso no encontrado (`404`).

---

## 5. Requerimientos No Funcionales

- **NFR-01:** Solo usuarios con rol `ADMINISTRADOR` pueden ver los botones de crear, editar y desactivar.
- **NFR-02:** Las fechas deben mostrarse en formato legible (DD/MM/YYYY) y editarse con un date picker.
- **NFR-03:** El estado debe mostrarse con un badge de color: `PLANIFICADO` (azul), `EN_CURSO` (verde), `FINALIZADO` (gris).
- **NFR-04:** Al desactivar un curso, mostrar un diálogo de confirmación advirtiendo que el curso desaparecerá del catálogo de la app móvil.

---

## 6. Estructura de Archivos Propuesta

```
src/
├── services/
│   └── cursoService.ts              # Servicio de API para cursos
├── types/
│   └── curso.ts                     # Tipos TypeScript
├── components/
│   └── cursos/
│       ├── CursoList.tsx            # Tabla de cursos
│       ├── CursoForm.tsx            # Formulario crear/editar
│       ├── CursoDetail.tsx          # Detalle de curso
│       └── CursoDeleteConfirm.tsx   # Diálogo de confirmación
├── pages/
│   └── CursosPage.tsx               # Página principal de cursos
└── hooks/
    └── useCursos.ts                 # Hook personalizado
```

---

## 7. Código de Referencia (TypeScript/React)

### 7.1. Tipos

```typescript
// types/curso.ts

export interface Curso {
  id: number;
  titulo: string;
  descripcion: string | null;
  areaTematica: string;
  unidadVecinal: string;
  fechaInicio: string;  // yyyy-MM-dd
  fechaTermino: string; // yyyy-MM-dd
  estado: 'PLANIFICADO' | 'EN_CURSO' | 'FINALIZADO';
}

export interface CursoCreateRequest {
  titulo: string;
  descripcion?: string;
  areaTematica: string;
  unidadVecinal: string;
  fechaInicio: string;
  fechaTermino: string;
  estado: 'PLANIFICADO' | 'EN_CURSO' | 'FINALIZADO';
}

export interface CursoUpdateRequest {
  titulo?: string;
  descripcion?: string;
  areaTematica?: string;
  unidadVecinal?: string;
  fechaInicio?: string;
  fechaTermino?: string;
  estado?: 'PLANIFICADO' | 'EN_CURSO' | 'FINALIZADO';
}
```

### 7.2. Servicio de API

```typescript
// services/cursoService.ts

import { Curso, CursoCreateRequest, CursoUpdateRequest } from '../types/curso';

const API_BASE = '/api/cursos';

class CursoService {
  private async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    const token = localStorage.getItem('jwtToken'); // Adaptar a tu auth context
    const response = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        ...options.headers,
      },
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ error: 'Error desconocido' }));
      throw new ApiError(error.error, response.status);
    }

    return response.json();
  }

  async listarCursos(): Promise<Curso[]> {
    return this.request<Curso[]>(API_BASE);
  }

  async obtenerCurso(id: number): Promise<Curso> {
    return this.request<Curso>(`${API_BASE}/${id}`);
  }

  async crearCurso(data: CursoCreateRequest): Promise<Curso> {
    return this.request<Curso>(API_BASE, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async editarCurso(id: number, data: CursoUpdateRequest): Promise<Curso> {
    return this.request<Curso>(`${API_BASE}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async desactivarCurso(id: number): Promise<Curso> {
    return this.request<Curso>(`${API_BASE}/${id}/desactivar`, {
      method: 'PATCH',
      body: '',
    });
  }
}

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

export const cursoService = new CursoService();
```

### 7.3. Hook personalizado

```typescript
// hooks/useCursos.ts

import { useState, useEffect, useCallback } from 'react';
import { Curso, CursoCreateRequest, CursoUpdateRequest } from '../types/curso';
import { cursoService, ApiError } from '../services/cursoService';

interface UseCursosReturn {
  cursos: Curso[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  crearCurso: (data: CursoCreateRequest) => Promise<Curso>;
  editarCurso: (id: number, data: CursoUpdateRequest) => Promise<Curso>;
  desactivarCurso: (id: number) => Promise<void>;
}

export function useCursos(): UseCursosReturn {
  const [cursos, setCursos] = useState<Curso[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await cursoService.listarCursos();
      setCursos(data);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Error al cargar cursos');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const crearCurso = async (data: CursoCreateRequest): Promise<Curso> => {
    try {
      const nuevo = await cursoService.crearCurso(data);
      await refresh();
      return nuevo;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Error al crear curso');
      throw e;
    }
  };

  const editarCurso = async (id: number, data: CursoUpdateRequest): Promise<Curso> => {
    try {
      const actualizado = await cursoService.editarCurso(id, data);
      await refresh();
      return actualizado;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Error al editar curso');
      throw e;
    }
  };

  const desactivarCurso = async (id: number): Promise<void> => {
    try {
      await cursoService.desactivarCurso(id);
      await refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Error al desactivar curso');
      throw e;
    }
  };

  return { cursos, loading, error, refresh, crearCurso, editarCurso, desactivarCurso };
}
```

### 7.4. Página principal

```tsx
// pages/CursosPage.tsx

import React, { useState } from 'react';
import { useCursos } from '../hooks/useCursos';
import { CursoForm } from '../components/cursos/CursoForm';
import { Curso } from '../types/curso';
import { useAuth } from '../hooks/useAuth'; // Adaptar a tu auth context

export function CursosPage() {
  const { cursos, loading, error, crearCurso, editarCurso, desactivarCurso } = useCursos();
  const { user, isAdmin } = useAuth();
  const [showForm, setShowForm] = useState(false);
  const [editingCurso, setEditingCurso] = useState<Curso | null>(null);

  const handleCrear = async (data: any) => {
    await crearCurso(data);
    setShowForm(false);
  };

  const handleEditar = async (data: any) => {
    if (editingCurso) {
      await editarCurso(editingCurso.id, data);
      setEditingCurso(null);
    }
  };

  const handleDesactivar = async (id: number) => {
    if (window.confirm('¿Estás seguro? El curso desaparecerá del catálogo de la app móvil.')) {
      await desactivarCurso(id);
    }
  };

  const getEstadoBadge = (estado: string) => {
    const colors: Record<string, string> = {
      PLANIFICADO: 'bg-blue-100 text-blue-800',
      EN_CURSO: 'bg-green-100 text-green-800',
      FINALIZADO: 'bg-gray-100 text-gray-800',
    };
    return colors[estado] || 'bg-gray-100 text-gray-800';
  };

  if (loading) return <div>Cargando cursos...</div>;
  if (error) return <div className="text-red-600">Error: {error}</div>;

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Gestión de Cursos</h1>
        {isAdmin && (
          <button
            onClick={() => { setEditingCurso(null); setShowForm(true); }}
            className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
          >
            + Nuevo Curso
          </button>
        )}
      </div>

      {showForm && (
        <CursoForm
          onSubmit={editingCurso ? handleEditar : handleCrear}
          onCancel={() => { setShowForm(false); setEditingCurso(null); }}
          initialData={editingCurso || undefined}
        />
      )}

      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Título</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Área Temática</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Unidad Vecinal</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Fechas</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Estado</th>
              {isAdmin && (
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Acciones</th>
              )}
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {cursos.map((curso) => (
              <tr key={curso.id}>
                <td className="px-6 py-4">{curso.titulo}</td>
                <td className="px-6 py-4">{curso.areaTematica}</td>
                <td className="px-6 py-4">{curso.unidadVecinal}</td>
                <td className="px-6 py-4">
                  {new Date(curso.fechaInicio).toLocaleDateString('es-ES')} —{' '}
                  {new Date(curso.fechaTermino).toLocaleDateString('es-ES')}
                </td>
                <td className="px-6 py-4">
                  <span className={`px-2 py-1 rounded text-xs font-medium ${getEstadoBadge(curso.estado)}`}>
                    {curso.estado}
                  </span>
                </td>
                {isAdmin && (
                  <td className="px-6 py-4 space-x-2">
                    <button
                      onClick={() => { setEditingCurso(curso); setShowForm(true); }}
                      className="text-blue-600 hover:text-blue-800"
                    >
                      Editar
                    </button>
                    <button
                      onClick={() => handleDesactivar(curso.id)}
                      className="text-red-600 hover:text-red-800"
                    >
                      Desactivar
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

### 7.5. Formulario de curso

```tsx
// components/cursos/CursoForm.tsx

import React, { useState } from 'react';
import { Curso, CursoCreateRequest } from '../../types/curso';

interface CursoFormProps {
  onSubmit: (data: CursoCreateRequest) => Promise<void>;
  onCancel: () => void;
  initialData?: Curso;
}

const ESTADOS = ['PLANIFICADO', 'EN_CURSO', 'FINALIZADO'] as const;

export function CursoForm({ onSubmit, onCancel, initialData }: CursoFormProps) {
  const [titulo, setTitulo] = useState(initialData?.titulo || '');
  const [descripcion, setDescripcion] = useState(initialData?.descripcion || '');
  const [areaTematica, setAreaTematica] = useState(initialData?.areaTematica || '');
  const [unidadVecinal, setUnidadVecinal] = useState(initialData?.unidadVecinal || '');
  const [fechaInicio, setFechaInicio] = useState(initialData?.fechaInicio || '');
  const [fechaTermino, setFechaTermino] = useState(initialData?.fechaTermino || '');
  const [estado, setEstado] = useState(initialData?.estado || 'PLANIFICADO');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!titulo.trim()) errs.titulo = 'El título es obligatorio';
    if (!areaTematica.trim()) errs.areaTematica = 'El área temática es obligatoria';
    if (!unidadVecinal.trim()) errs.unidadVecinal = 'La unidad vecinal es obligatoria';
    if (!fechaInicio) errs.fechaInicio = 'La fecha de inicio es obligatoria';
    if (!fechaTermino) errs.fechaTermino = 'La fecha de término es obligatoria';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    await onSubmit({
      titulo: titulo.trim(),
      descripcion: descripcion || undefined,
      areaTematica: areaTematica.trim(),
      unidadVecinal: unidadVecinal.trim(),
      fechaInicio,
      fechaTermino,
      estado,
    });
  };

  return (
    <div className="mb-6 p-4 border rounded bg-gray-50">
      <h2 className="text-lg font-semibold mb-4">
        {initialData ? 'Editar Curso' : 'Nuevo Curso'}
      </h2>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700">Título *</label>
          <input
            type="text"
            value={titulo}
            onChange={(e) => setTitulo(e.target.value)}
            className={`mt-1 block w-full rounded border ${errors.titulo ? 'border-red-500' : 'border-gray-300'} px-3 py-2`}
            maxLength={150}
          />
          {errors.titulo && <p className="text-red-500 text-sm">{errors.titulo}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Descripción</label>
          <textarea
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            className="mt-1 block w-full rounded border border-gray-300 px-3 py-2"
            rows={3}
            maxLength={1000}
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Área Temática *</label>
            <input
              type="text"
              value={areaTematica}
              onChange={(e) => setAreaTematica(e.target.value)}
              className={`mt-1 block w-full rounded border ${errors.areaTematica ? 'border-red-500' : 'border-gray-300'} px-3 py-2`}
              maxLength={60}
            />
            {errors.areaTematica && <p className="text-red-500 text-sm">{errors.areaTematica}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">Unidad Vecinal *</label>
            <input
              type="text"
              value={unidadVecinal}
              onChange={(e) => setUnidadVecinal(e.target.value)}
              className={`mt-1 block w-full rounded border ${errors.unidadVecinal ? 'border-red-500' : 'border-gray-300'} px-3 py-2`}
              maxLength={100}
            />
            {errors.unidadVecinal && <p className="text-red-500 text-sm">{errors.unidadVecinal}</p>}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Fecha Inicio *</label>
            <input
              type="date"
              value={fechaInicio}
              onChange={(e) => setFechaInicio(e.target.value)}
              className={`mt-1 block w-full rounded border ${errors.fechaInicio ? 'border-red-500' : 'border-gray-300'} px-3 py-2`}
            />
            {errors.fechaInicio && <p className="text-red-500 text-sm">{errors.fechaInicio}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">Fecha Término *</label>
            <input
              type="date"
              value={fechaTermino}
              onChange={(e) => setFechaTermino(e.target.value)}
              className={`mt-1 block w-full rounded border ${errors.fechaTermino ? 'border-red-500' : 'border-gray-300'} px-3 py-2`}
            />
            {errors.fechaTermino && <p className="text-red-500 text-sm">{errors.fechaTermino}</p>}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Estado *</label>
          <select
            value={estado}
            onChange={(e) => setEstado(e.target.value)}
            className="mt-1 block w-full rounded border border-gray-300 px-3 py-2"
          >
            {ESTADOS.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>

        <div className="flex justify-end space-x-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 border rounded text-gray-700 hover:bg-gray-100"
          >
            Cancelar
          </button>
          <button
            type="submit"
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            {initialData ? 'Guardar Cambios' : 'Crear Curso'}
          </button>
        </div>
      </form>
    </div>
  );
}
```

---

## 8. Integración con Autenticación

El panel debe integrarse con el sistema de autenticación existente (AWS Cognito). Los endpoints de escritura (`POST`, `PUT`, `PATCH`) requieren el rol `ADMINISTRADOR` en el JWT.

```typescript
// Ejemplo de verificación de rol
const { user, isAdmin } = useAuth();

// Solo mostrar botones de acción si es administrador
{isAdmin && <button onClick={handleCrear}>+ Nuevo Curso</button>}
```

**Importante:** La seguridad se aplica en el backend. El frontend solo oculta los botones para mejorar la UX, pero el backend valida el rol en cada request.

---

## 9. Reglas de Negocio

1. **Los cursos desactivados no aparecen en la lista.** Al desactivar un curso (`PATCH /desactivar`), el backend cambia el estado a `FINALIZADO`, y `GET /api/cursos` ya no lo devuelve.
2. **Solo `ADMINISTRADOR` puede crear, editar o desactivar.** El backend responde `403 Forbidden` para otros roles.
3. **Cualquier usuario autenticado puede leer cursos.** `GET /api/cursos` no requiere rol especial.
4. **Los eventos dentro de un curso no se borran al desactivar.** Solo el curso desaparece del catálogo; los eventos, inscripciones y asistencias se mantienen.
5. **El catálogo de la app móvil se actualiza automáticamente.** Los cursos creados o desactivados se reflejan en la próxima sincronización de la app.

---

## 10. Notas Importantes

- **El backend ya está implementado y funcionando.** No necesitas modificar el backend.
- **`CursoResponse` tiene la misma estructura que `ProyectoDto`.** Los campos son idénticos: `id`, `titulo`, `descripcion`, `areaTematica`, `unidadVecinal`, `fechaInicio`, `fechaTermino`, `estado`.
- **Los endpoints de escritura están protegidos por rol.** El backend valida el rol `ADMINISTRADOR` en el JWT.
- **No se requiere migración de base de datos.** Se usa el campo `estado` existente de la tabla `proyecto`.

---

## 11. Checklist de Implementación

- [ ] Crear tipos TypeScript (`Curso`, `CursoCreateRequest`, `CursoUpdateRequest`)
- [ ] Crear `cursoService.ts` con métodos para listar, obtener, crear, editar y desactivar
- [ ] Crear hook `useCursos` con estado y manejo de errores
- [ ] Crear página `CursosPage` con tabla de cursos
- [ ] Crear componente `CursoForm` para crear/editar
- [ ] Agregar badges de estado con colores (PLANIFICADO, EN_CURSO, FINALIZADO)
- [ ] Restringir botones de acción solo para `ADMINISTRADOR`
- [ ] Agregar diálogo de confirmación al desactivar un curso
- [ ] Manejar errores: `400` (validación), `403` (sin permiso), `404` (no encontrado), `409` (ya desactivado)
- [ ] Formatear fechas en la UI (DD/MM/YYYY)
- [ ] Agregar ruta en el router para `/cursos`
- [ ] Agregar enlace en el menú de navegación (solo `ADMINISTRADOR`)
- [ ] Probar que un usuario no `ADMINISTRADOR` solo puede ver la lista
