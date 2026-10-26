import { createContext } from 'react';

// Los objetos de contexto viven aparte de los Provider para que los archivos de componentes solo exporten componentes
// (requisito del refresco en caliente de Vite) y los hooks `useAuth`/`useModal`/`useTheme` los lean desde aqui.
export const AuthContext = createContext();
export const ModalContext = createContext(null);
export const ThemeContext = createContext();
