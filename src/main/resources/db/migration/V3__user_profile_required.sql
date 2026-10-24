-- V3__user_profile_required.sql : tout utilisateur doit avoir un profil (app_user.profile_id NOT NULL).

-- Les utilisateurs existants sans profil sont rattachés à un profil sans aucun privilège,
-- créé uniquement s'il y en a : ils ne gagnent aucun droit et un administrateur
-- pourra leur attribuer le bon profil.
-- Id explicite : la séquence de profile n'a pas été avancée après l'insertion de l'id 1 en V2.
INSERT INTO profile (id, name, description)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM profile), 'Sans accès',
       'Aucun privilège. Attribué aux utilisateurs qui n''avaient pas de profil.'
WHERE EXISTS (SELECT 1 FROM app_user WHERE profile_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM profile WHERE name = 'Sans accès');

UPDATE app_user
SET profile_id = (SELECT id FROM profile WHERE name = 'Sans accès')
WHERE profile_id IS NULL;

ALTER TABLE app_user ALTER COLUMN profile_id SET NOT NULL;
