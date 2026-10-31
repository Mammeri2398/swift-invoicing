-- V31__audit_subject.sql : ce que vise chaque entrée du journal d'activité (la facture FA-2026-0001, le client « SARL X »...),
-- lu avant l'action pour qu'une suppression nomme encore ce qu'elle a supprimé. Affiché en phrase dans la langue du lecteur.
ALTER TABLE audit_event ADD COLUMN subject_type VARCHAR(30);
ALTER TABLE audit_event ADD COLUMN subject VARCHAR(255);
