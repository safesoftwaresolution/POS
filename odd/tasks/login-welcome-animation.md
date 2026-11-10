# Login welcome animation
Objective: post-login animation like Gastroflow (full-screen brand animation, greeting, auto redirect, click to skip), built with the POS Bento isotype blocks.
Branch: feature/login-welcome-animation (from develop). Runner: `npm test`, `npm run lint`, `npm run build` in frontend.

## Specs
- S1 "hacer una animacion de inicio de sesion como tenemos en gastroflow, con respecto a los hizotipos de pos bento, hacer una animacion chimba" -> Gastroflow reference (public/js/auth/login.js `mostrarBienvenida`): overlay, brand animation ~2 s, greeting "¡Hola, nombre!", redirect after ~2.6 s, click skips, reduced motion respected.
- S2 Animation made in SVG+CSS (no video asset): tile pop, four bento blocks fly in, brand dot with ripple, wordmark reveal, greeting, progress bar.

## Tasks
- [x] T1 S1-S2 inline: WelcomeSplash, ws-* keyframes, LoginBento wiring, greeting helper + test, DESIGN.md §2.1a

## Log
L1 (user): "me gustaria hacer una animacion de inicio de sesion como tenemos en gastroflow, con respecto a los hizotipos de pos bento, hacer una animacion chimba"
L2 Verified: 84 frontend tests, lint clean, build ok; frames captured with headless Edge at 0.7/1.3/2.1 s. Not run through a real login in the browser.
