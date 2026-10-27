-- V23__accounting_settings.sql : comptes et journaux des écritures comptables exportées pour le comptable.
-- Une seule ligne (id = 1), créée au premier enregistrement ; sans elle, les comptes par défaut du SCF s'appliquent.
CREATE TABLE accounting_settings (
    id                BIGINT PRIMARY KEY,
    customer_account  VARCHAR(20) NOT NULL,
    sales_account     VARCHAR(20) NOT NULL,
    vat_account       VARCHAR(20) NOT NULL,
    stamp_account     VARCHAR(20) NOT NULL,
    bank_account      VARCHAR(20) NOT NULL,
    cash_account      VARCHAR(20) NOT NULL,
    sales_journal     VARCHAR(10) NOT NULL,
    bank_journal      VARCHAR(10) NOT NULL,
    cash_journal      VARCHAR(10) NOT NULL
);
