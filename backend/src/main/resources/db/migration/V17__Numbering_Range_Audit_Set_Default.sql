-- V17__Numbering_Range_Audit_Set_Default.sql
-- El administrador del local elige cual de sus rangos de numeracion usa para facturar
-- (POST /api/v1/billing/numbering-ranges/{id}/set-default). Ese cambio tambien se audita, asi que
-- la accion 'SET_DEFAULT' se suma a las permitidas por el CHECK de V15.
ALTER TABLE numbering_range_audit DROP CONSTRAINT CK_numbering_range_audit_action;

ALTER TABLE numbering_range_audit ADD CONSTRAINT CK_numbering_range_audit_action CHECK (action IN
    ('CREATE', 'DELETE', 'ACTIVATE', 'DEACTIVATE', 'UPDATE_CURRENT', 'SET_DEFAULT'));
