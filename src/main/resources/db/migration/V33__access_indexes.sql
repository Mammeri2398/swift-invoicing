-- Index on the foreign keys of the shared tables that had none: deleting a profile or a company checks them.
CREATE INDEX idx_user_company_profile ON user_company(profile_id);
CREATE INDEX idx_app_user_last_company ON app_user(last_company_id);
