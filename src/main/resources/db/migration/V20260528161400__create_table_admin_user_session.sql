create table if not exists public.admin_user_session (
    id uuid primary key default gen_random_uuid(),
    admin_user_id uuid not null,
    access_token text,
    expiry timestamp not null,
    created_at timestamptz not null default now(),
    created_by varchar(255) not null default 'SYSTEM',
    updated_at timestamptz not null default now(),
    updated_by varchar(255) not null default 'SYSTEM',
    is_active boolean not null default false
)