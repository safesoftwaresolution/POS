import React, { createContext, useContext, useState } from 'react';
import { loginApi, logoutApi, refreshSessionApi } from '../services/authApi';
import { getAuthToken, setAuthToken } from '../services/http';

const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const token = getAuthToken();
    const storedUser = localStorage.getItem('pos_user');
    if (token && storedUser) {
      try {
        return JSON.parse(storedUser);
      } catch {
        return null;
      }
    }
    return null;
  });

  const [loading, setLoading] = useState(false);

  const applySession = (data, fallbackUsername) => {
    const userData = {
      username: data.username || fallbackUsername,
      role: data.role || 'ADMINISTRATOR',
      fullName: data.fullName || (data.username === 'admin' ? 'Administrador Principal' : data.username || fallbackUsername),
      storeId: data.storeId ?? null,
      token: data.token
    };
    setAuthToken(data.token);
    localStorage.setItem('pos_user', JSON.stringify(userData));
    setUser(userData);
    return userData;
  };

  const login = async (username, password) => {
    setLoading(true);
    try {
      const data = await loginApi(username, password);
      const userData = applySession(data, username);
      setLoading(false);
      return userData;
    } catch (err) {
      setLoading(false);
      throw err;
    }
  };

  // Usado tras verificar el correo (POST /auth/verify-email ya autologuea al usuario).
  const setSessionFromResponse = (data) => applySession(data);

  // Vuelve a emitir el access token y trae storeId/role al dia desde el backend. Necesario tras
  // completar el onboarding del local: el JWT/usuario en memoria quedaron con storeId=null desde
  // el login original, y sin esto el guard de rutas seguiria mandando al usuario a /onboarding/store.
  const refreshSession = async () => {
    const data = await refreshSessionApi();
    return applySession(data, user?.username);
  };

  const logout = async () => {
    setLoading(true);
    await logoutApi();
    localStorage.removeItem('pos_user');
    setUser(null);
    setLoading(false);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, loading, isAuthenticated: !!user, setSessionFromResponse, refreshSession }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
