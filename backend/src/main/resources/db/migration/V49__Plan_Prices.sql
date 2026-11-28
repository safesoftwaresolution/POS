-- Commercial prices of the plan catalog. NULL means "price not defined yet"; no seed values and no CHECK
-- (validated in the application). Plan limits and stores.plan_code are not touched.
ALTER TABLE plans ADD COLUMN monthly_price DECIMAL(12,2);
ALTER TABLE plans ADD COLUMN yearly_price DECIMAL(12,2);
