# Store brand logos
Objective: sidebar logo per store theme (isotype glyph + wordmark in accent + label), like the user's reference screenshots (CapitánDATA POS per business type), following POS Bento branding.
Branch: feature/store-brand-logos (from develop). Runner: `npm test`, `npm run lint`, `npm run build` in frontend.

## Specs
- S1 "armar los logos ... siguiendo el branding y los isotipos que usamos en pos bento colores y demas, pero me gustaria referenciarlos" -> SVG logos built in code from the POS Bento isotype (tile + corner dot), one glyph per theme, accent from the theme.
- S2 Reference (screenshots): business-type logo, brand wordmark colored by theme, type label next to the brand, tinted header band.

## Tasks
- [x] T1 S1-S2 inline: StoreBrandLogo (11 glyphs), wordmark paths, sidebar header wired, test, DESIGN.md/CLAUDE.md

## Log
L1 (user): "a esto me refiero mira este es un sistema de referencia y me gustaria hacer lo mismo para pos bento"
L2 (user): "seria armar los logos, tambien pues obviamente siguiendo el branding y los isotipos que usamos en pos bento colores y demas, pero me gustaria referenciarlos"
L3 Verified: 82 frontend tests, lint clean, build ok; contact sheet of all 11 logos rendered with headless Edge in light and dark. Not checked inside the running app.
