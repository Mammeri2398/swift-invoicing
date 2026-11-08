-- db/tenant V5 : index pour les recherches fréquentes.
-- Le dernier document émis d'une année (à chaque émission), le chiffre d'affaires et la liste des années.
CREATE INDEX idx_invoice_kind_year_sequence ON invoice(kind, invoice_year, sequence_number);
-- La TVA sur les encaissements : les paiements d'une période.
CREATE INDEX idx_invoice_payment_paid_on ON invoice_payment(paid_on);
-- Supprimer un brouillon met à jour le modèle récurrent qui l'a préparé (ON DELETE SET NULL).
CREATE INDEX idx_recurring_invoice_last_invoice ON recurring_invoice(last_invoice_id);
-- Doublon de l'index créé par la contrainte UNIQUE de invoice_number.
DROP INDEX IF EXISTS idx_invoice_number;
