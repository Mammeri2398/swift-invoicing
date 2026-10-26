-- V21__user_language.sql : langue de l'interface choisie par chaque utilisateur (fr, en, ar), dans « Mon compte ».
ALTER TABLE app_user ADD COLUMN language VARCHAR(5) DEFAULT 'fr' NOT NULL;
