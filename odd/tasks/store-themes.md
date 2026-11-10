# Feature: store-themes (tema visual por categoría de local)
Objetivo: la interfaz cambia su acento e icono según la categoría del local, automáticamente. Rama: feature/store-themes (desde develop). Runner: backend `.\mvnw.cmd test`, frontend `npm test` + `npm run lint`.

## Specs
- S1: "Que cambie según la categoría automáticamente": el tema visual de un local se deriva de su categoría; sin categoría, tema DEFAULT (el naranja de marca actual, sin cambios).
- S2: "siguiendo el branding y el tema de colores y diseño que ya manejamos": solo cambian el acento (`--color-primary` y su hover) y un icono; neutros, formas, tipografía y modo claro/oscuro no cambian. Cada acento supera el contraste del naranja actual (texto blanco >= 3,2; texto sobre tarjeta oscura >= 4,5).
- S3: "si es un mini market, ... si es tienda de ropa, ... si es una papelería": minimercado/supermercado/tienda de barrio = GROCERY, boutique = FASHION, papelería = STATIONERY; además droguería, panadería, ferretería, restaurante, licorera, mascotas y belleza (barbería, peluquería, uñas) (assumption: el usuario dio tres ejemplos; el resto de categorías base recibe tema por analogía).
- S4 (assumption): el SUPER_ADMIN puede cambiar el tema de una categoría en el formulario de categorías, para que las categorías nuevas no queden en DEFAULT.
- S5: "que se puedan seleccionar varias plantillas o que la persona tenga acceso a varias plantillas": NO incluido aquí; pendiente de decisión.

## Tasks
- [x] T1 (S1,S3,S4) Backend: StoreTheme + V35 + DTOs + seeder + endpoint + tests — inline — commit e8fc483; 856 backend tests green; endpoint verified against a dev backend on in-memory H2
- [x] T2 (S1,S2,S4) Frontend: temas (acentos), aplicación en el layout, icono, selector en categorías + tests — inline — commit 64d9e33; 64 frontend tests, lint 0, build ok; NOT checked in a browser
- [x] T3 Docs: DESIGN.md §2.1b, stores spec RN-STORE-007, schema, CLAUDE.md

## Log
- L1 (2026-11-05): "Pero la pregunta es si, obviamente, se cambia la categoría o está ligado con el tema de la plantilla [...] El sistema cambia acorde a las plantillas: si es un mini market, sea decoración tipo mini market; si es tienda de ropa, sea decoración de ropa; si es una papelería, tenga decoración de papelería. Todo es progisterio, teniendo en cuenta y siguiendo el branding y el tema de colores y diseño que ya manejamos."
- L2 (2026-11-05): "Que cambie según la categoría automáticamente" (respuesta a: el tema visual cambia según la categoría o se elige local por local).
