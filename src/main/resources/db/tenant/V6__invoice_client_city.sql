-- db/tenant V6 : la ville du client, copiée à l'émission comme son adresse, et imprimée sur la facture.
-- Les documents déjà émis la reprennent de la fiche client actuelle.
ALTER TABLE invoice ADD COLUMN client_city VARCHAR(255);
UPDATE invoice SET client_city = (SELECT city FROM client WHERE client.id = invoice.client_id) WHERE status <> 'DRAFT';
