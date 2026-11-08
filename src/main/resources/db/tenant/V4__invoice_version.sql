-- db/tenant V4 : un numéro de version par document. Deux modifications en même temps (deux onglets, deux
-- utilisateurs, un double clic) ne s'écrasent plus en silence : la seconde est refusée.
ALTER TABLE invoice ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
