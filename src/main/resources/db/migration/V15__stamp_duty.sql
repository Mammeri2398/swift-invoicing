-- V15__stamp_duty.sql : droit de timbre des factures payées en espèces, en plus du total TTC.
-- Le net à payer est total_amount + stamp_duty_amount ; le chiffre d'affaires et la TVA n'en tiennent pas compte.
ALTER TABLE invoice ADD COLUMN stamp_duty_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
