-- V8__Sale_Payment_Method.sql
-- Registra el método de pago de cada venta (código del tema de parámetros PAYMENT_METHODS,
-- ej. CASH, NEQUI, CARD, TRANSFER). DEFAULT 'CASH' preserva las ventas existentes, que hasta
-- ahora solo soportaban efectivo.
ALTER TABLE sales ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH';
