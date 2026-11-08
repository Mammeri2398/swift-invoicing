-- The email server of a company is no longer changed with EDIT_ISSUER: the password reset links of its users go
-- through it, so whoever sets it could receive them (an administrator's included). Its own privilege, held by the
-- Administrateur profile only; other profiles get it from an administrator when they need it.
INSERT INTO privilege (code, name, description) VALUES
('MANAGE_MAIL', 'Configurer l''envoi des emails', 'Choisir le serveur d''envoi des emails de la société (factures et liens de réinitialisation du mot de passe)');

INSERT INTO profile_privilege (profile_id, privilege_id)
SELECT 1, id FROM privilege WHERE code = 'MANAGE_MAIL' AND EXISTS (SELECT 1 FROM profile WHERE id = 1);
