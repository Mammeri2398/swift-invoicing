-- V20__backups.sql : sauvegardes de la base sur une installation locale (Réglages > Sauvegardes).
-- Une seule ligne (id = 1), créée au premier enregistrement ; sans elle, les réglages app.backup.* s'appliquent.
CREATE TABLE backup_settings (
    id          BIGINT PRIMARY KEY,
    directory   VARCHAR(1000) NOT NULL,
    keep_count  INTEGER NOT NULL
);

INSERT INTO privilege (code, name, description) VALUES
('MANAGE_BACKUPS', 'Gérer les sauvegardes', 'Sauvegarder la base, télécharger et restaurer les sauvegardes');

-- Administrateur keeps every privilege
INSERT INTO profile_privilege (profile_id, privilege_id)
SELECT 1, id FROM privilege WHERE code = 'MANAGE_BACKUPS' AND EXISTS (SELECT 1 FROM profile WHERE id = 1);
