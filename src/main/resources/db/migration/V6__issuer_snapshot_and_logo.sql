-- V6__issuer_snapshot_and_logo.sql : instantané complet de l'émetteur sur la facture, et logo de l'émetteur.

-- Ville, téléphone et email étaient lus sur le profil émetteur actuel, même pour une facture déjà émise.
ALTER TABLE invoice ADD COLUMN issuer_city VARCHAR(255);
ALTER TABLE invoice ADD COLUMN issuer_phone VARCHAR(50);
ALTER TABLE invoice ADD COLUMN issuer_email VARCHAR(255);

-- Factures déjà émises : la meilleure information disponible est le profil émetteur actuel.
UPDATE invoice
SET issuer_city = (SELECT city FROM issuer WHERE id = 1),
    issuer_phone = (SELECT phone FROM issuer WHERE id = 1),
    issuer_email = (SELECT email FROM issuer WHERE id = 1)
WHERE status <> 'DRAFT';

-- Logo stocké en base (une seule petite image, sauvegardée avec les données). L'ancienne colonne n'était pas utilisée.
ALTER TABLE issuer DROP COLUMN logo;
ALTER TABLE issuer ADD COLUMN logo_data BYTEA;
ALTER TABLE issuer ADD COLUMN logo_content_type VARCHAR(50);
