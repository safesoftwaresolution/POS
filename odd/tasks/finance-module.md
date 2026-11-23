# finance-module
Objetivo: módulo de Finanzas (base caja) para el POS, inspirado en Gastroflow. Rama: main. Runner: `.\mvnw.cmd test` (backend), `npm test` (frontend).

## Specs
- S1. "base caja (MVP)": utilidad = ingresos − (compras + gastos + costos fijos prorrateados). Ingresos = ventas no `CREDIT` + abonos de fiado − devoluciones de ventas no `CREDIT`.
- S2. Gastos manuales (`expenses`) con categoría, monto, método de pago y fecha real (no futura); costos fijos mensuales (`fixed_costs`) activables, prorrateados mensual ÷ 30 × días.
- S3. Resumen por período con comparativa contra el período anterior de igual longitud, gastos por categoría, ingresos por método de pago y serie diaria.
- S4. Solo `ADMINISTRATOR`; todo filtrado por `storeId`.
- S5. Fuera de alcance (backlog): cuentas por pagar, PDF del estado financiero, costo de lo vendido.

- S6. "en lo de costos fijos que se pueda tambien agregar la fecha de cuando se tiene que cobrar o como funciona lo de costos fijos": `due_day` (1-31, opcional) como recordatorio con la próxima fecha, y una nota de cómo funcionan.
- S7. "los botones de agregar eso quedan muy abajo la gente necesita saber que es lo que tiene que ubicar seria ponerlo en la parte de arriba": botones de alta arriba de la página.
- S8. "poder ingresar, si es egreso o ingreso": movimientos manuales con tipo `EXPENSE`/`INCOME` (supuesto: un ingreso manual suma a los ingresos y a la utilidad).

## Tasks
- T1 (S1,S2) Migración V48 + entidades/repositorios — inline
- T2 (S1-S4) Servicio, DAO SQL, controller — inline
- T3 (S1-S4) Tests backend — inline
- T4 (S3) Frontend `/finance` — inline
- T5 Docs (spec, schema, roadmap) — inline

- T6 (S6-S8) Ronda 2: V48 reescrita (`finance_movements` + `due_day`, aún sin commitear ni aplicada), API `/movements`, UI con botones arriba y día de cobro — inline

## Estado (sin commits; el usuario no los pidió)
- T1-T5 hechas y verificadas: backend 1354 tests, 0 fallos (17 nuevos de finance); frontend lint 0, 141 tests, build OK.
- Pendiente: probar la UI en el navegador con datos reales; commits separados por área.
- Risk: item 1 en sentido débil (migración V48 solo crea tablas nuevas, no toca datos existentes); resto: none.

## Log
- L2 (2026-11-23): "en lo de costos fijos que se pueda tambien agregar la fecha de cuando se tiene que cobrar o como funciona lo de costos fijos y los botones de agregar eso quedan muy abajo la gente necesita saber que es lo que tiene que ubicar seria ponerlo en la parte de arriba, como tambien poner lo que es hacer algo parecido como en gastroflow poder ingresar, si es egreso o ingreso, me entiendes la idea"
- L1 (2026-11-23): "ve tenia una pregunta en el pos tenemos un apartado de finanzas?" → "revisa reportes a fondo y hacer el modulo de finanzas me gustaria que miraras como esta construido en gastroflow y traigas o montes algo parecido" → "Base caja (MVP)"
