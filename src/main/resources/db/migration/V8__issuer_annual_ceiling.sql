-- V8__issuer_annual_ceiling.sql : le plafond annuel de chiffre d'affaires devient un réglage de l'émetteur,
-- modifiable dans les paramètres (il était fixé dans application.properties).
ALTER TABLE issuer ADD COLUMN annual_ceiling NUMERIC(14,2) NOT NULL DEFAULT 5000000;
ALTER TABLE issuer ADD CONSTRAINT chk_issuer_annual_ceiling CHECK (annual_ceiling > 0);
