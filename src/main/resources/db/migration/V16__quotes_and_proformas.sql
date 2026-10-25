-- V16__quotes_and_proformas.sql : devis et factures pro forma. Ce sont des documents de la table invoice
-- (kind = 'QUOTE' ou 'PROFORMA') sans valeur comptable : ils ne comptent ni dans le chiffre d'affaires
-- ni dans la TVA. « Facturer » crée une facture brouillon rattachée au document d'origine.

ALTER TABLE invoice ADD COLUMN source_document_id BIGINT REFERENCES invoice(id);
CREATE INDEX idx_invoice_source ON invoice(source_document_id);
ALTER TABLE invoice ADD COLUMN accepted_at TIMESTAMP;
ALTER TABLE invoice ADD COLUMN refused_at TIMESTAMP;

-- Compteurs annuels des autres documents (devis, pro forma), par type
CREATE TABLE document_sequence (
    kind        VARCHAR(20) NOT NULL,
    doc_year    INTEGER NOT NULL,
    last_value  INTEGER NOT NULL,
    PRIMARY KEY (kind, doc_year)
);
