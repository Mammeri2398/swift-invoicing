-- V25__vat_mention.sql : la mention d'exonération de TVA d'un auto-entrepreneur devient un réglage de l'émetteur
-- (vide : la valeur par défaut invoice.vat-mention), recopiée sur chaque facture à l'émission comme les identifiants.
ALTER TABLE issuer ADD COLUMN vat_mention VARCHAR(255);
ALTER TABLE invoice ADD COLUMN issuer_vat_mention VARCHAR(255);
