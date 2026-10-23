-- V22__Store_Plans.sql
-- Planes de suscripcion: cada local tiene un plan que limita cuantos usuarios adicionales (ademas del
-- administrador) puede tener activos. Los limites viven en la tabla plans para ajustarlos sin desplegar.
CREATE TABLE plans (
    code                 VARCHAR(20) NOT NULL,
    name                 VARCHAR(50) NOT NULL,
    max_additional_users INT         NOT NULL,
    sort_order           INT         NOT NULL,

    CONSTRAINT PK_plans PRIMARY KEY (code)
);

INSERT INTO plans (code, name, max_additional_users, sort_order) VALUES ('BASIC', 'Básico', 1, 1);
INSERT INTO plans (code, name, max_additional_users, sort_order) VALUES ('STANDARD', 'Estándar', 4, 2);
INSERT INTO plans (code, name, max_additional_users, sort_order) VALUES ('PRO', 'Pro', 10, 3);

-- Los locales que ya existen pasan al plan mas alto para no afectar a nadie; los nuevos empiezan en BASIC
-- (valor por defecto en la entidad) y el SUPER_ADMIN puede cambiarlos.
ALTER TABLE stores ADD COLUMN plan_code VARCHAR(20);
UPDATE stores SET plan_code = 'PRO';
ALTER TABLE stores ALTER COLUMN plan_code SET NOT NULL;
ALTER TABLE stores ALTER COLUMN plan_code SET DEFAULT 'BASIC';
ALTER TABLE stores ADD CONSTRAINT FK_stores_plan FOREIGN KEY (plan_code) REFERENCES plans(code);
