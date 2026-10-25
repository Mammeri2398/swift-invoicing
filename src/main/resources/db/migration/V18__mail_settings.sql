-- V18__mail_settings.sql : serveur d'envoi des emails réglé dans l'application (Paramètres > Email),
-- indispensable sur une installation locale où les variables d'environnement ne sont pas utilisées.
-- Une seule ligne (id = 1), créée au premier enregistrement ; sans elle, les réglages MAIL_* s'appliquent.
CREATE TABLE mail_settings (
    id            BIGINT PRIMARY KEY,
    enabled       BOOLEAN NOT NULL,
    host          VARCHAR(255),
    port          INTEGER,
    security      VARCHAR(20) NOT NULL,
    username      VARCHAR(255),
    password      VARCHAR(255),
    from_address  VARCHAR(255)
);
