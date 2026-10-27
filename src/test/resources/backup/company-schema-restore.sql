--
-- PostgreSQL database dump
--

\restrict 98Esbp8LHRubmUWYPfU32ahu8UNCGY68yFQNynEywuJBzXMj8NdY0W8d08pBsK3

-- Dumped from database version 14.1 (Debian 14.1-1.pgdg110+1)
-- Dumped by pg_dump version 18.6 (Ubuntu 18.6-0ubuntu0.26.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY company_1.recurring_invoice_line DROP CONSTRAINT IF EXISTS recurring_invoice_line_recurring_invoice_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.recurring_invoice DROP CONSTRAINT IF EXISTS recurring_invoice_last_invoice_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.recurring_invoice DROP CONSTRAINT IF EXISTS recurring_invoice_client_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice DROP CONSTRAINT IF EXISTS invoice_source_document_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice_payment DROP CONSTRAINT IF EXISTS invoice_payment_invoice_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice DROP CONSTRAINT IF EXISTS invoice_original_invoice_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice_line DROP CONSTRAINT IF EXISTS invoice_line_invoice_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice_event DROP CONSTRAINT IF EXISTS invoice_event_invoice_id_fkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice DROP CONSTRAINT IF EXISTS invoice_client_id_fkey;
DROP INDEX IF EXISTS company_1.idx_recurring_invoice_line_parent;
DROP INDEX IF EXISTS company_1.idx_recurring_invoice_client_id;
DROP INDEX IF EXISTS company_1.idx_invoice_status;
DROP INDEX IF EXISTS company_1.idx_invoice_source;
DROP INDEX IF EXISTS company_1.idx_invoice_payment_invoice;
DROP INDEX IF EXISTS company_1.idx_invoice_original;
DROP INDEX IF EXISTS company_1.idx_invoice_number;
DROP INDEX IF EXISTS company_1.idx_invoice_line_invoice_id;
DROP INDEX IF EXISTS company_1.idx_invoice_issue_date;
DROP INDEX IF EXISTS company_1.idx_invoice_event_invoice;
DROP INDEX IF EXISTS company_1.idx_invoice_client_id;
DROP INDEX IF EXISTS company_1.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY company_1.recurring_invoice DROP CONSTRAINT IF EXISTS recurring_invoice_pkey;
ALTER TABLE IF EXISTS ONLY company_1.recurring_invoice_line DROP CONSTRAINT IF EXISTS recurring_invoice_line_pkey;
ALTER TABLE IF EXISTS ONLY company_1.mail_settings DROP CONSTRAINT IF EXISTS mail_settings_pkey;
ALTER TABLE IF EXISTS ONLY company_1.issuer DROP CONSTRAINT IF EXISTS issuer_pkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice_sequence DROP CONSTRAINT IF EXISTS invoice_sequence_pkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice DROP CONSTRAINT IF EXISTS invoice_pkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice_payment DROP CONSTRAINT IF EXISTS invoice_payment_pkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice_line DROP CONSTRAINT IF EXISTS invoice_line_pkey;
ALTER TABLE IF EXISTS ONLY company_1.invoice DROP CONSTRAINT IF EXISTS invoice_invoice_number_key;
ALTER TABLE IF EXISTS ONLY company_1.invoice_event DROP CONSTRAINT IF EXISTS invoice_event_pkey;
ALTER TABLE IF EXISTS ONLY company_1.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY company_1.document_sequence DROP CONSTRAINT IF EXISTS document_sequence_pkey;
ALTER TABLE IF EXISTS ONLY company_1.credit_note_sequence DROP CONSTRAINT IF EXISTS credit_note_sequence_pkey;
ALTER TABLE IF EXISTS ONLY company_1.client DROP CONSTRAINT IF EXISTS client_pkey;
ALTER TABLE IF EXISTS ONLY company_1.catalog_item DROP CONSTRAINT IF EXISTS catalog_item_pkey;
ALTER TABLE IF EXISTS ONLY company_1.backup_settings DROP CONSTRAINT IF EXISTS backup_settings_pkey;
ALTER TABLE IF EXISTS ONLY company_1.accounting_settings DROP CONSTRAINT IF EXISTS accounting_settings_pkey;
DROP TABLE IF EXISTS company_1.recurring_invoice_line;
DROP TABLE IF EXISTS company_1.recurring_invoice;
DROP TABLE IF EXISTS company_1.mail_settings;
DROP TABLE IF EXISTS company_1.issuer;
DROP TABLE IF EXISTS company_1.invoice_sequence;
DROP TABLE IF EXISTS company_1.invoice_payment;
DROP TABLE IF EXISTS company_1.invoice_line;
DROP TABLE IF EXISTS company_1.invoice_event;
DROP TABLE IF EXISTS company_1.invoice;
DROP TABLE IF EXISTS company_1.flyway_schema_history;
DROP TABLE IF EXISTS company_1.document_sequence;
DROP TABLE IF EXISTS company_1.credit_note_sequence;
DROP TABLE IF EXISTS company_1.client;
DROP TABLE IF EXISTS company_1.catalog_item;
DROP TABLE IF EXISTS company_1.backup_settings;
DROP TABLE IF EXISTS company_1.accounting_settings;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounting_settings; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.accounting_settings (
    id bigint NOT NULL,
    customer_account character varying(20) NOT NULL,
    sales_account character varying(20) NOT NULL,
    vat_account character varying(20) NOT NULL,
    stamp_account character varying(20) NOT NULL,
    bank_account character varying(20) NOT NULL,
    cash_account character varying(20) NOT NULL,
    sales_journal character varying(10) NOT NULL,
    bank_journal character varying(10) NOT NULL,
    cash_journal character varying(10) NOT NULL
);


--
-- Name: backup_settings; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.backup_settings (
    id bigint NOT NULL,
    directory character varying(1000) NOT NULL,
    keep_count integer NOT NULL
);


--
-- Name: catalog_item; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.catalog_item (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    unit character varying(50),
    unit_price numeric(14,2) NOT NULL,
    vat_rate numeric(5,2) DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL
);


--
-- Name: catalog_item_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.catalog_item ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.catalog_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: client; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.client (
    id bigint NOT NULL,
    type character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    address character varying(255),
    city character varying(255),
    phone character varying(50),
    email character varying(255),
    nif character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    rc_number character varying(50),
    nis character varying(50),
    tax_article character varying(50)
);


--
-- Name: client_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.client ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: credit_note_sequence; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.credit_note_sequence (
    credit_note_year integer NOT NULL,
    last_value integer NOT NULL
);


--
-- Name: document_sequence; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.document_sequence (
    kind character varying(20) NOT NULL,
    doc_year integer NOT NULL,
    last_value integer NOT NULL
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: invoice; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.invoice (
    id bigint NOT NULL,
    invoice_number character varying(50),
    invoice_year integer,
    sequence_number integer,
    client_id bigint NOT NULL,
    issue_date date,
    due_date date,
    status character varying(20) NOT NULL,
    payment_method character varying(20),
    notes character varying(1000),
    total_amount numeric(14,2) DEFAULT 0 NOT NULL,
    issuer_full_name character varying(255),
    issuer_trade_name character varying(255),
    issuer_address character varying(255),
    issuer_ae_card_number character varying(50),
    issuer_nif character varying(50),
    issuer_nis character varying(50),
    issuer_bank_account character varying(100),
    client_name character varying(255),
    client_address character varying(255),
    client_nif character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    issued_at timestamp without time zone,
    paid_at timestamp without time zone,
    cancelled_at timestamp without time zone,
    issuer_city character varying(255),
    issuer_phone character varying(50),
    issuer_email character varying(255),
    issuer_company_type character varying(20),
    issuer_legal_form character varying(50),
    issuer_share_capital numeric(14,2),
    issuer_rc_number character varying(50),
    issuer_tax_article character varying(50),
    subtotal_amount numeric(14,2) DEFAULT 0 NOT NULL,
    vat_amount numeric(14,2) DEFAULT 0 NOT NULL,
    client_rc_number character varying(50),
    client_nis character varying(50),
    client_tax_article character varying(50),
    kind character varying(20) DEFAULT 'INVOICE'::character varying NOT NULL,
    original_invoice_id bigint,
    stamp_duty_amount numeric(14,2) DEFAULT 0 NOT NULL,
    source_document_id bigint,
    accepted_at timestamp without time zone,
    refused_at timestamp without time zone,
    issuer_vat_mention character varying(255)
);


--
-- Name: invoice_event; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.invoice_event (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    action character varying(20) NOT NULL,
    username character varying(100),
    occurred_at timestamp without time zone NOT NULL,
    detail character varying(500)
);


--
-- Name: invoice_event_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.invoice_event ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.invoice_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: invoice_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.invoice ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.invoice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: invoice_line; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.invoice_line (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    "position" integer NOT NULL,
    description character varying(255) NOT NULL,
    unit character varying(50),
    quantity numeric(12,3) NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    line_total numeric(14,2) NOT NULL,
    vat_rate numeric(5,2) DEFAULT 0 NOT NULL
);


--
-- Name: invoice_line_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.invoice_line ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.invoice_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: invoice_payment; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.invoice_payment (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    amount numeric(14,2) NOT NULL,
    paid_on date NOT NULL,
    method character varying(20),
    reference character varying(100),
    username character varying(100),
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT invoice_payment_amount_check CHECK ((amount > (0)::numeric))
);


--
-- Name: invoice_payment_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.invoice_payment ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.invoice_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: invoice_sequence; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.invoice_sequence (
    invoice_year integer NOT NULL,
    last_value integer DEFAULT 0 NOT NULL
);


--
-- Name: issuer; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.issuer (
    id bigint NOT NULL,
    full_name character varying(255) NOT NULL,
    trade_name character varying(255),
    address character varying(255),
    city character varying(255),
    phone character varying(50),
    email character varying(255),
    ae_card_number character varying(50),
    nif character varying(50) NOT NULL,
    nis character varying(50),
    activity_description character varying(500),
    bank_or_ccp_account character varying(100),
    logo_data bytea,
    logo_content_type character varying(50),
    annual_ceiling numeric(14,2) DEFAULT 5000000 NOT NULL,
    company_type character varying(20) DEFAULT 'AUTO_ENTREPRENEUR'::character varying NOT NULL,
    legal_form character varying(50),
    share_capital numeric(14,2),
    rc_number character varying(50),
    tax_article character varying(50),
    vat_mention character varying(255),
    CONSTRAINT chk_issuer_annual_ceiling CHECK ((annual_ceiling > (0)::numeric))
);


--
-- Name: mail_settings; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.mail_settings (
    id bigint NOT NULL,
    enabled boolean NOT NULL,
    host character varying(255),
    port integer,
    security character varying(20) NOT NULL,
    username character varying(255),
    password character varying(255),
    from_address character varying(255)
);


--
-- Name: recurring_invoice; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.recurring_invoice (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    client_id bigint NOT NULL,
    frequency character varying(20) NOT NULL,
    start_date date NOT NULL,
    occurrences integer DEFAULT 0 NOT NULL,
    end_date date,
    due_days integer,
    payment_method character varying(30),
    notes character varying(1000),
    active boolean DEFAULT true NOT NULL,
    last_invoice_id bigint,
    last_run_at timestamp without time zone,
    last_error character varying(500),
    created_at timestamp without time zone NOT NULL
);


--
-- Name: recurring_invoice_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.recurring_invoice ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.recurring_invoice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: recurring_invoice_line; Type: TABLE; Schema: company_1; Owner: -
--

CREATE TABLE company_1.recurring_invoice_line (
    id bigint NOT NULL,
    recurring_invoice_id bigint NOT NULL,
    "position" integer NOT NULL,
    description character varying(255) NOT NULL,
    unit character varying(50),
    quantity numeric(12,3) NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    vat_rate numeric(5,2) DEFAULT 0 NOT NULL
);


--
-- Name: recurring_invoice_line_id_seq; Type: SEQUENCE; Schema: company_1; Owner: -
--

ALTER TABLE company_1.recurring_invoice_line ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME company_1.recurring_invoice_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: accounting_settings accounting_settings_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.accounting_settings
    ADD CONSTRAINT accounting_settings_pkey PRIMARY KEY (id);


--
-- Name: backup_settings backup_settings_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.backup_settings
    ADD CONSTRAINT backup_settings_pkey PRIMARY KEY (id);


--
-- Name: catalog_item catalog_item_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.catalog_item
    ADD CONSTRAINT catalog_item_pkey PRIMARY KEY (id);


--
-- Name: client client_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.client
    ADD CONSTRAINT client_pkey PRIMARY KEY (id);


--
-- Name: credit_note_sequence credit_note_sequence_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.credit_note_sequence
    ADD CONSTRAINT credit_note_sequence_pkey PRIMARY KEY (credit_note_year);


--
-- Name: document_sequence document_sequence_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.document_sequence
    ADD CONSTRAINT document_sequence_pkey PRIMARY KEY (kind, doc_year);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: invoice_event invoice_event_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_event
    ADD CONSTRAINT invoice_event_pkey PRIMARY KEY (id);


--
-- Name: invoice invoice_invoice_number_key; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice
    ADD CONSTRAINT invoice_invoice_number_key UNIQUE (invoice_number);


--
-- Name: invoice_line invoice_line_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_line
    ADD CONSTRAINT invoice_line_pkey PRIMARY KEY (id);


--
-- Name: invoice_payment invoice_payment_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_payment
    ADD CONSTRAINT invoice_payment_pkey PRIMARY KEY (id);


--
-- Name: invoice invoice_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice
    ADD CONSTRAINT invoice_pkey PRIMARY KEY (id);


--
-- Name: invoice_sequence invoice_sequence_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_sequence
    ADD CONSTRAINT invoice_sequence_pkey PRIMARY KEY (invoice_year);


--
-- Name: issuer issuer_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.issuer
    ADD CONSTRAINT issuer_pkey PRIMARY KEY (id);


--
-- Name: mail_settings mail_settings_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.mail_settings
    ADD CONSTRAINT mail_settings_pkey PRIMARY KEY (id);


--
-- Name: recurring_invoice_line recurring_invoice_line_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.recurring_invoice_line
    ADD CONSTRAINT recurring_invoice_line_pkey PRIMARY KEY (id);


--
-- Name: recurring_invoice recurring_invoice_pkey; Type: CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.recurring_invoice
    ADD CONSTRAINT recurring_invoice_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON company_1.flyway_schema_history USING btree (success);


--
-- Name: idx_invoice_client_id; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_client_id ON company_1.invoice USING btree (client_id);


--
-- Name: idx_invoice_event_invoice; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_event_invoice ON company_1.invoice_event USING btree (invoice_id, occurred_at);


--
-- Name: idx_invoice_issue_date; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_issue_date ON company_1.invoice USING btree (issue_date);


--
-- Name: idx_invoice_line_invoice_id; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_line_invoice_id ON company_1.invoice_line USING btree (invoice_id);


--
-- Name: idx_invoice_number; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_number ON company_1.invoice USING btree (invoice_number);


--
-- Name: idx_invoice_original; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_original ON company_1.invoice USING btree (original_invoice_id);


--
-- Name: idx_invoice_payment_invoice; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_payment_invoice ON company_1.invoice_payment USING btree (invoice_id);


--
-- Name: idx_invoice_source; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_source ON company_1.invoice USING btree (source_document_id);


--
-- Name: idx_invoice_status; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_invoice_status ON company_1.invoice USING btree (status);


--
-- Name: idx_recurring_invoice_client_id; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_recurring_invoice_client_id ON company_1.recurring_invoice USING btree (client_id);


--
-- Name: idx_recurring_invoice_line_parent; Type: INDEX; Schema: company_1; Owner: -
--

CREATE INDEX idx_recurring_invoice_line_parent ON company_1.recurring_invoice_line USING btree (recurring_invoice_id);


--
-- Name: invoice invoice_client_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice
    ADD CONSTRAINT invoice_client_id_fkey FOREIGN KEY (client_id) REFERENCES company_1.client(id);


--
-- Name: invoice_event invoice_event_invoice_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_event
    ADD CONSTRAINT invoice_event_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES company_1.invoice(id) ON DELETE CASCADE;


--
-- Name: invoice_line invoice_line_invoice_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_line
    ADD CONSTRAINT invoice_line_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES company_1.invoice(id) ON DELETE CASCADE;


--
-- Name: invoice invoice_original_invoice_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice
    ADD CONSTRAINT invoice_original_invoice_id_fkey FOREIGN KEY (original_invoice_id) REFERENCES company_1.invoice(id);


--
-- Name: invoice_payment invoice_payment_invoice_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice_payment
    ADD CONSTRAINT invoice_payment_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES company_1.invoice(id) ON DELETE CASCADE;


--
-- Name: invoice invoice_source_document_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.invoice
    ADD CONSTRAINT invoice_source_document_id_fkey FOREIGN KEY (source_document_id) REFERENCES company_1.invoice(id);


--
-- Name: recurring_invoice recurring_invoice_client_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.recurring_invoice
    ADD CONSTRAINT recurring_invoice_client_id_fkey FOREIGN KEY (client_id) REFERENCES company_1.client(id) ON DELETE CASCADE;


--
-- Name: recurring_invoice recurring_invoice_last_invoice_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.recurring_invoice
    ADD CONSTRAINT recurring_invoice_last_invoice_id_fkey FOREIGN KEY (last_invoice_id) REFERENCES company_1.invoice(id) ON DELETE SET NULL;


--
-- Name: recurring_invoice_line recurring_invoice_line_recurring_invoice_id_fkey; Type: FK CONSTRAINT; Schema: company_1; Owner: -
--

ALTER TABLE ONLY company_1.recurring_invoice_line
    ADD CONSTRAINT recurring_invoice_line_recurring_invoice_id_fkey FOREIGN KEY (recurring_invoice_id) REFERENCES company_1.recurring_invoice(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict 98Esbp8LHRubmUWYPfU32ahu8UNCGY68yFQNynEywuJBzXMj8NdY0W8d08pBsK3

