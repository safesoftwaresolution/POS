-- Una venta a fiado (payment_method = 'CREDIT') no recibe efectivo: el CHECK de V1 (cash_received >= total_amount)
-- la rechazaba. El resto de métodos conserva la regla original.
ALTER TABLE sales DROP CONSTRAINT CK_sale_payment;
ALTER TABLE sales ADD CONSTRAINT CK_sale_payment CHECK (payment_method = 'CREDIT' OR cash_received >= total_amount);
