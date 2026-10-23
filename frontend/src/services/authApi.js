import { fetchApi, setAuthToken, setRefreshToken, getRefreshToken } from './http';

export async function loginApi(username, password) {
  const data = await fetchApi('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  if (data && data.token) {
    setAuthToken(data.token);
  }
  if (data && data.refreshToken) {
    setRefreshToken(data.refreshToken);
  }
  return data;
}

export async function registerApi(payload) {
  return fetchApi('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function resendVerificationApi(email) {
  return fetchApi('/auth/resend-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function forgotPasswordApi(email) {
  return fetchApi('/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function resetPasswordApi(token, newPassword) {
  return fetchApi('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  });
}

export async function verifyEmailApi(token) {
  const data = await fetchApi(`/auth/verify-email?token=${encodeURIComponent(token)}`);
  if (data && data.token) {
    setAuthToken(data.token);
  }
  if (data && data.refreshToken) {
    setRefreshToken(data.refreshToken);
  }
  return data;
}

// Renueva la sesion contra /auth/refresh y devuelve el LoginResponse completo (incluye
// storeId/role al dia). Se usa, ademas del reintento automatico transparente de fetchApi en
// un 401, justo despues de completar el onboarding del local: el JWT emitido en el login
// original tiene storeId=null (el usuario aun no tenia local), y sin este refresh explicito
// el frontend seguiria creyendo que no hay local y rebotaria de vuelta al onboarding.
export async function refreshSessionApi() {
  const data = await fetchApi('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify(getRefreshToken() ? { refreshToken: getRefreshToken() } : {}),
  });
  if (data && data.token) {
    setAuthToken(data.token);
  }
  if (data && data.refreshToken) {
    setRefreshToken(data.refreshToken);
  }
  return data;
}

export async function logoutApi() {
  try {
    await fetchApi('/auth/logout', {
      method: 'POST',
      body: JSON.stringify(getRefreshToken() ? { refreshToken: getRefreshToken() } : {}),
    });
  } catch {
    // Ignore on logout
  } finally {
    setAuthToken('');
    setRefreshToken('');
  }
}
