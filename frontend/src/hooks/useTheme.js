import { useContext } from 'react';
import { ThemeContext } from '../context/contexts';

/** Tema claro/oscuro: `darkMode` y `toggleTheme` (ver `ThemeProvider`). */
export default function useTheme() {
  return useContext(ThemeContext);
}
