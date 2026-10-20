// Cliente HTTP compartido — maneja el token JWT, el refresh transparente en 401
// y el envio/lectura JSON. Todos los modulos de servicios (`*Api.js`) construyen
// sus llamadas sobre `fetchApi`; ningun otro modulo debe usar `fetch` directamente.

const API_BASE_URL = '/api/v1';

// Genera una clave de idempotencia (UUID). Se usa en operaciones no idempotentes
// (ventas, compras) para que un reenvío no cree registros duplicados en el backend.
export function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

let authToken = localStorage.getItem('jwt_token') || '';
let refreshToken = localStorage.getItem('refresh_token') || '';

export function getAuthToken() {
  return authToken;
}

export function setAuthToken(token) {
  authToken = token || '';
  if (token) {
    localStorage.setItem('jwt_token', token);
  } else {
    localStorage.removeItem('jwt_token');
  }
}

export function getRefreshToken() {
  return refreshToken;
}

export function setRefreshToken(token) {
  refreshToken = token || '';
  if (token) {
    localStorage.setItem('refresh_token', token);
  } else {
    localStorage.removeItem('refresh_token');
  }
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
    // Evita bucles: no intentar renovar el propio endpoint de refresh.
    if (!_retried && endpoint !== '/auth/refresh') {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return fetchApi(endpoint, options, true);
      }
    }
    setAuthToken('');
    setRefreshToken('');
    throw new Error('Sesión expirada o credenciales no válidas.');
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `Error HTTP ${response.status}`);
  }

  if (response.status === 204) return true;
  return await response.json();
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
    throw new Error('Sesión expirada o credenciales no válidas.');
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `Error HTTP ${response.status}`);
  }

  if (response.status === 204) return true;
  return await response.json();
}
