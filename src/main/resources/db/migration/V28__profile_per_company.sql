-- V28__profile_per_company.sql : le profil d'un utilisateur se règle par société (user_company, V26).
-- La société où il a travaillé en dernier est retrouvée à la connexion suivante.
ALTER TABLE app_user DROP COLUMN profile_id;
ALTER TABLE app_user ADD COLUMN last_company_id BIGINT REFERENCES company(id) ON DELETE SET NULL;
