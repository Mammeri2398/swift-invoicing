-- db/tenant V2 : un prix unitaire peut avoir jusqu'à 6 décimales (un forfait mensuel divisé en jours :
-- 130 000 / 22 = 5 909,090909). Les totaux des lignes et des factures restent arrondis au centime.
ALTER TABLE invoice_line ALTER COLUMN unit_price SET DATA TYPE NUMERIC(18,6);
ALTER TABLE catalog_item ALTER COLUMN unit_price SET DATA TYPE NUMERIC(18,6);
ALTER TABLE recurring_invoice_line ALTER COLUMN unit_price SET DATA TYPE NUMERIC(18,6);
