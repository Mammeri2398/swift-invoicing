-- V19__licence.sql : licence d'une installation locale (une seule ligne, id = 1).
-- La clé est vérifiée à chaque lecture (signature de l'éditeur) ; last_seen_at protège contre
-- un recul de l'horloge du PC pour prolonger une licence ou un essai.
CREATE TABLE licence (
    id                BIGINT PRIMARY KEY,
    licence_key       VARCHAR(2000),
    activated_at      TIMESTAMP,
    trial_started_at  TIMESTAMP NOT NULL,
    last_seen_at      TIMESTAMP NOT NULL
);
