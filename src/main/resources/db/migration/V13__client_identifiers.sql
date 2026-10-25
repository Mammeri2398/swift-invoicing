-- V13__client_identifiers.sql : identifiants des clients professionnels (RC, NIS, article d'imposition),
-- imprimés sur les factures et copiés sur la facture à son émission, comme le NIF.
ALTER TABLE client ADD COLUMN rc_number VARCHAR(50);
ALTER TABLE client ADD COLUMN nis VARCHAR(50);
ALTER TABLE client ADD COLUMN tax_article VARCHAR(50);

ALTER TABLE invoice ADD COLUMN client_rc_number VARCHAR(50);
ALTER TABLE invoice ADD COLUMN client_nis VARCHAR(50);
ALTER TABLE invoice ADD COLUMN client_tax_article VARCHAR(50);
