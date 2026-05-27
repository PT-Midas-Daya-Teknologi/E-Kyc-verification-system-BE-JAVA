-- Table: public.user_session

-- DROP TABLE IF EXISTS public.user_session;

CREATE TABLE IF NOT EXISTS public.user_session
(
    id uuid primary key default gen_random_uuid(),
    attempts json,
    document_id uuid,
    is_active boolean,
    session_expiry timestamp(6) without time zone,
    user_id bigint,
    video_id uuid,
    constraint user_session_user_id_fkey foreign key (user_id) references public.users(id),
    constraint user_session_document_id_fkey foreign key (document_id) references public.user_document(id)
)

TABLESPACE pg_default;