-- V14__credit_notes.sql : avoirs. Un avoir est un document de la table invoice (kind = 'CREDIT_NOTE')
-- rattaché à la facture qu'il annule en tout ou partie, avec sa propre numérotation (AV-2026-0001).

ALTER TABLE invoice ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'INVOICE';
ALTER TABLE invoice ADD COLUMN original_invoice_id BIGINT REFERENCES invoice(id);
CREATE INDEX idx_invoice_original ON invoice(original_invoice_id);

-- Compteur annuel des avoirs, séparé de celui des factures (même principe : continu, sans trou)
CREATE TABLE credit_note_sequence (
    credit_note_year  INTEGER PRIMARY KEY,
    last_value        INTEGER NOT NULL
);
