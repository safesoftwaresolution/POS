# Prueba de carga (k6)

Valida la Fase 5 del plan de escalabilidad. **No lo apuntes a H2/dev**: usa una réplica con la misma forma
del VPS (2 vCPU / 8 GB, Postgres) o producción en horario valle con tiendas de prueba.

## Requisitos
- [k6](https://k6.io/docs/get-started/installation/) instalado.
- Cuentas `ADMINISTRATOR` de tiendas de prueba, con productos activos y stock suficiente. Los reportes exigen
  `ADMINISTRATOR`, por eso el script usa esa cuenta para ambos flujos.

## Ejecución
```bash
# 50, 100 y 200 locales (cada uno ~0,5 req/s) + 10 reportes concurrentes
k6 run -e BASE_URL=https://api.ejemplo.com -e PROFILE=50  -e USERS='t1:Clave1,t2:Clave2' pos-load.js
k6 run -e BASE_URL=https://api.ejemplo.com -e PROFILE=100 -e USERS='t1:Clave1,t2:Clave2' pos-load.js
k6 run -e BASE_URL=https://api.ejemplo.com -e PROFILE=200 -e USERS='t1:Clave1,t2:Clave2' pos-load.js

# Línea base sin reportes
k6 run -e PROFILE=100 -e REPORT_VUS=0 pos-load.js
```

| Variable | Default | Descripción |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | API a probar |
| `PROFILE` | `50` | Locales simulados (VUs) |
| `USERS` | `admin:Password123` | Cuentas `usuario:clave` separadas por coma; los VUs las reparten |
| `REPORT_VUS` | `10` | Reportes concurrentes (`0` desactiva) |
| `REPORT_DAYS` | `30` | Ventana de los reportes |
| `DURATION` | `10m` | Duración en régimen (más 2 min de subida y 1 de bajada) |

Umbrales (fallan la corrida si no se cumplen): `sale_duration p95 < 800 ms`, `report_duration p95 < 5 s`,
errores HTTP y de negocio < 1 %.

## Qué observar durante la corrida
- Dokploy → Monitoring: CPU y RAM del backend y de Postgres.
- Conexiones del pool: `SELECT state, count(*) FROM pg_stat_activity WHERE datname = 'app_db' GROUP BY state;`
  (esperar `idle in transaction` bajo y sin crecer; si hay hilos esperando, subir `HIKARI_MAX_POOL_SIZE`).
- Comparar el p95 de `sales_create` con y sin reportes concurrentes.
- Con los resultados, reemplazar las estimaciones de `docs/_deployment.md` ("Capacidad y límites") por números medidos.
