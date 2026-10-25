-- V11__user_names.sql : prénom et nom des utilisateurs, affichés dans l'en-tête et modifiables dans « Mon compte ».
ALTER TABLE app_user ADD COLUMN first_name VARCHAR(100);
ALTER TABLE app_user ADD COLUMN last_name VARCHAR(100);
