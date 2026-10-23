<!-- doc-version: 1.4 | last-updated: 2026-10-16 -->
# Índice del Proyecto POS

## Resolución Rápida por Módulo

| Módulo | Spec | Tablas BD | Endpoint Base |
|---|---|---|---|
| auth | modules/auth/spec.md | refresh_tokens, email_verification_tokens | /api/v1/auth |
| users | modules/users/spec.md | users | /api/v1/users |
| products | modules/products/spec.md | products | /api/v1/products |
| categories | modules/categories/spec.md | categories, product_subcategories | /api/v1/categories, /api/v1/subcategories |
| inventory | modules/inventory/spec.md | inventory_movements | /api/v1/inventory |
| purchases | modules/purchases/spec.md | purchases, purchase_items | /api/v1/purchases |
| sales | modules/sales/spec.md | sales, sale_items, sale_returns, sale_return_items | /api/v1/sales |
| customers | modules/customers/spec.md | customers | /api/v1/customers |
| suppliers | modules/suppliers/spec.md | suppliers | /api/v1/suppliers |
| reports | modules/reports/spec.md | (read-only) | /api/v1/reports |
| settings | modules/settings/spec.md | settings, parameter_topics, parameter_values | /api/v1/settings |
| billing | modules/billing/spec.md | electronic_invoices, electronic_credit_notes, electronic_debit_notes, store_billing_config, numbering_range_audit | /api/v1/billing, /api/v1/backoffice/billing, /api/v1/backoffice/stores/{id}/billing |
| stores | modules/stores/spec.md | stores, store_categories, store_documents | /api/v1/stores, /api/v1/backoffice/stores |
| legal | modules/legal/spec.md | legal_documents | /api/v1/legal, /api/v1/backoffice/legal-documents |
| support | modules/support/spec.md | support_tickets | /api/v1/support, /api/v1/backoffice/support |
| notifications | modules/notifications/spec.md | notifications | /api/v1/notifications |
| storage | modules/storage/spec.md | (Cloudflare R2, sin tablas) | /api/v1/storage |
| backoffice | modules/backoffice/spec.md | (opera sobre users y stores) | /api/v1/backoffice/staff, /api/v1/backoffice/onboarding |

## Documentos Globales

| Archivo | Cuándo cargarlo |
|---|---|
| _project.md | Primer contacto con el proyecto |
| _architecture.md | Crear módulo nuevo o refactorizar |
| _conventions.md | Escribir código nuevo |
| _security.md | Tocar autenticación o permisos |
| _database-schema.md | Crear tablas nuevas o relaciones |
| _deployment.md | Configurar entorno o desplegar |
| _glossary.md | Término desconocido del dominio |
| _roadmap.md | Estado por módulo, deuda técnica, ideas de valor agregado y plan de trabajo vigente |
