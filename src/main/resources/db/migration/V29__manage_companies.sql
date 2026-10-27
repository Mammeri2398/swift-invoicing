-- V29__manage_companies.sql : créer, renommer et désactiver les sociétés de l'installation (Réglages > Sociétés).
INSERT INTO privilege (code, name, description) VALUES
('MANAGE_COMPANIES', 'Gérer les sociétés', 'Créer, renommer et désactiver les sociétés de l''installation');

-- Administrateur keeps every privilege
INSERT INTO profile_privilege (profile_id, privilege_id)
SELECT 1, id FROM privilege WHERE code = 'MANAGE_COMPANIES' AND EXISTS (SELECT 1 FROM profile WHERE id = 1);
