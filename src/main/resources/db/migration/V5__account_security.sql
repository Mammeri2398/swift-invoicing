-- V5__account_security.sql : verrouillage après échecs de connexion, changement de mot de passe forcé,
-- et jetons de réinitialisation stockés sous forme d'empreinte (SHA-256) au lieu du jeton en clair.

ALTER TABLE app_user ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMP;
ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMP;

-- Les jetons existants sont en clair : ils sont supprimés (validité d'une heure, il suffit d'en redemander un).
DELETE FROM password_reset_token;
DROP INDEX idx_password_reset_token_token;
ALTER TABLE password_reset_token RENAME COLUMN token TO token_hash;
ALTER TABLE password_reset_token ALTER COLUMN token_hash SET DATA TYPE VARCHAR(64);
CREATE INDEX idx_password_reset_token_hash ON password_reset_token(token_hash);
