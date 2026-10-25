-- V12__company_type_and_vat.sql : type d'entreprise de l'émetteur (auto-entrepreneur ou société),
-- identifiants légaux d'une société et TVA par ligne de facture.

-- Émetteur
ALTER TABLE issuer ADD COLUMN company_type VARCHAR(20) NOT NULL DEFAULT 'AUTO_ENTREPRENEUR';
ALTER TABLE issuer ADD COLUMN legal_form VARCHAR(50);
ALTER TABLE issuer ADD COLUMN share_capital NUMERIC(14,2);
ALTER TABLE issuer ADD COLUMN rc_number VARCHAR(50);
-- Article d'imposition (AI)
ALTER TABLE issuer ADD COLUMN tax_article VARCHAR(50);
-- La carte auto-entrepreneur ne concerne plus que les auto-entrepreneurs
ALTER TABLE issuer ALTER COLUMN ae_card_number DROP NOT NULL;

-- Instantané de l'émetteur sur la facture
ALTER TABLE invoice ADD COLUMN issuer_company_type VARCHAR(20);
ALTER TABLE invoice ADD COLUMN issuer_legal_form VARCHAR(50);
ALTER TABLE invoice ADD COLUMN issuer_share_capital NUMERIC(14,2);
ALTER TABLE invoice ADD COLUMN issuer_rc_number VARCHAR(50);
ALTER TABLE invoice ADD COLUMN issuer_tax_article VARCHAR(50);
UPDATE invoice SET issuer_company_type = 'AUTO_ENTREPRENEUR' WHERE status <> 'DRAFT';

-- TVA : taux par ligne (0 pour un auto-entrepreneur), totaux HT / TVA / TTC sur la facture.
-- total_amount reste le montant à payer (TTC).
ALTER TABLE invoice_line ADD COLUMN vat_rate NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE invoice ADD COLUMN subtotal_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE invoice ADD COLUMN vat_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
UPDATE invoice SET subtotal_amount = total_amount;
