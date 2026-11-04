import { useContext } from 'react';
import { AuthContext } from '../context/contexts';

/** Sesion actual: `user`, `login`, `logout`, etc. (ver `AuthProvider`). */
export default function useAuth() {
  return useContext(AuthContext);
}
