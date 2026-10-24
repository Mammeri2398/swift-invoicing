-- V9__invoice_event_detail.sql : précision sur un événement de l'historique (ex. destinataires d'un envoi par email).
ALTER TABLE invoice_event ADD COLUMN detail VARCHAR(500);
