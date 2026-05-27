-- Table: public.user_document

-- DROP TABLE IF EXISTS public.user_document;

CREATE TABLE IF NOT EXISTS public.user_document
(
    id uuid primary key default gen_random_uuid(),
    content oid,
    ocr_data json,
    session_id character varying(255) COLLATE pg_catalog."default",
    type character varying(255) COLLATE pg_catalog."default"
)

TABLESPACE pg_default;