// Cliente HTTP compartido — maneja el token JWT, el refresh transparente en 401
// y el envio/lectura JSON. Todos los modulos de servicios (`*Api.js`) construyen
// sus llamadas sobre `fetchApi`; ningun otro modulo debe usar `fetch` directamente.

// En produccion el backend vive en otro dominio: VITE_API_URL se incrusta al compilar.
// Sin ella (dev) se usa la ruta relativa y el proxy de Vite.
const API_ORIGIN = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '');
export const API_BASE_URL = `${API_ORIGIN}/api/v1`;

// Genera una clave de idempotencia (UUID). Se usa en operaciones no idempotentes
// (ventas, compras) para que un reenvío no cree registros duplicados en el backend.
export function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

// Los tokens viven SOLO en memoria. La sesion real viaja en cookies HttpOnly (jwt_token y refresh_token) que el
// navegador envia con credentials: 'include' y que el JavaScript no puede leer: un XSS ya no puede robar una
// sesion de 7 dias guardada en localStorage. Tras recargar la pagina no hay token en memoria y la cookie
// autentica (y el 401 dispara el refresh por cookie).
let authToken = '';
let refreshToken = '';

// Limpia los tokens que versiones anteriores dejaban en localStorage.
try {
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('refresh_token');
} catch {
  // localStorage no disponible (modo privado): nada que limpiar.
}

// Aviso para que AuthContext cierre la sesion de la UI cuando el backend ya no la acepta.
export const SESSION_EXPIRED_EVENT = 'auth:session-expired';

function notifySessionExpired() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
}

export function getAuthToken() {
  return authToken;
}

export function setAuthToken(token) {
  authToken = token || '';
}

export function getRefreshToken() {
  return refreshToken;
}

export function setRefreshToken(token) {
  refreshToken = token || '';
}

// Deduplica renovaciones concurrentes: varias peticiones que reciben 401 a la vez
// comparten una única promesa de refresh.
let refreshPromise = null;

async function refreshAccessToken() {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(refreshToken ? { refreshToken } : {}),
      });
      if (!response.ok) return false;

      const data = await response.json();
      if (data && data.token) setAuthToken(data.token);
      if (data && data.refreshToken) setRefreshToken(data.refreshToken);
      return !!(data && data.token);
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

// Core HTTP Fetch Wrapper
// Respuestas exitosas sin cuerpo (204, o 201 de /auth/register): response.json() fallaria con
// "Unexpected end of JSON input" aunque la operacion haya funcionado.
async function parseSuccessBody(response) {
  if (response.status === 204) return true;
  const text = await response.text();
  return text ? hoistPageMeta(JSON.parse(text)) : true;
}

// Spring Boot 4 serializa una Page como `{ content, page: { size, number, totalElements, totalPages } }`;
// el frontend lee `totalElements`/`totalPages` en la raíz, así que se suben aquí, en un solo sitio.
function hoistPageMeta(data) {
  if (data && Array.isArray(data.content) && data.page && typeof data.page === 'object' && data.totalPages === undefined) {
    const { size, number, totalElements, totalPages } = data.page;
    return { ...data, size, number, totalElements, totalPages };
  }
  return data;
}

// Error de una respuesta no exitosa. Además del mensaje lleva `status` y el cuerpo en `data`, porque algunos
// endpoints devuelven detalle útil (p. ej. la vista previa de una importación rechazada con 422).
async function httpError(response) {
  const errorData = await response.json().catch(() => ({}));
  const error = new Error(errorData.message || `Error HTTP ${response.status}`);
  error.status = response.status;
  error.data = errorData;
  return error;
}

export async function fetchApi(endpoint, options = {}, _retried = false) {
  const headers = {
    'Content-Type': 'application/json',
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: 'include',
  });

  if (response.status === 401) {
    // Un 401 de /auth/login es 'credenciales incorrectas': se muestra el mensaje del backend, sin intentar
    // renovar ni cerrar una sesion que no existe. Tampoco se renueva el propio endpoint de refresh.
    if (endpoint === '/auth/login') {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || 'Usuario o contraseña incorrectos');
    }
    if (!_retried && endpoint !== '/auth/refresh') {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return fetchApi(endpoint, options, true);
      }
    }
    setAuthToken('');
    setRefreshToken('');
    notifySessionExpired();
    throw new Error('Sesión expirada o credenciales no válidas.');
  }

  if (!response.ok) {
    throw await httpError(response);
  }

  return await parseSuccessBody(response);
}

// Variante de fetchApi para subir archivos (multipart/form-data). No fija 'Content-Type':
// el navegador debe generar el boundary del multipart por su cuenta. Comparte el mismo
// manejo de token/refresh/errores que fetchApi.
export async function uploadFileApi(endpoint, formData, options = {}, _retried = false) {
  const headers = {
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    method: options.method || 'POST',
    headers,
    body: formData,
    credentials: 'include',
  });

  if (response.status === 401) {
    if (!_retried) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return uploadFileApi(endpoint, formData, options, true);
      }
    }
    setAuthToken('');
    setRefreshToken('');
    notifySessionExpired();
    throw new Error('Sesión expirada o credenciales no válidas.');
  }

  if (!response.ok) {
    throw await httpError(response);
  }

  return await parseSuccessBody(response);
}

// Variante de fetchApi para descargar archivos (Excel, etc.): devuelve un Blob en vez de JSON.
// Comparte el mismo manejo de token/refresh/errores que fetchApi.
export async function downloadFileApi(endpoint, _retried = false) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    headers: authToken ? { Authorization: `Bearer ${authToken}` } : {},
    credentials: 'include',
  });

  if (response.status === 401) {
    if (!_retried) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return downloadFileApi(endpoint, true);
      }
    }
    setAuthToken('');
    setRefreshToken('');
    notifySessionExpired();
    throw new Error('Sesión expirada o credenciales no válidas.');
  }

  if (!response.ok) {
    throw await httpError(response);
  }

  return await response.blob();
}
